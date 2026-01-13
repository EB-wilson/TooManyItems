package tmi.ui.calculator

import arc.func.Cons
import arc.func.Cons2
import arc.math.geom.Vec2
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.io.Reads
import arc.util.io.Writes
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItem
import tmi.ui.RecipeNode
import tmi.ui.RecipeView
import tmi.ui.calculator.RecipeGraphElement.*
import kotlin.Array
import kotlin.FloatArray

class RecipeGraph{
  private val recipeNodes = Seq<RecipeGraphNode>()

  fun addNode(node: RecipeGraphNode){
    recipeNodes.add(node)
    node.graph = this
  }

  fun removeNode(node: RecipeGraphNode) {
    if (node.graph == this) {
      node.graph = null
      recipeNodes.remove(node)
    }
  }

  fun clear(){
    recipeNodes.forEach { it.graph = null }
    recipeNodes.clear()
  }

  fun eachNode(callBack: Cons<RecipeGraphNode>){
    recipeNodes.forEach { callBack.get(it) }
  }

  fun eachNode(addedSet: MutableSet<RecipeGraphNode> = mutableSetOf(), callBack: Cons2<Int, RecipeGraphNode>){
    for (root in recipeNodes.select({ e -> e.parents().isEmpty() })) {
      root.visitTree(0, addedSet, callBack)
    }
  }

  fun write(writer: Writes){

  }

  fun read(reader: Reads){

  }
}