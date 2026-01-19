package tmi.ui.calculator

import arc.func.Cons
import arc.func.Cons2
import arc.struct.Seq
import arc.util.io.Reads
import arc.util.io.Writes
import kotlin.math.max

class RecipeGraph{
  private var lastIndex = 0
  private val recipeNodes = Seq<RecipeGraphNode>()

  fun addNode(node: RecipeGraphNode){
    recipeNodes.add(node)
    node.graph = this
    node.graphIndex = lastIndex++
  }

  fun removeNode(node: RecipeGraphNode) {
    if (node.graph == this) {
      node.graph = null
      node.graphIndex = -1
      recipeNodes.remove(node)
    }
  }

  fun clear(){
    lastIndex = 0
    recipeNodes.forEach { it.graph = null }
    recipeNodes.clear()
  }

  fun isEmpty() = recipeNodes.isEmpty

  fun eachNode(callBack: Cons<RecipeGraphNode>){
    recipeNodes.forEach { callBack.get(it) }
  }

  fun eachNode(callBack: Cons2<Int, RecipeGraphNode>){
    val set = linkedSetOf<RecipeGraphNode>()

    recipeNodes.forEach { it.contextDepth = 0 }

    for (root in recipeNodes.select { e -> e.parents().isEmpty() }) {
      root.visit(0){ depth, node ->
        set.add(node)
        node.contextDepth = max(node.contextDepth, depth)
      }
    }

    set.forEach { callBack.get(it.contextDepth, it) }
  }

  fun write(writer: Writes){
    writer.i(recipeNodes.size)

    recipeNodes.forEach { node ->
      writer.i(node.recipe.hashCode())
      writer.i(node.balanceAmount)
    }
  }

  fun read(reader: Reads){
    clear()
  }
}