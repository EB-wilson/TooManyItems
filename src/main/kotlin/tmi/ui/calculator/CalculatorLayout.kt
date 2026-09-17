package tmi.ui.calculator

import arc.math.geom.Rect
import arc.math.geom.Vec2
import arc.struct.Seq
import arc.util.Log
import tmi.recipe.types.RecipeItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object CalculatorLayout {
  // ==================== 魔数 ====================
  /** 同层节点之间的最小水平间距；同时也是同一通道带里相邻走线之间的堆叠步长 */
  const val PADDING = 24f
  /** 预置层高：相邻两层节点行之间预留的通道带高度。实际层高取它与「该层排线所需高度」的最大值 */
  const val LAYER_MARGIN = 160f
  /** 排序键中次级项的权重：目标 x 相同时按起点 x 稳定排序（量级远小于 1 像素，不改变主序） */
  const val SORT_TIE_WEIGHT = 0.0001f
  /** 水平区间重叠判定的容差：贴边的线也算重叠，避免差零点几像素时漏判 */
  const val OVERLAP_EPSILON = 0.1f
  /** 占位符（线标记）与真实节点之间需要的最小避让距离 */
  /** 元素缺失（降级）时用于估算宽度的兜底值 */
  const val MISSING_NODE_WIDTH = 8f
  /** 连线描边的设计线宽（实际绘制时再乘 Scl） */
  const val LINE_STROKE = 5f
  /** 连线描边的偏移量：同一根线先用深色偏移画一遍，再画本体 */
  const val LINE_OUTLINE_OFFSET = 5f
  /** 跨层长边连回真身时虚线的虚线节距 */
  const val SHADOW_DASH = 18f
  /** 多个影子节点连回真身时，每条线错开的横向距离 */
  const val SHADOW_LANE_STEP = 16f
  /** 影子连线折向图左侧时距边界的内缩量 */
  const val SHADOW_SIDE_OFFSET = 45f
  /** 背景网格线宽 */
  const val GRID_STROKE = 4f
  /** 背景网格间距的默认值（可被设置项 tmi_gridSize 覆盖） */
  const val GRID_SIZE_DEFAULT = 150

  data class LinkLine(
    val item: RecipeItem<*>,
    val from: Vec2,
    val to: Vec2,
  ) {
    var centerY: Float = (from.y + to.y)/2f
    var isOver: Boolean = false
  }

  fun layoutNodes(
    layers: Array<Seq<RecipeGraphLayout.Node>>,
    elements: (RecipeGraphLayout.Node) -> RecipeGraphElement?,
    bounds: Rect,
    layerCenter: Seq<Float>,
  ) {
    bounds.setSize(0f)
    layerCenter.clear()

    if (layers.isEmpty()) return

    val maxLayerWidth = PADDING + layers.maxOf { layer ->
      layer.sumf { node -> (elements(node)?.nodeWidth ?: MISSING_NODE_WIDTH) + PADDING }
    }
    val root = layers[0]

    if (root.size <= 0) return

    if (root.size > 1) {
      val rootWidth = PADDING + root.sumf { node -> (elements(node)?.nodeWidth ?: MISSING_NODE_WIDTH) + PADDING }
      val diff = maxLayerWidth - rootWidth
      val rootDelta = diff/root.size

      var currX = -maxLayerWidth/2f

      root.forEach { node ->
        val tab = elements(node) ?: return@forEach
        tab.nodeX = currX + rootDelta/2
        currX += tab.nodeWidth + PADDING + rootDelta
      }
    }
    else {
      val tab = elements(root.first()) ?: return
      tab.nodeX = -tab.nodeWidth/2
    }

    val bandHeights = FloatArray(layers.size) { LAYER_MARGIN + PADDING }
    layers.forEachIndexed { depth, nodes ->
      val lines = nodes.sumf { node -> node.parentsWithItem().size.toFloat() }
      bandHeights[depth] = max(bandHeights[depth], (lines + 1)*PADDING)
    }

    var currY = 0f
    layers.forEachIndexed { depth, nodes ->
      val layerHeight = nodes.maxOf { elements(it)?.nodeHeight ?: 0f }

      nodes.forEach { node ->
        val tab = elements(node) ?: return@forEach
        val diff = layerHeight - tab.nodeHeight
        tab.nodeY = currY - tab.nodeHeight - diff/2f
      }

      val band = bandHeights[depth]
      layerCenter.add(currY - layerHeight - band/2f)
      currY -= layerHeight + band
    }

    val overlaps = Seq<RecipeGraphElement>()
    for (depth in 1..<layers.size) {
      overlaps.clear()

      layers[depth].forEach { node ->
        val layoutTab = elements(node) ?: return@forEach
        if (node.children().isEmpty()) return@forEach

        var n = 0
        var sumX = 0f
        var sumOffX = 0f

        node.childrenWithItem().forEach { (item, children) ->
          val outOff = layoutTab.outputOffset(item)?.x ?: return@forEach

          children.forEach { child ->
            val childTab = elements(child) ?: return@forEach
            val inOff = childTab.inputOffset(item)?.x ?: return@forEach

            n++
            sumOffX += outOff
            sumX += childTab.nodeX + inOff
          }
        }

        if (n <= 0) return@forEach

        layoutTab.nodeX = sumX/n - sumOffX/n

        resolveOverlaps(overlaps, layoutTab)
      }
    }

    var first = true
    for (layer in layers) for (node in layer) {
      val tab = elements(node) ?: continue

      if (first) {
        bounds.set(tab.nodeX, tab.nodeY, tab.nodeWidth, tab.nodeHeight)
        first = false
      }
      else {
        bounds.merge(tab.nodeX, tab.nodeY)
        bounds.merge(tab.nodeX + tab.nodeWidth, tab.nodeY + tab.nodeHeight)
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
        val overlapping = checkingTab.nodeX - (layoutTab.nodeX + layoutTab.nodeWidth + PADDING)
        if (overlapping < 0) {
          val move = overlapping/2f
          layoutTab.nodeX += move
        }
      }
      if (insertIndex >= overlaps.size - 1) {
        val checkingTab = overlaps[overlaps.size - 2]

        val overlapping = layoutTab.nodeX - (checkingTab.nodeX + checkingTab.nodeWidth + PADDING)
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
        val overlapping = curr.nodeX - (checkingTab.nodeX + checkingTab.nodeWidth + PADDING)
        if (overlapping < 0) checkingTab.nodeX += overlapping
        curr = checkingTab
      }
    }
    if (remRight < overlaps.size) {
      var curr = layoutTab
      (remRight..<overlaps.size).forEach { i ->
        val checkingTab = overlaps[i]
        val overlapping = checkingTab.nodeX - (curr.nodeX + curr.nodeWidth + PADDING)
        if (overlapping < 0) checkingTab.nodeX -= overlapping
        curr = checkingTab
      }
    }
  }

  fun layoutLinks(
    layers: Array<Seq<RecipeGraphLayout.Node>>,
    elements: (RecipeGraphLayout.Node) -> RecipeGraphElement?,
    layerCenter: Seq<Float>,
    out: Seq<LinkLine>,
    onLine: (tab: RecipeGraphElement, linked: RecipeGraphElement, line: LinkLine) -> Unit,
  ) {
    out.clear()

    layers.forEachIndexed { depth, layer ->
      val linkList = Seq<LinkLine>()

      layer.forEach { node ->
        val tab = elements(node) ?: return@forEach

        node.parentsWithItem().forEach { (item, parent) ->
          val linked = elements(parent) ?: return@forEach
          val from = tab.inputOffset(item)?.cpy()?.add(tab.nodeX, tab.nodeY) ?: return@forEach
          val to = linked.outputOffset(item)?.cpy()?.add(linked.nodeX, linked.nodeY) ?: return@forEach

          val line = LinkLine(item, from, to)

          onLine(tab, linked, line)
          linkList.add(line)
        }
      }

      if (linkList.isEmpty) return@forEachIndexed

      if (depth >= layerCenter.size) {
        warnOnce("The center of the channel is missing (layer $depth), this layer's connections skip routing allocation")
        return@forEachIndexed
      }

      assignChannelY(linkList, layerCenter[depth])
      out.addAll(linkList)
    }
  }

  private fun assignChannelY(linkList: Seq<LinkLine>, centerY: Float) {
    var sumLineCent = 0f

    linkList.sort { it.to.x + (it.from.x - it.to.x)*SORT_TIE_WEIGHT }
    linkList.forEachIndexed { i, line ->
      val lineLeft = min(line.from.x, line.to.x) - OVERLAP_EPSILON
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
          line.centerY = upper + PADDING
        }
        else {
          line.centerY = lower - PADDING
        }
      }
      else if (n == 0) line.centerY = centerY

      sumLineCent += line.centerY
    }

    val off = sumLineCent/linkList.size - centerY
    linkList.forEach { it.centerY -= off }

    linkList.sort { it.to.x + abs(it.from.x - it.to.x)*SORT_TIE_WEIGHT }
  }

  class GraphDefectException(message: String): RuntimeException(message)

  private var lenient = false

  private var buildHadDefect = false

  val isLenient: Boolean get() = lenient

  fun beginBuild() {
    buildHadDefect = false
  }

  fun endBuild() {
    if (lenient && !buildHadDefect) lenient = false
  }

  fun forceOpen() {
    lenient = true
  }

  fun resetPolicy() {
    lenient = false
    buildHadDefect = false
  }

  internal fun defect(message: String) {
    buildHadDefect = true
    if (!lenient) throw GraphDefectException(message)
    warnOnce(message)
  }

  private val warned = HashSet<String>()

  internal fun warnOnce(message: String) {
    if (warned.add(message)) Log.warn("[TMI/calculator-layout] $message")
  }
}
