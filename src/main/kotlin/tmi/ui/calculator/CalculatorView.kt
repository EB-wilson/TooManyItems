package tmi.ui.calculator

import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
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
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Align
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.content.Liquids
import tmi.TooManyItems.Companion.itemsManager
import tmi.TooManyItems.Companion.recipesManager
import tmi.ui.calculator.RecipeGraphElement.*
import kotlin.math.max
import kotlin.math.min

class CalculatorView: Table() {
  var padding = 24f
  var layerMargin = 160f

  private val graph = RecipeGraph()
  private var layers = arrayOf<Seq<RecipeGraphNode>>()

  private val tabList = Seq<RecipeGraphElement>()
  private val nodeToTab = ObjectMap<RecipeGraphNode, RecipeGraphElement>()
  private val linkLines = Seq<LinkLine>()
  private val layerCenter = Seq<Float>()

  private var lastZoom: Float = -1f
  private var panX: Float = 0f
  private var panY: Float = 0f

  val container: Group = object : Group() {
    override fun act(delta: Float) {
      super.act(delta)

      setPosition(panX + zoom.width/2f, panY + zoom.height/2f, Align.center)
    }

    override fun childrenChanged() {
      invalidate()
    }

    override fun layout() {
      // Validate children separately from sizing actors to ensure actors without a cell are validated.
      val children = getChildren()

      children.forEach { it.validate() }

      //nodeToTab.removeAll { it.key.isLineMark }
      layers = graph.generateLayers{
        nodeToTab.put(it, LineMark(it))
      }

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

    fun drawLines(){
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
    val t1 = RecipeTab(recipesManager.getRecipesByFactory(itemsManager.getItem(Blocks.surgeSmelter)).first())
    val t2 = RecipeTab(recipesManager.getRecipesByFactory(itemsManager.getItem(Blocks.siliconCrucible)).first())
    val t3 = RecipeTab(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.coal)).first())
    val t4 = RecipeTab(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.pyratite)).first())
    val t5 = RecipeTab(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.copper)).first())
    val t6 = RecipeTab(recipesManager.getRecipesByProduction(itemsManager.getItem(Liquids.slag)).first())
    val t7 = RecipeTab(recipesManager.getRecipesByProduction(itemsManager.getItem(Items.lead))[3])

    addRecipeTab(t1)
    addRecipeTab(t2)
    addRecipeTab(t3)
    addRecipeTab(t4)
    addRecipeTab(t5)
    addRecipeTab(t6)
    addRecipeTab(t7)

    t1.node.setInput(itemsManager.getItem(Items.silicon), t2.node)
    t1.node.setInput(itemsManager.getItem(Items.copper), t5.node)
    t1.node.setInput(itemsManager.getItem(Items.lead), t7.node)
    t1.node.setInput(itemsManager.getItem(Items.titanium), t5.node)
    t2.node.setInput(itemsManager.getItem(Items.coal), t3.node)
    t2.node.setInput(itemsManager.getItem(Items.pyratite), t4.node)
    t4.node.setInput(itemsManager.getItem(Items.coal), t3.node)
    t5.node.setInput(itemsManager.getItem(Liquids.slag), t6.node)
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
      var maxHeight = 0f

      nodes.forEach {
        val tab = nodeToTab[it]?: return@forEach
        tab.nodeY = currY - tab.nodeHeight
        maxHeight = max(maxHeight, tab.nodeHeight)
      }

      layerCenter.add(currY - maxHeight - (layerMargin + padding)/2f)
      currY -= maxHeight + layerMargin + padding
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
          val line = LinkLine(from, to)

          linkList.add(line)
        }
      }

      if (!linkList.isEmpty) {
        var sumLineCent = 0f
        val centerY = layerCenter[depth]
        linkList.sort { (it.from.x + it.to.x)/2f }

        linkList.forEachIndexed { i, line ->
          line.centerY = centerY

          val lineLeft = min(line.from.x, line.to.x)
          for (r in (i - 1) downTo 0) {
            val checkingLine = linkList[r]
            val checkingRight = max(checkingLine.from.x, checkingLine.to.x)

            if (checkingRight > lineLeft) {
              if (line.from.x > checkingLine.from.x && line.to.x > checkingLine.to.x
              && line.to.x > line.from.x && checkingLine.to.x > checkingLine.from.x) {
                line.centerY = checkingLine.centerY + padding
              }
              else /*if (line.from.x < checkingLine.from.x && line.to.x < checkingLine.to.x)*/ {
                line.centerY = checkingLine.centerY - padding
              }

              break
            }
          }

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

    zoom.addChild(container)
    fill { t -> t.add(zoom).grow() }

    touchable = Touchable.enabled
    setPanListener()
    setZoomListener()
  }

  fun addRecipeTab(recipeTab: RecipeTab){
    container.addChild(recipeTab)
    tabList.add(recipeTab)
    nodeToTab.put(recipeTab.node, recipeTab)
    graph.addNode(recipeTab.node)
  }

  fun removeRecipeTab(recipeTab: RecipeTab){
    tabList.remove(recipeTab)
    nodeToTab.remove(recipeTab.node)
    container.removeChild(recipeTab)
    graph.removeNode(recipeTab.node)
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
    val from: Vec2,
    val to: Vec2,
  ){
    var centerY: Float = (from.y + to.y)/2f
  }
}