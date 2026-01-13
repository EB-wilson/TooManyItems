package tmi.ui.calculator

import arc.graphics.Color
import arc.graphics.g2d.Lines
import arc.input.KeyCode
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.Group
import arc.scene.event.ElementGestureListener
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.ui.Button
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Align
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.content.Liquids
import mindustry.gen.Icon
import tmi.TooManyItems.Companion.itemsManager
import tmi.TooManyItems.Companion.recipesManager
import tmi.recipe.types.RecipeItem
import tmi.ui.RecipesDialog
import tmi.ui.TmiUI
import tmi.ui.calculator.RecipeGraphElement.*
import kotlin.math.max
import kotlin.math.min

class CalculatorView: Table() {
  var padding = 24f
  var layerMargin = 160f

  var graph = RecipeGraph()
    set(value) {
      field = value
      updateGraph()
    }

  private var layers: Array<Seq<RecipeGraphLayout.Node>> = arrayOf()

  private val tabList = Seq<RecipeGraphElement>()
  private val nodeToTab = ObjectMap<RecipeGraphLayout.Node, RecipeGraphElement>()
  private val linkLines = Seq<LinkLine>()
  private val layerCenter = Seq<Float>()

  private var lastZoom: Float = -1f
  private var panX: Float = 0f
  private var panY: Float = 0f

  private val graphView: Group = object : Group() {
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
      super.draw()
    }

    private fun drawLines(){
      Lines.stroke(Scl.scl(5f), Color.gray)
      linkLines.forEach { line ->
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
    }
  }
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
      val validNodes = tabList
        .filterIsInstance<RecipeTab>()
        .filter { it.node.recipe.productions.containsKey(item) }

      selecting = item
      visible = true

      clearChildren()
      validNodes.forEach { tabs ->
        val select = Button()
        select.clicked {
          node.setInput(item, tabs.node.targetNode)
          updateGraph()
          hide()
        }
        select.setBounds(tabs.x, tabs.y, tabs.width, tabs.height)
        addChild(select)
      }

      return validNodes.any()
    }
  }
  val container = object : Group() {
    override fun act(delta: Float) {
      super.act(delta)

      setPosition(panX + zoom.width/2f, panY + zoom.height/2f, Align.center)
    }

    override fun draw() {
      validate()
      super.draw()
    }
  }

  val zoom: Group = object : Group() {
    init {
      setFillParent(true)
      isTransform = true
    }

    override fun draw() {
      validate()
      super.draw()
    }
  }

  init {
    val t1 = RecipeGraphNode(recipesManager.getRecipesByFactory(itemsManager.getItem(Blocks.surgeSmelter)).first())
    val t2 = RecipeGraphNode(recipesManager.getRecipesByFactory(itemsManager.getItem(Blocks.siliconCrucible)).first())
    val t3 = RecipeGraphNode(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.coal)).first())
    val t4 = RecipeGraphNode(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.pyratite)).first())
    val t5 = RecipeGraphNode(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.copper)).first())
    val t6 = RecipeGraphNode(recipesManager.getRecipesByProduction(itemsManager.getItem(Liquids.slag)).first())
    val t7 = RecipeGraphNode(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.lead))[3])
    val t8 = RecipeGraphNode(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.sand))[2])

    graph.addNode(t1)
    graph.addNode(t2)
    graph.addNode(t3)
    graph.addNode(t4)
    graph.addNode(t5)
    graph.addNode(t6)
    graph.addNode(t7)
    graph.addNode(t8)

    t1.setInput(itemsManager.getItem(Items.silicon), t2)
    t1.setInput(itemsManager.getItem(Items.copper), t5)
    t1.setInput(itemsManager.getItem(Items.lead), t7)
    t1.setInput(itemsManager.getItem(Items.titanium), t5)
    t2.setInput(itemsManager.getItem(Items.coal), t3)
    t2.setInput(itemsManager.getItem(Items.pyratite), t4)
    t2.setInput(itemsManager.getItem(Items.sand), t8)
    t4.setInput(itemsManager.getItem(Items.coal), t3)
    t4.setInput(itemsManager.getItem(Items.lead), t7)
    t4.setInput(itemsManager.getItem(Items.sand), t8)
    t5.setInput(itemsManager.getItem(Liquids.slag), t6)

    updateGraph()
  }

  fun layoutRecipeTabs(){
    val layers = this.layers
    val maxLayerWidth = padding + layers.maxOf { it.sumf{ tab -> (nodeToTab[tab]?.nodeWidth ?: 8f) + padding } }
    val root = layers[0]

    layerCenter.clear()

    if (root.size <= 0) return
    else if (root.size > 1) {
      val rootWidth = padding + root.sumf { tab -> (nodeToTab[tab]?.nodeWidth ?: 8f) + padding }
      val diff = maxLayerWidth - rootWidth
      val rootDelta = diff/root.size

      var currX = -maxLayerWidth/2f

      root.forEach {
        val tab = nodeToTab[it]
        tab.nodeX = currX + rootDelta/2
        currX += tab.nodeWidth + padding + rootDelta
      }
    }
    else {
      val tab = nodeToTab[root.first()]
      tab.nodeX = -tab.nodeWidth/2
    }

    var currY = 0f
    layers.forEach { nodes ->
      val layerHeight = nodes.maxOf { nodeToTab[it]?.nodeHeight?: 0f }

      nodes.forEach {
        val tab = nodeToTab[it]?: return@forEach
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
        val layoutTab = nodeToTab[node] ?: return@a
        if (node.parents().isEmpty()) return@a

        val parents = node.parentsWithItem()

        var n = 0
        var sumX = 0f
        var sumOffX = 0f

        parents.forEach b@{ (item, nodes) ->
          val outOff = layoutTab.outputOffset(item).x

          nodes.forEach c@{ parent ->
            val parentTab = nodeToTab[parent]?: return@b
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
  }

  private fun resolveOverlaps(
    overlaps: Seq<RecipeGraphElement>,
    layoutTab: RecipeGraphElement
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
        val tab = nodeToTab[node]?: return@forEach
        val children = node.childrenWithItem()
        children.forEach a@{ (item, child) ->
          val linked = nodeToTab[child] ?: return@a
          val from = tab.inputOffset(item).add(tab.nodeX, tab.nodeY)
          val to = linked.outputOffset(item).add(linked.nodeX, linked.nodeY)
          val line = LinkLine(item, from, to)

          linkList.add(line)
        }
      }

      if (!linkList.isEmpty) {
        var sumLineCent = 0f
        val centerY = layerCenter[depth]
        linkList.sort { it.to.x + if (it.from.x > it.to.x) 1 else -1 }

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

            if (checkingRight < lineLeft) break

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

  fun build() {
    clear()

    graphView.setBounds(0f, 0f, 0f, 0f)
    tabSelectors.setBounds(0f, 0f, 0f, 0f)

    container.addChild(graphView)
    container.addChild(tabSelectors)
    zoom.addChild(container)
    fill { t -> t.add(zoom).grow() }

    touchable = Touchable.enabled
    setPanListener()
    setZoomListener()
  }

  fun showRecipeSelector(
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
          graphNode.setInput(item, newNode)
          updateGraph()

          hide()
        }
      }
    }
  }
  
  fun updateGraph() {
    tabList.clear()
    nodeToTab.clear()
    graphView.clearChildren()

    layers = RecipeGraphLayout.generateLayout(graph)
    layers.forEach { layer -> layer.forEach {
      val elem =
        if (it is RecipeGraphLayout.LineMark) LineMark(it)
        else RecipeTab(it as RecipeGraphLayout.RecNode, this)
      addRecipeTab(elem)
    } }
  }

  private fun addRecipeTab(recipeTab: RecipeGraphElement){
    if (recipeTab is RecipeTab) graphView.addChild(recipeTab)
    tabList.add(recipeTab)
    nodeToTab.put(recipeTab.node, recipeTab)
  }

  private fun removeRecipeTab(recipeTab: RecipeGraphElement){
    tabList.remove(recipeTab)
    nodeToTab.remove(recipeTab.node)
    if (recipeTab is RecipeTab) graphView.removeChild(recipeTab)
  }

  private fun clamp() {


  }

  private fun setZoomListener() {
    addListener(object : ElementGestureListener() {
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
    addListener(object : InputListener() {
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

  private data class LinkLine(
    val item: RecipeItem<*>,
    val from: Vec2,
    val to: Vec2,
  ){
    var centerY: Float = (from.y + to.y)/2f
  }
}