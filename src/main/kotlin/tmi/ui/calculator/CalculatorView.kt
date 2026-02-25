package tmi.ui.calculator

import arc.Core
import arc.files.Fi
import arc.func.Boolp
import arc.graphics.Camera
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.input.KeyCode
import arc.math.Mathf
import arc.math.geom.Rect
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.Group
import arc.scene.event.*
import arc.scene.ui.Button
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Align
import arc.util.Log
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.recipe.AmountFormatter
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.ui.*
import tmi.ui.calculator.RecipeGraphElement.AddRecipeButton
import tmi.util.Consts
import tmi.util.enterSt
import tmi.util.exitSt
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CalculatorView: Table(), CalculatorDialog.TipsProvider {
  var isUpdated = false
    private set
  var astringentValid = false
    private set


  var padding = 24f
  var layerMargin = 160f

  var browsMode = false
  var showGrid = true
  var autoLinkInput = true
  var autoLinkOutput = true

  var graph = RecipeGraph()
    set(value) {
      field = value
      graphUpdated()
    }
  var statistic = RecipeStatistic(graph)

  var imageGenerating: Boolean = false
    private set
  private var tmpGridAlpha: Float = 1f

  private var layers: Array<Seq<RecipeGraphLayout.Node>> = arrayOf()

  private val recipeElements = Seq<RecipeGraphElement>()
  private val nodeToElement = ObjectMap<RecipeGraphLayout.Node, RecipeGraphElement>()

  private var hoveringShadow: RecipeTab? = null
  private val shadowTabs = Seq<RecipeTab>()

  private val linkLines = Seq<LinkLine>()
  private val layerCenter = Seq<Float>()

  private var lastZoom: Float = -1f
  private var panX: Float = 0f
  private var panY: Float = 0f

  private val viewBound = Rect()
  private lateinit var graphView: Group
  private lateinit var container: Group
  private lateinit var zoom: Group

  private lateinit var ioList: Table
  private lateinit var status: Table
  private lateinit var inputs: Table
  private lateinit var outputs: Table

  private val tabSelectors = object : Group() {
    var selecting: RecipeItem<*>? = null
      private set

    init {
      visible = false
      touchable = Touchable.childrenOnly
    }

    fun hide(){
      selecting = null
      visible = false
    }

    fun show(node: RecipeGraphNode, item: RecipeItem<*>): Boolean {
      val validNodes = recipeElements
        .filterIsInstance<RecipeTab>()
        .filter { it.node.recipe.containsProduction(item) }

      selecting = item
      visible = true

      clearChildren()
      validNodes.forEach { tabs ->
        val select = Button()
        select.style = Consts.recipeTabSelector
        select.clicked {
          node.disInput(item)
          node.setInput(item, tabs.node.targetNode)
          graphUpdated()
          hide()
        }
        select.setBounds(
          tabs.x - Scl.scl(16f), tabs.y - Scl.scl(16f),
          tabs.width + Scl.scl(32f), tabs.height + Scl.scl(32f)
        )
        select.addEventBlocker()
        addChild(select)
      }

      return validNodes.any()
    }
  }

  override fun getTip(): String = Core.bundle["calculator.tips.selectExisted"]
  override fun tipValid(): Boolean = tabSelectors.visible

  fun getBound() = Rect(viewBound).also {
    it.y -= ioList.height
    it.height += ioList.height
  }

  fun layoutRecipeTabs(){
    val bounds = viewBound
    bounds.setSize(0f)

    val layers = this.layers
    val maxLayerWidth = padding + layers.maxOf { it.sumf{ tab -> (nodeToElement[tab]?.nodeWidth ?: 8f) + padding } }
    val root = layers[0]

    layerCenter.clear()

    if (root.size <= 0) return
    else if (root.size > 1) {
      val rootWidth = padding + root.sumf { tab -> (nodeToElement[tab]?.nodeWidth ?: 8f) + padding }
      val diff = maxLayerWidth - rootWidth
      val rootDelta = diff/root.size

      var currX = -maxLayerWidth/2f

      root.forEach {
        val tab = nodeToElement[it]
        tab.nodeX = currX + rootDelta/2
        currX += tab.nodeWidth + padding + rootDelta
      }
    }
    else {
      val tab = nodeToElement[root.first()]
      tab.nodeX = -tab.nodeWidth/2
    }

    var currY = 0f
    layers.forEach { nodes ->
      val layerHeight = nodes.maxOf { nodeToElement[it]?.nodeHeight ?: 0f }

      nodes.forEach {
        val tab = nodeToElement[it] ?: return@forEach
        val diff = layerHeight - tab.nodeHeight
        tab.nodeY = currY - tab.nodeHeight - diff/2f
      }

      layerCenter.add(currY - layerHeight - (layerMargin + padding)/2f)
      currY -= layerHeight + layerMargin + padding
    }

    val overlaps = Seq<RecipeGraphElement>()
    for (depth in 1..<layers.size) {
      val layoutLayer = layers[depth]

      overlaps.clear()

      layoutLayer.forEach a@{ node ->
        val layoutTab = nodeToElement[node] ?: return@a
        if (node.parents().isEmpty()) return@a

        var n = 0
        var sumX = 0f
        var sumOffX = 0f

        node.parentsWithItem().forEach b@{ (item, nodes) ->
          val outOff = layoutTab.outputOffset(item).x

          nodes.forEach c@{ parent ->
            val parentTab = nodeToElement[parent] ?: return@b
            val inOff = parentTab.inputOffset(item).x

            n++
            sumOffX += outOff
            sumX += parentTab.nodeX + inOff
          }
        }

        layoutTab.nodeX = sumX/n - sumOffX/n
        resolveOverlaps(overlaps, layoutTab)
      }
    }

    recipeElements.forEachIndexed { i, layoutTab ->
      if (i == 0) {
        bounds.set(layoutTab.nodeX, layoutTab.nodeY, layoutTab.nodeWidth, layoutTab.nodeHeight)
      }
      else {
        bounds.merge(layoutTab.nodeX, layoutTab.nodeY)
        bounds.merge(layoutTab.nodeX + layoutTab.nodeWidth, layoutTab.nodeY + layoutTab.nodeHeight)
      }
    }
  }

  private fun resolveOverlaps(
    overlaps: Seq<RecipeGraphElement>,
    layoutTab: RecipeGraphElement,
  ) {
    val node = layoutTab.node
    val tabCenter = layoutTab.nodeX + layoutTab.nodeWidth/2f

    var insertIndex = 0
    for (tab in overlaps) {
      val center = tab.nodeX + tab.nodeWidth/2f
      if (center > tabCenter || (center == tabCenter && node.layerIndex > tab.node.layerIndex)) {
        break
      }
      insertIndex++
    }

    if (insertIndex < overlaps.size) overlaps.insert(insertIndex, layoutTab)
    else overlaps.add(layoutTab)

    val remLeft = insertIndex - 1
    val remRight = insertIndex + 1

    if (overlaps.size > 1) {
      if (insertIndex == 0) {
        val checkingTab = overlaps[1]
        val overlapping = checkingTab.nodeX - (layoutTab.nodeX + layoutTab.nodeWidth + padding)
        if (overlapping < 0) {
          val move = overlapping/2f
          layoutTab.nodeX += move
        }
      }
      if (insertIndex >= overlaps.size - 1) {
        val checkingTab = overlaps[overlaps.size - 2]

        val overlapping = layoutTab.nodeX - (checkingTab.nodeX + checkingTab.nodeWidth + padding)
        if (overlapping < 0) {
          val move = overlapping/2f
          layoutTab.nodeX -= move
        }
      }
    }

    if (remLeft >= 0) {
      var curr = layoutTab
      (remLeft downTo 0).forEach { i ->
        val checkingTab = overlaps[i]
        val overlapping = curr.nodeX - (checkingTab.nodeX + checkingTab.nodeWidth + padding)
        if (overlapping < 0) checkingTab.nodeX += overlapping
        curr = checkingTab
      }
    }
    if (remRight < overlaps.size) {
      var curr = layoutTab
      (remRight..<overlaps.size).forEach { i ->
        val checkingTab = overlaps[i]
        val overlapping = checkingTab.nodeX - (curr.nodeX + curr.nodeWidth + padding)
        if (overlapping < 0) checkingTab.nodeX -= overlapping
        curr = checkingTab
      }
    }
  }

  fun layoutLinkLines(){
    val layers = this.layers

    linkLines.clear()
    layers.forEachIndexed{ depth, layer ->
      val linkList = Seq<LinkLine>()
      layer.forEach { node ->
        val tab = nodeToElement[node] ?: return@forEach
        val children = node.childrenWithItem()
        children.forEach a@{ (item, child) ->
          val linked = nodeToElement[child] ?: return@a
          val from = tab.inputOffset(item).cpy().add(tab.nodeX, tab.nodeY)
          val to = linked.outputOffset(item).cpy().add(linked.nodeX, linked.nodeY)

          val line = LinkLine(item, from, to)

          tab.setupInputOverListener(line)
          linked.setupOutputOverListener(line)

          linkList.add(line)
        }
      }

      if (!linkList.isEmpty) {
        var sumLineCent = 0f
        val centerY = layerCenter[depth]
        linkList.sort { it.to.x + (it.from.x - it.to.x)*0.001f }
        linkList.forEachIndexed { i, line ->
          val lineLeft = min(line.from.x, line.to.x) - 0.1
          var n = 0
          var sumFrom = 0f
          var sumTo = 0f
          var upper = Float.NEGATIVE_INFINITY
          var lower = Float.POSITIVE_INFINITY

          for (r in (i - 1) downTo 0) {
            val checkingLine = linkList[r]
            val checkingRight = max(checkingLine.from.x, checkingLine.to.x)

            if (checkingRight < lineLeft) continue

            if (checkingLine.item == line.item) {
              line.centerY = checkingLine.centerY
              n = -1
              break
            }

            sumFrom += checkingLine.from.x
            sumTo += checkingLine.to.x
            upper = max(upper, checkingLine.centerY)
            lower = min(lower, checkingLine.centerY)
            n++
          }

          if (n > 0) {
            val aveFrom = sumFrom/n
            val aveTo = sumTo/n

            if ((aveTo > aveFrom && line.from.x > aveFrom && line.to.x > aveTo)
            || (aveTo < aveFrom && line.from.x < aveFrom && line.to.x < aveTo)) {
              line.centerY = upper + padding
            }
            else {
              line.centerY = lower - padding
            }
          }
          else if (n == 0) line.centerY = centerY

          sumLineCent += line.centerY
        }

        val ave = sumLineCent/linkList.size
        val off = ave - centerY
        linkList.forEach { it.centerY -= off }

        linkLines.addAll(linkList)
      }
    }
  }

  private fun setupGraphView() {
    graphView = object : Group() {
      private val tempList = Seq<LinkLine>()

      override fun childrenChanged() {
        invalidate()
      }

      override fun layout() {
        val children = getChildren()

        children.forEach { it.validate() }

        layoutRecipeTabs()
        layoutLinkLines()
      }

      override fun removeChild(actor: Element?, unfocus: Boolean): Boolean {
        val removed = super.removeChild(actor, unfocus)
        if (removed && actor is RecipeTab) removeRecipeTab(actor)
        return removed
      }

      override fun draw() {
        validate()
        drawLines()
        drawShadowLines()
        super.draw()
      }

      private fun drawLines() {
        Lines.stroke(Scl.scl(5f))
        fun draw(line: LinkLine) {
          val from = line.from
          val to = line.to

          val cent = y + line.centerY

          val fromX = x + from.x
          val fromY = y + from.y
          val toX = x + to.x
          val toY = y + to.y

          Lines.line(
            fromX, fromY,
            fromX, cent
          )
          Lines.line(
            fromX, cent,
            toX, cent,
          )
          Lines.line(
            toX, cent,
            toX, toY
          )
        }

        tempList.clear()

        Draw.color(Color.gray)
        linkLines.forEach { line ->
          if (line.isOver) {
            tempList.add(line)
            return@forEach
          }
          draw(line)
        }

        Draw.color(Pal.accent)
        tempList.forEach { line -> draw(line) }

        Draw.color()
      }

      private fun drawShadowLines() {
        fun draw(shadow: RecipeTab, offX: Float) {
          val realNode = (shadow.node as RecipeGraphLayout.ShadowNode).shadowed
          val realTab = nodeToElement[realNode]

          val bx = viewBound.x - Scl.scl(45f) - offX
          val rc = realTab.centerOffset().cpy().add(realTab.nodeX, realTab.nodeY)
          val sc = shadow.centerOffset().cpy().add(shadow.nodeX, shadow.nodeY)

          val sig = Scl.scl(18f)
          Lines.dashLine(
            x + sc.x, y + sc.y,
            x + bx, y + sc.y,
            (abs(bx - sc.x)/sig).roundToInt()
          )
          Lines.dashLine(
            x + bx, y + sc.y,
            x + bx, y + rc.y,
            (abs(sc.y - rc.y)/sig).roundToInt()
          )
          Lines.dashLine(
            x + bx, y + rc.y,
            x + rc.x, y + rc.y,
            (abs(bx - rc.x)/sig).roundToInt()
          )
        }

        var n = 0
        shadowTabs.forEachIndexed { i, shadow ->
          if (shadow == hoveringShadow) {
            n = i
            return@forEachIndexed
          }
          Lines.stroke(Scl.scl(5f))
          Draw.color(Color.gray)

          draw(shadow, Scl.scl(16f)*i)
        }

        hoveringShadow?.also { shadow ->
          Lines.stroke(Scl.scl(5f))
          Draw.color(Pal.accent, Color.gray, Mathf.absin(10f, 1f))

          draw(shadow, Scl.scl(16f)*n)
        }
      }
    }
  }

  fun build() {
    clear()

    setupGraphView()
    graphView.setBounds(0f, 0f, 0f, 0f)
    tabSelectors.setBounds(0f, 0f, 0f, 0f)

    setupIOList()

    container = object : Group() {
      override fun act(delta: Float) {
        super.act(delta)

        setPosition(panX + zoom.width/2f, panY + zoom.height/2f, Align.center)
      }

      override fun draw() {
        if (showGrid) {
          val a = if (imageGenerating) tmpGridAlpha else 1f
          Lines.stroke(Scl.scl(4f), Pal.gray)
          Draw.alpha(parentAlpha*a)
          val gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", 150).toFloat())

          var offX = 0f
          while (offX <= (Core.scene.width)/zoom.scaleX - panX) {
            Lines.line(x + offX, -Core.scene.height/zoom.scaleY, x + offX, Core.scene.height/zoom.scaleY*2)
            offX += gridSize
          }
          offX = 0f
          while (offX >= -(Core.scene.width)/zoom.scaleX - panX) {
            Lines.line(x + offX, -Core.scene.height/zoom.scaleY, x + offX, Core.scene.height/zoom.scaleY*2)
            offX -= gridSize
          }

          var offY = 0f
          while (offY <= (Core.scene.height)/zoom.scaleY - panY) {
            Lines.line(-Core.scene.width/zoom.scaleX, y + offY, Core.scene.width/zoom.scaleX*2, y + offY)
            offY += gridSize
          }
          offY = 0f
          while (offY >= -(Core.scene.height)/zoom.scaleY - panY) {
            Lines.line(-Core.scene.width/zoom.scaleX, y + offY, Core.scene.width/zoom.scaleX*2, y + offY)
            offY -= gridSize
          }
        }
        super.draw()
      }
    }
    container.addChild(graphView)
    container.addChild(tabSelectors)
    container.addChild(ioList)

    fill { t ->
      zoom = t
      zoom.isTransform = true
    }
    zoom.addChild(container)

    graphUpdated()

    touchable = Touchable.enabled
    setPanListener()
    setZoomListener()

    addEventBlocker(true){ e -> browsMode && e is InputEvent && e.type == InputEvent.InputEventType.touchDown }

    addListener(object: ClickListener(KeyCode.mouseLeft){
      override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        super.touchDragged(event, x, y, pointer)
        if (!cancelled && Mathf.dst(x, y, touchDownX, touchDownY) > 8) {
          cancel()
        }
      }

      override fun clicked(event: InputEvent?, x: Float, y: Float) {
        tabSelectors.hide()
      }
    })
  }

  private fun setupIOList() {
    ioList = Table()
    val ioList = ioList

    ioList.visibility = Boolp{ !graph.isEmpty() }
    ioList.update {
      ioList.setPosition(viewBound.x + viewBound.width/2f, viewBound.y, Align.top)
    }

    ioList.table { label ->
      label.add().size(32f)
      label.add(Core.bundle["dialog.calculator.statistic"]).color(Pal.accent)
        .padLeft(12f).padRight(12f).growX()
        .labelAlign(Align.center)
      label.button(Icon.listSmall, Styles.clearNonei){}.size(32f)
    }.pad(8f).padTop(26f).growX()
    ioList.row()
    ioList.image().color(Pal.accent).growX().height(4f).padBottom(8f)

    ioList.row()
    ioList.table { i -> status = i }.growX().fillY()

    ioList.row()
    ioList.table { o ->
      o.add(Core.bundle["dialog.calculator.statOutputs"])
      o.image(Icon.upload).size(32f).pad(8f)
      outputs = o.table().growY().fillY().pad(8f).get()
    }.pad(8f).growX().fillY()

    ioList.row()
    ioList.table { i ->
      i.add(Core.bundle["dialog.calculator.statInputs"])
      i.image(Icon.download).size(32f).pad(8f)
      inputs = i.table().growX().fillY().pad(8f).get()
    }.pad(8f).padTop(16f).growX().fillY()
  }

  fun rebuildIO() {
    val formatter = AmountFormatter.timedAmountFormatter()

    status.clearChildren()
    if (!astringentValid) {
      status.table { s ->
        s.image(Icon.warning).color(Color.crimson).size(36f).pad(8f)
        s.add(Core.bundle["dialog.calculator.astringentFailed"])
      }
    }

    outputs.clearChildren()
    val out = statistic.resultOutputs()
    val res = statistic.resultRedundant()

    (out + res).groupBy { it.item }
      .map { (k, v) -> RecipeItemStack(k, v.sumOf{ s -> s.amount.toDouble() }.toFloat()) }
      .filter { it.amount > 0.000001f }
      .forEachIndexed { i, stack ->
        if (i > 0 && i % 6 == 0) outputs.row()
        outputs.add(RecipeItemCell(CellType.PRODUCTION, stack).also {
          it.style = Styles.cleart
          it.setFormatter(formatter)
        }).size(56f).pad(8f).padLeft(12f).padRight(12f)
      }

    inputs.clearChildren()
    val nonOptional = statistic.resultInputs()
    val optional = statistic.resultOptionalInputs()
      nonOptional.forEachIndexed { i, stack ->
      if (i > 0 && i % 6 == 0) inputs.row()
      inputs.add(RecipeItemCell(CellType.MATERIAL, *stack.toTypedArray()).also {
        it.style = Styles.cleart
        it.setFormatter(formatter)
      }).size(56f).pad(8f).padLeft(12f).padRight(12f)
    }

    if (optional.isNotEmpty()) {
      inputs.row()
      inputs.table{ opt ->
        opt.table { t ->
          t.add(Core.bundle["misc.optional"]).pad(8f)
          t.row()
          t.image().color(Pal.gray).growX().height(4f).padTop(8f).padBottom(8f)
          t.row()
          t.table { items ->
            optional.forEachIndexed { i, stack ->
              if (i > 0 && i%6 == 0) inputs.row()
              items.add(RecipeItemCell(CellType.MATERIAL, *stack.toTypedArray()).also {
                it.style = Styles.cleart
                it.setFormatter(formatter)
              }).size(56f).pad(8f).padLeft(12f).padRight(12f)
            }
          }
        }.fill()
      }.colspan(6)
    }

    ioList.validate()
    ioList.pack()
  }

  fun showRecipeSelector(
    cell: RecipeItemCell,
    item: RecipeItem<out Any?>,
    graphNode: RecipeGraphNode,
  ) {
    val existed = if (tabSelectors.selecting != item) tabSelectors.show(graphNode, item) else false

    if (!existed) {
      tabSelectors.hide()

      TmiUI.recipesDialog.showWith {
        setCurrSelecting(item, RecipesDialog.Mode.RECIPE, true)
        callbackRecipe(Icon.tree) { recipe ->
          val newNode = RecipeGraphNode(recipe)

          graph.addNode(newNode)
          graphNode.disInput(item)
          graphNode.optionals.remove(item)
          graphNode.setInput(item, newNode)
          cell.setChosenItem(item)

          linkExisted(newNode)

          graphUpdated()

          hide()
        }
        showDoubleRecipe(true)
      }
    }
  }

  fun linkExisted(target: RecipeGraphNode) {
    if (autoLinkInput || autoLinkOutput) {
      val validConsNodes = mutableMapOf<RecipeItem<*>, RecipeGraphNode>()
      val validProdNodes = mutableMapOf<RecipeItem<*>, Seq<RecipeGraphNode>>()
      val recipe = target.recipe

      graph.forEach { node ->
        if (node == target) return@forEach

        val nodeRec = node.recipe

        if (autoLinkInput) {
          recipe.materials
            .filter { it.itemType != RecipeItemType.POWER && it.itemType != RecipeItemType.ATTRIBUTE }
            .filter { !it.isOptional || node.optionals.contains(it.item) }
            .forEach { mat ->
              if (nodeRec.containsProduction(mat.item) && !target.hasInput(mat.item)) {
                validConsNodes[mat.item] = node
              }
            }
        }

        if (autoLinkOutput) {
          recipe.productions
            .filter { it.itemType != RecipeItemType.POWER }
            .forEach { mat ->
              if (nodeRec.containsMaterial(mat.item) && !node.hasInput(mat.item)
              && (!mat.isOptional || node.optionals.contains(mat.item))) {
                validProdNodes.computeIfAbsent(mat.item) { Seq() }.add(node)
              }
            }
        }
      }

      if (autoLinkInput) validConsNodes.forEach { (item, n) -> target.setInput(item, n) }
      if (autoLinkOutput) validProdNodes.forEach { (item, nodes) -> nodes.forEach { n -> target.setOutput(item, n) } }
    }
  }
  
  fun graphUpdated() {
    recipeElements.clear()
    nodeToElement.clear()
    shadowTabs.clear()
    hoveringShadow = null
    graphView.clearChildren()

    layers = RecipeGraphLayout.generateLayout(graph)

    layers.forEach { layer ->
      layer.filterIsInstance<RecipeGraphLayout.RecNode>().forEach {
        val elem = RecipeTab(it, this, it is RecipeGraphLayout.ShadowNode)

        if (elem.isShadow) {
          elem.touchable = Touchable.enabled
          elem.enterSt { Core.app.post { hoveringShadow = elem } }
          elem.exitSt { hoveringShadow = null }

          elem.clicked { focusOn((it as RecipeGraphLayout.ShadowNode).shadowed) }
          elem.getRecipeCells().forEach { c -> c.addEventBlocker() }
        }

        addRecipeTab(elem)
      }
    }

    layers.forEach { layer ->
      layer.filterIsInstance<RecipeGraphLayout.LineMark>().forEach {
        val origin = it.getOriginNode()
        val target = it.getTargetNode()
        val from = nodeToElement[target] as? RecipeTab?: throw IllegalStateException("Illegal recipe graph structure")
        val to = nodeToElement[origin] as? RecipeTab?: throw IllegalStateException("Illegal recipe graph structure")
        val elem = LineMark(it, from, to)
        addRecipeTab(elem)
      }
    }

    val add = AddRecipeButton(this)
    addRecipeTab(add)
    if (layers.isEmpty()) layers = arrayOf(Seq.with(add.node))
    else layers.first().add(add.node)

    balanceUpdated()
  }

  fun focusOn(node: RecipeGraphLayout.Node) {
    val elem = nodeToElement[node]?:
      throw NoSuchElementException("No such node in this recipe view found, target node: $node")

    panX = -(elem.nodeX + elem.nodeWidth/2f)
    panY = -(elem.nodeY + elem.nodeHeight/2f)
  }

  fun balanceUpdated(){
    isUpdated = true

    graph.forEach {
      it.balanceAmount = -1f
    }
    recipeElements.filterIsInstance<RecipeTab>().forEach { it.setupEnvParameters(it.graphNode.envParameter) }

    var iterated = 0
    val nodeSet = linkedSetOf<RecipeGraphNode>()

    nodeSet.addAll(
      layers.flatMap { it }
        .filterIsInstance<RecipeGraphLayout.RecNode>()
        .filter { it !is RecipeGraphLayout.ShadowNode }
        .map { it.targetNode }
    )

    while (nodeSet.isNotEmpty()) {
      astringentValid = true
      iterated++
      val nl = nodeSet.toList()
      nodeSet.clear()
      nl.forEach { node ->
        node.updateEfficiency()
        if (node.updateBalance()){
          node.visit(visitedSet = nodeSet) { _, _ -> }
        }
      }

      if (iterated >= 100) {
        astringentValid = false
      }
    }

    statistic.reset()
    statistic.updateStatistic()
    graphView.act(0f) // update the balanced amount

    recipeElements.filterIsInstance<RecipeTab>().forEach {
      it.invalidate()
      it.validate()
      it.pack()
    }

    graphView.invalidate()

    rebuildIO()
  }

  private fun addRecipeTab(recipeTab: RecipeGraphElement){
    if (recipeTab is Element) graphView.addChild(recipeTab)
    if (recipeTab is RecipeTab && recipeTab.isShadow) shadowTabs.add(recipeTab)
    recipeElements.add(recipeTab)
    nodeToElement.put(recipeTab.node, recipeTab)
  }

  private fun removeRecipeTab(recipeTab: RecipeGraphElement){
    recipeElements.remove(recipeTab)
    nodeToElement.remove(recipeTab.node)
    if (recipeTab is Element) graphView.removeChild(recipeTab)
    if (recipeTab is RecipeTab && recipeTab.isShadow) shadowTabs.remove(recipeTab)
  }

  private fun clamp() {
    val pad = Scl.scl(40f)
    val bounds = viewBound

    val ox = width/2f
    val oy = height/2f
    var rx = bounds.x + panX + ox
    var ry = panY + oy + bounds.y
    val rw = bounds.width
    val rh = bounds.height
    rx = Mathf.clamp(rx, -rw + pad, width - pad)
    ry = Mathf.clamp(ry, -rh + pad, height - pad)
    panX = rx - bounds.x - ox
    panY = ry - bounds.y - oy
  }

  private fun setZoomListener() {
    addCaptureListener(object : ElementGestureListener() {
      var panEnable: Boolean = false

      override fun zoom(event: InputEvent, initialDistance: Float, distance: Float) {
        zoom.setOrigin(Align.center)

        if (lastZoom < 0) {
          lastZoom = zoom.scaleX
        }

        zoom.setScale(Mathf.clamp(distance/initialDistance*lastZoom, 0.25f, 1f))

        clamp()
      }

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        zoom.setOrigin(Align.center)
        if (button != KeyCode.mouseLeft || pointer != 0) return
        panEnable = true
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (button != KeyCode.mouseLeft || pointer != 0) return
        lastZoom = zoom.scaleX
        panEnable = false
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (!panEnable) return

        panX += deltaX/zoom.scaleX
        panY += deltaY/zoom.scaleY
        clamp()
      }
    })
  }

  private fun setPanListener() {
    addCaptureListener(object : InputListener() {
      override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
        zoom.setScale(Mathf.clamp(zoom.scaleX - amountY/10f*zoom.scaleX, 0.25f, 1f).also { lastZoom = it })

        clamp()
        return true
      }

      override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        requestScroll()
        super.enter(event, x, y, pointer, fromActor)
      }
    })
  }

  //IO
  fun save(file: Fi): Boolean {
    try {
      val writer = Writes(DataOutputStream(file.write(false)))
      graph.write(writer)

      isUpdated = false
    } catch (e: IOException) {
      Log.err(e)
      return false
    }

    return true
  }

  fun load(file: Fi): Boolean {
    try {
      val reader = Reads(DataInputStream(file.read()))
      graph.read(reader)

      graphUpdated()
      isUpdated = false
    } catch (e: IOException) {
      Log.err(e)
      return false
    }

    return true
  }

  fun resetView() {
    panX = 0f
    panY = 0f
    zoom.scaleX = 1f
    zoom.scaleY = 1f
  }

  fun drawToImage(padding: Float, backAlpha: Float) {
    tmpGridAlpha = backAlpha
    val view = getBound()

    val width = view.width + padding*2
    val height = view.height + padding*2

    val dx = view.x - padding
    val dy = view.y - padding

    val camera = Camera()
    camera.width = width
    camera.height = height
    camera.position.x = dx + width/2f
    camera.position.y = dy + height/2f
    camera.update()

    val par = container.parent
    val x = container.x
    val y = container.y
    val sclX = zoom.scaleX
    val sclY = zoom.scaleY
    val scW = Core.scene.viewport.worldWidth
    val scH = Core.scene.viewport.worldHeight

    zoom.scaleX = 1f
    zoom.scaleY = 1f
    container.parent = null
    container.x = 0f
    container.y = 0f
    Core.scene.viewport.worldWidth = width
    Core.scene.viewport.worldHeight = height

    Draw.proj(camera)
    imageGenerating = true
    container.forEach { it.act(0f) }
    container.draw()
    imageGenerating = false
    container.forEach { it.act(0f) }
    Draw.flush()

    container.parent = par
    container.x = x
    container.y = y
    zoom.scaleX = sclX
    zoom.scaleY = sclY
    Core.scene.viewport.worldWidth = scW
    Core.scene.viewport.worldHeight = scH
  }

  data class LinkLine(
    val item: RecipeItem<*>,
    val from: Vec2,
    val to: Vec2,
  ){
    var centerY: Float = (from.y + to.y)/2f
    var isOver = false
  }
}