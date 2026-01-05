package tmi.ui.calculator

import arc.struct.ObjectMap
import arc.struct.Seq
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItem
import tmi.set

class RecipeGraphNode private constructor(
  val recipe: Recipe,
  val isLineMark: Boolean,
) {
  constructor(recipe: Recipe): this(recipe, false)

  private val outputs = ObjectMap<RecipeItem<*>, Seq<RecipeGraphNode>>()
  private val inputs = ObjectMap<RecipeItem<*>, RecipeGraphNode>()

  var contextDepth = 0
  var layerIndex = 0

  fun genLineMark() = RecipeGraphNode(recipe, true)

  fun children() = inputs.values().toList()
  fun childrenWithItem() = inputs.map { it.key to it.value }

  fun parents() = outputs.values().flatMap { it }
  fun parentsWithItem() = outputs.map { it.key to it.value }.filter { it.second.any() }

  fun setInput(item: RecipeItem<*>, child: RecipeGraphNode){
    if (!isLineMark && !child.isLineMark) {
      val prod = child.recipe.productions.keys()
      val cons = recipe.materials.keys()

      if (!cons.contains(item)) throw IllegalArgumentException("This recipe does not consume item $item.")
      if (!prod.contains(item)) throw IllegalArgumentException("Used recipe does not product item $item.")
    }

    inputs[item] = child
    child.outputs.get(item){ Seq() }.add(this)
  }

  fun setOutput(item: RecipeItem<*>, parent: RecipeGraphNode){
    parent.setInput(item, this)
  }

  fun disInput(item: RecipeItem<*>) {
    val child = inputs[item]?: return
    inputs.remove(item)
    val parents = child.outputs.get(item) ?: return
    parents.remove(this)

    if (parents.isEmpty) child.outputs.remove(item)
  }

  fun disOutput(item: RecipeItem<*>, parent: RecipeGraphNode) {
    val parents = outputs[item] ?: return
    parents.remove(this)
    parent.inputs.remove(item)

    if (parents.isEmpty) outputs.remove(item)
  }

  fun visitTree(
    currDepth: Int = 0,
    visitedSet: MutableSet<RecipeGraphNode> = mutableSetOf(),
    block: (Int, RecipeGraphNode) -> Unit
  ): Set<RecipeGraphNode> {
    visitedSet.clear()
    visit(currDepth, visitedSet, block)

    return visitedSet
  }

  private fun visit(
    currDepth: Int,
    visited: MutableSet<RecipeGraphNode>,
    block: (Int, RecipeGraphNode) -> Unit
  ){
    if (visited.add(this)) {
      block(currDepth, this)
      inputs.values().forEach {
        it.visit(currDepth + 1, visited, block)
      }
    }
  }
}