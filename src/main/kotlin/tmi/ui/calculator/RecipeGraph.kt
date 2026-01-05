package tmi.ui.calculator

import arc.func.Cons
import arc.struct.Seq
import tmi.ui.calculator.RecipeGraphElement.*
import kotlin.Array
import kotlin.FloatArray

class RecipeGraph{
  private val recipeNodes = Seq<RecipeGraphNode>()

  fun addNode(node: RecipeGraphNode){
    recipeNodes.add(node)
  }

  fun removeNode(node: RecipeGraphNode) {
    recipeNodes.remove(node)
  }

  fun clear(){
    recipeNodes.clear()
  }

  fun generateLayers(lineMarkCallBack: Cons<RecipeGraphNode>): Array<Seq<RecipeGraphNode>>{
    //recipeNodes.removeAll { it.isLineMark }

    var maxDepth = -1
    for (root in recipeNodes.select({ e -> e.parents().isEmpty() })) {
      root.visitTree { depth, node ->
        node.contextDepth = depth
        maxDepth = maxDepth.coerceAtLeast(depth)
      }
    }

    val layers = Array(maxDepth + 1){ Seq<RecipeGraphNode>() }
    recipeNodes.forEach { node ->
      val depth = node.contextDepth
      layers[depth].add(node)
    }

    val standardizeLayers = standardLayers(layers)
    val insertedLayers = insertLineMark(standardizeLayers, lineMarkCallBack)
    val sortedLayers = sortLayers(insertedLayers)

    return sortedLayers
  }

  private fun sortLayers(layers: Array<Seq<RecipeGraphNode>>): Array<Seq<RecipeGraphNode>> {
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

  private fun standardLayers(layers: Array<Seq<RecipeGraphNode>>): Array<Seq<RecipeGraphNode>> {
    val swap = Seq(layers)
    val aligned: Seq<RecipeGraphNode> = Seq()
    for (lay in 0..<swap.size) {
      aligned.clear()
      for (node in swap[lay]) {
        for (child in node.children()) {
          if (child.contextDepth == lay) {
            aligned.add(child)
            if (lay + 1 >= swap.size) {
              swap.add(Seq<RecipeGraphNode>())
            }
            swap[lay + 1].addUnique(child)
            child.contextDepth = lay + 1
          }
        }
      }
      swap[lay].removeAll(aligned)
    }

    return swap.toArray()
  }

  private fun insertLineMark(layers: Array<Seq<RecipeGraphNode>>, lineMarkCallBack: Cons<RecipeGraphNode>): Array<Seq<RecipeGraphNode>> {
    val swap = layers.map { it.copy() }

    for (node in recipeNodes) {
      if (node.isLineMark) continue
      for (pair in node.childrenWithItem()) {
        val item = pair.first
        val child = pair.second

        if (child.contextDepth - node.contextDepth > 1) {
          node.disInput(item)

          var curr = node
          for (dep in 1..<child.contextDepth - node.contextDepth) {
            val fc = curr

            val lay = swap[node.contextDepth + dep]
            var ins = lay.find { n -> n.isLineMark && n.parents().first() == fc }
            if (ins == null) {
              ins = node.genLineMark()
              recipeNodes.add(ins)
              lineMarkCallBack.get(ins)
              curr.setInput(item, ins)
              ins.contextDepth = node.contextDepth + dep
              lay.add(ins)
            }
            curr = ins
          }
          curr.setInput(item, child)
        }
      }
    }

    return swap.toTypedArray()
  }
}