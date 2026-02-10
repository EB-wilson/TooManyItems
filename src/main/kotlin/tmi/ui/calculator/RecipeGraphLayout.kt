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
      val rec = RecNode(node, node.recipe)
      rec.contextDepth = depth
      copyMap[node] = rec
      nodeList.add(rec)
    }

    graph.eachNode { node ->
      val rec = copyMap[node]?: throw NoSuchElementException()

      node.childrenWithItem().forEach { (item, child) ->
        val cpyCld = copyMap[child]?: throw NoSuchElementException()
        rec.setInput(item, cpyCld)
      }
      node.parentsWithItem().forEach { (item, parents) ->
        parents.forEach { parent ->
          val cpyParents = copyMap[parent]?: throw NoSuchElementException()
          rec.setOutput(item, cpyParents)
        }
      }
    }

    val layered = nodeList.sortedBy { it.contextDepth }
    val resolved = resolveLoops(layered)

    val layers = Array(resolved.maxOf { it.contextDepth } + 1){ Seq<Node>() }
    resolved.forEach { node ->
      val depth = node.contextDepth
      layers[depth].add(node)
    }

    val insertedLayers = insertLineMark(layers)
    val sortedLayers = sortLayers(insertedLayers)

    return sortedLayers
  }

  private fun sortLayers(layers: Array<Seq<Node>>): Array<Seq<Node>> {
    val swap = layers.map { it.copy() }

    for (i in 1..<swap.size) {
      val ref = swap[i - 1]
      val sorting = swap[i]
      val order = FloatArray(sorting.size)

      for (l in 0..<sorting.size) {
        val node = sorting[l]
        var o = 0f
        val parents = node.parents()
        for (parent in parents) {
          o += ref.indexOf(parent).toFloat()
        }
        order[l] = o/parents.size
      }

      sorting.sort{ a, b -> order[sorting.indexOf(a)].compareTo(order[sorting.indexOf(b)]) }
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
        node.children().forEach { child ->
          if (child == this@findLoop)
            isLoop = true
          stack.push(child)
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
        node.children().forEach { child ->
          if (child.contextDepth <= node.contextDepth) {
            child.contextDepth = node.contextDepth + 1
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
      node.childrenWithItem().forEach { (item, child) ->
        if (child is RecNode && child.contextDepth < node.contextDepth && child.findLoop(node)) {
          val shadowed = shadowedMap.computeIfAbsent(child) { _ ->
            ShadowNode(child).also { swap.add(it) }
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
      for (pair in node.childrenWithItem()) {
        val item = pair.key
        val child = pair.value

        if (child.contextDepth - node.contextDepth > 1) {
          node.disInput(item)

          var curr = node
          for (dep in 1..<child.contextDepth - node.contextDepth) {
            val fc = curr

            val lay = swap[node.contextDepth + dep]
            var ins = lay.find { n ->
              val parent = n.parentsWithItem().entries.firstOrNull()?: return@find false
              n is LineMark && parent.key == item && parent.value == fc
            }
            if (ins == null) {
              val stack = node.recipe.getMaterial(item)
                          ?: throw IllegalStateException("insert lineMarks with item $item, but no such item consuming in the recipe found.")
              ins = LineMark(stack)
              curr.linkInput(item, ins)
              ins.contextDepth = node.contextDepth + dep
              lay.add(ins)
            }
            curr = ins
          }
          curr.linkInput(item, child)
        }
      }
    }

    return swap.toTypedArray()
  }

  abstract class Node{
    internal var contextDepth = 0
    internal var layerIndex = 0

    abstract fun parents(): List<Node>
    abstract fun parentsWithItem(): Map<RecipeItem<*>, List<Node>>

    abstract fun children(): List<Node>
    abstract fun childrenWithItem(): Map<RecipeItem<*>, Node>

    fun disInput(item: RecipeItem<*>) {
      val children = childrenWithItem()
      unInput(item)
      children[item]?.unOutput(item, this)
    }
    fun linkInput(item: RecipeItem<*>, ins: Node) {
      setInput(item, ins)
      ins.setOutput(item, this)
    }

    abstract fun setOutput(item: RecipeItem<*>, ins: Node)
    abstract fun unOutput(item: RecipeItem<*>, ins: Node)
    abstract fun setInput(item: RecipeItem<*>, ins: Node)
    abstract fun unInput(item: RecipeItem<*>)
  }

  open class RecNode(
    val targetNode: RecipeGraphNode,
    val recipe: Recipe,
  ): Node(){
    private val outputs = mutableMapOf<RecipeItem<*>, MutableList<Node>>()
    private val inputs = mutableMapOf<RecipeItem<*>, Node>()

    override fun parents() = outputs.values.flatMap { it }
    override fun parentsWithItem() = outputs.toMap()
    override fun children() = inputs.values.toList()
    override fun childrenWithItem() = inputs.toMap()

    override fun setOutput(item: RecipeItem<*>, ins: Node) {
      outputs.computeIfAbsent(item) { mutableListOf() }.add(ins)
    }

    override fun unOutput(item: RecipeItem<*>, ins: Node) {
      val outs = outputs[item]?: return
      outs.remove(ins)

      if (outs.isEmpty()) outputs.remove(item)
    }

    override fun setInput(item: RecipeItem<*>, ins: Node) {
      inputs[item] = ins
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

    fun getTargetNode(): RecNode = child?.let { if (it is LineMark) it.getTargetNode() else it as RecNode }?:
      throw IllegalStateException("This line mark was not linked to any recipe node.")
    fun getOriginNode(): RecNode = parent?.let { if (it is LineMark) it.getOriginNode() else it as RecNode }?:
      throw IllegalStateException("This line mark was not linked to any recipe node.")

    override fun parents() = listOf(parent!!)
    override fun parentsWithItem() = mapOf(stack.item to listOf(parent!!))

    override fun children() = listOf(child!!)
    override fun childrenWithItem() = mapOf(stack.item to child!!)

    override fun setInput(item: RecipeItem<*>, ins: Node) {
      if (item != stack.item) throw IllegalArgumentException("Item does not match the recipe item.")
      child = ins
    }

    override fun unInput(item: RecipeItem<*>) {
      throw UnsupportedOperationException("line mark does not support un-output items.")
    }

    override fun setOutput(item: RecipeItem<*>, ins: Node) {
      if (item != stack.item) throw IllegalArgumentException("Item does not match the recipe item.")
      parent = ins
    }

    override fun unOutput(item: RecipeItem<*>, ins: Node) {
      throw UnsupportedOperationException("line mark does not support un-output items.")
    }
  }
}