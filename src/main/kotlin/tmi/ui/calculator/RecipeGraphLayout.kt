package tmi.ui.calculator

import arc.struct.Seq
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import java.util.Stack

object RecipeGraphLayout {
  fun generateLayout(graph: RecipeGraph): Array<Seq<Node>> {
    if (graph.isEmpty()) return emptyArray()

    val copyMap = mutableMapOf<RecipeGraphNode, RecNode>()
    val nodeList = mutableListOf<Node>()

    graph.eachNode { depth, node ->
      if (node.graph != graph) return@eachNode

      val rec = RecNode(node, node.recipe)
      rec.contextDepth = depth
      copyMap[node] = rec
      nodeList.add(rec)
    }

    graph.eachNode { node ->
      val rec = copyMap[node] ?: run {
        CalculatorLayout.defect("The linked node in layout node not exist, Illegal node format.")
        return@eachNode
      }

      node.parentsWithItem().forEach { (item, parent) ->
        val cpyParent = copyMap[parent]
        if (cpyParent == null) CalculatorLayout.defect("No such input in this recipe: ${item.name}, recipe: ${node.recipe}")
        else rec.setInput(item, cpyParent)
      }
      node.childrenWithItem().forEach { (item, children) ->
        children.forEach { child ->
          val cpyChild = copyMap[child]
          if (cpyChild == null) CalculatorLayout.defect("No such output in this recipe: ${item.name}, recipe: ${node.recipe}")
          else rec.setOutput(item, cpyChild)
        }
      }
    }

    val layered = nodeList.sortedBy { it.contextDepth }
    val resolved = reduceLongEdges(resolveLoops(layered))

    val layers = Array(resolved.maxOf { it.contextDepth } + 1){ Seq<Node>() }
    resolved.forEach { node ->
      val depth = node.contextDepth
      layers[depth].add(node)
    }

    val insertedLayers = insertLineMark(layers)
    val sortedLayers = sortLayers(insertedLayers)

    return sortedLayers
  }

  /**
   * 长边压缩：重新分配层号，让**全图连线长度之和**最小（连线每跨一层记 1），
   * 也就是「长边（跨层连接边）长度最小化」这一步。
   *
   * 约束有两条，都是界面语义要求的：
   * 1. 提供方必须比消费方至少深一层（连线的方向不许反）；
   * 2. 最终产物（没有下游消费者的节点）必须留在第 0 层 —— [Node.isRoot] 就是 `contextDepth == 0`，
   *    [RecipeTab] 靠它决定显示目标产量输入框还是倍率。
   *
   * 在这两条约束下最小化 Σ(层号差) 是个整数规划，[RecipeLayerAssignment] 用一次最大流
   * （最大权闭合子图）求出精确最优解；这里只负责把布局节点转成它的邻接表、再把结果写回
   * `contextDepth`。同一对节点之间按 item 连几条线就算几条。
   *
   * @return 传入的 [nodes]（层号已就地更新）。图里仍有环、或者规模超出求解器护栏时保持原层号。
   */
  private fun reduceLongEdges(nodes: List<Node>): List<Node> {
    if (nodes.size <= 1) return nodes

    val index = HashMap<Node, Int>(nodes.size)
    nodes.forEachIndexed { i, node -> index[node] = i }

    val consumers = Array(nodes.size) { ArrayList<Int>(4) }
    val providers = Array(nodes.size) { ArrayList<Int>(4) }
    nodes.forEachIndexed { i, node ->
      node.children().forEach { child ->
        val childIndex = index[child] ?: return@forEach

        consumers[i].add(childIndex)
        providers[childIndex].add(i)
      }
    }

    val depth = RecipeLayerAssignment.solve(
      Array(nodes.size) { consumers[it].toIntArray() },
      Array(nodes.size) { providers[it].toIntArray() },
    ) { CalculatorLayout.warnOnce(it) }
    if (depth == null) {
      CalculatorLayout.warnOnce("Long edge reduction skipped: the layout graph still contains a cycle.")
      return nodes
    }

    nodes.forEachIndexed { i, node -> node.contextDepth = depth[i] }
    return nodes
  }

  private fun sortLayers(layers: Array<Seq<Node>>): Array<Seq<Node>> {
    val swap = layers.map { it.copy() }

    for (i in 1..<swap.size) {
      val ref = swap[i - 1]
      val sorting = swap[i]
      val order = FloatArray(sorting.size)

      val sortIndex = HashMap<Node, Int>(sorting.size)
      sorting.forEachIndexed { l, node -> sortIndex[node] = l }

      val refIndex = HashMap<Node, Int>(ref.size)
      ref.forEachIndexed { l, node -> refIndex[node] = l }

      for (l in 0..<sorting.size) {
        val node = sorting[l]
        var o = 0f
        val children = node.children()
        for (child in children) {
          o += (refIndex[child] ?: -1).toFloat()
        }
        order[l] = o/children.size
      }

      sorting.sort{ a, b -> order[sortIndex.getValue(a)].compareTo(order[sortIndex.getValue(b)]) }
      sorting.forEachIndexed { i, node -> node.layerIndex = i }
    }

    return swap.toTypedArray()
  }

  private fun Node.findLoop(other: Node): Boolean {
    val checkingSet: MutableSet<Node> = mutableSetOf()
    val stack = Stack<Node>()

    stack.push(this)

    var findOther = false
    var isLoop = false
    while (!stack.empty()) {
      val node = stack.pop()
      if (node == other) findOther = true
      if (checkingSet.add(node)){
        node.parents().forEach { parent ->
          if (parent == this@findLoop) isLoop = true
          stack.push(parent)
        }
      }

      if (findOther && isLoop) break
    }

    return findOther && isLoop
  }

  private fun standardDepth(nodes: List<Node>): List<Node> {
    var anySorted = true

    while (anySorted) {
      anySorted = false
      nodes.forEach { node ->
        node.parents().forEach { parent ->
          if (parent != node && parent.contextDepth <= node.contextDepth) {
            parent.contextDepth = node.contextDepth + 1
            anySorted = true
          }
        }
      }
    }

    return nodes
  }

  private fun resolveLoops(nodes: List<Node>): List<Node> {
    val swap = mutableListOf<Node>()
    val shadowedMap = mutableMapOf<Node, ShadowNode>()

    nodes.forEach { node ->
      swap.add(node)
      if (node !is RecNode) return@forEach
      node.parentsWithItem().forEach { (item, parent) ->
        if (parent is RecNode && ((parent.contextDepth < node.contextDepth && parent.findLoop(node)) || parent == node)) {
          val shadowed = shadowedMap.computeIfAbsent(parent) { _ ->
            ShadowNode(parent).also { swap.add(it) }
          }
          node.disInput(item)
          node.linkInput(item, shadowed)

          standardDepth(swap)
        }
      }
    }

    return standardDepth(swap)
  }

  private fun insertLineMark(layers: Array<Seq<Node>>): Array<Seq<Node>> {
    val swap = layers.map { it.copy() }

    for (node in layers.flatMap { it }) {
      if (node is LineMark) continue
      node as RecNode
      for (pair in node.parentsWithItem()) {
        val item = pair.key
        val parent = pair.value

        if (parent.contextDepth - node.contextDepth > 1) {
          val stack = node.recipe.getMaterial(item)
          if (stack == null) {
            CalculatorLayout.defect("No such item in recipe found ${item.name}，recipe: ${node.recipe}")
            continue
          }

          node.disInput(item)

          var curr = node
          for (dep in 1..<parent.contextDepth - node.contextDepth) {
            val fc = curr

            val lay = swap[node.contextDepth + dep]
            var ins = lay.find { n -> n is LineMark && n.stack.item == item && n.child == fc }
            if (ins == null) {
              ins = LineMark(stack)
              curr.linkInput(item, ins)
              ins.contextDepth = node.contextDepth + dep
              lay.add(ins)
            }
            curr = ins
          }
          curr.linkInput(item, parent)
        }
      }
    }

    return swap.toTypedArray()
  }

  abstract class Node{
    internal var contextDepth = 0
    internal var layerIndex = 0

    val isRoot: Boolean get() = contextDepth == 0

    abstract fun parents(): List<Node>
    abstract fun parentsWithItem(): Map<RecipeItem<*>, Node>

    abstract fun children(): List<Node>
    abstract fun childrenWithItem(): Map<RecipeItem<*>, List<Node>>

    fun disInput(item: RecipeItem<*>) {
      val parents = parentsWithItem()
      unInput(item)
      parents[item]?.unOutput(item, this)
    }
    fun linkInput(item: RecipeItem<*>, parent: Node) {
      setInput(item, parent)
      parent.setOutput(item, this)
    }

    abstract fun setOutput(item: RecipeItem<*>, child: Node)
    abstract fun unOutput(item: RecipeItem<*>, child: Node)
    abstract fun setInput(item: RecipeItem<*>, parent: Node)
    abstract fun unInput(item: RecipeItem<*>)
  }

  open class RecNode(
    val targetNode: RecipeGraphNode,
    val recipe: Recipe,
  ): Node(){
    private val outputs = mutableMapOf<RecipeItem<*>, MutableList<Node>>()
    private val inputs = mutableMapOf<RecipeItem<*>, Node>()

    override fun parents() = inputs.values.toList()
    override fun parentsWithItem() = inputs.toMap()

    override fun children() = outputs.values.flatMap { it }
    override fun childrenWithItem() = outputs.toMap()

    override fun setOutput(item: RecipeItem<*>, child: Node) {
      outputs.computeIfAbsent(item) { mutableListOf() }.add(child)
    }

    override fun unOutput(item: RecipeItem<*>, child: Node) {
      val outs = outputs[item]?: return
      outs.remove(child)

      if (outs.isEmpty()) outputs.remove(item)
    }

    override fun setInput(item: RecipeItem<*>, parent: Node) {
      inputs[item] = parent
    }

    override fun unInput(item: RecipeItem<*>) {
      inputs.remove(item)
    }
  }

  class ShadowNode(
    val shadowed: RecNode
  ): RecNode(shadowed.targetNode, shadowed.recipe)

  class LineMark(
    val stack: RecipeItemStack<*>
  ) : Node() {
    var parent: Node? = null
    var child: Node? = null

    fun getTargetNode(): RecNode? = parent?.let { if (it is LineMark) it.getTargetNode() else it as RecNode }
    fun getOriginNode(): RecNode? = child?.let { if (it is LineMark) it.getOriginNode() else it as RecNode }

    override fun parents() = listOfNotNull(parent)
    override fun parentsWithItem() = parent?.let { mapOf(stack.item to it) } ?: emptyMap()

    override fun children() = listOfNotNull(child)
    override fun childrenWithItem() = child?.let { mapOf(stack.item to listOf(it)) } ?: emptyMap()

    override fun setInput(item: RecipeItem<*>, parent: Node) {
      if (item != stack.item) throw IllegalArgumentException("Item does not match the recipe item.")
      this.parent = parent
    }

    override fun unInput(item: RecipeItem<*>) {
      throw UnsupportedOperationException("line mark does not support un-output items.")
    }

    override fun setOutput(item: RecipeItem<*>, child: Node) {
      if (item != stack.item) throw IllegalArgumentException("Item does not match the recipe item.")
      this.child = child
    }

    override fun unOutput(item: RecipeItem<*>, child: Node) {
      throw UnsupportedOperationException("line mark does not support un-output items.")
    }
  }
}
