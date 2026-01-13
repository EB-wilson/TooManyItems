package tmi.ui.calculator

import arc.func.Cons2
import arc.math.geom.Vec2
import arc.struct.ObjectMap
import arc.struct.Seq
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItem
import tmi.set
import tmi.ui.RecipeNode
import tmi.ui.RecipeView

class RecipeGraphNode(
  val recipe: Recipe
) {
  private val outputs = ObjectMap<RecipeItem<*>, Seq<RecipeGraphNode>>()
  private val inputs = ObjectMap<RecipeItem<*>, RecipeGraphNode>()

  internal var graph: RecipeGraph? = null

  fun children() = inputs.values().toList()
  fun childrenWithItem() = inputs.map { it.key to it.value }

  fun parents() = outputs.values().flatMap { it }
  fun parentsWithItem() = outputs.map { it.key to it.value }.filter { it.second.any() }

  fun isHovering() = inputs.isEmpty && outputs.sumOf { it.value.size } <= 0

  fun remove() {
    parentsWithItem().forEach { (i, nodes) -> nodes.forEach{ it.disInput(i, false) } }
    childrenWithItem().forEach { (i, _) -> disInput(i, false) }

    graph?.removeNode(this)
  }

  fun setInput(item: RecipeItem<*>, child: RecipeGraphNode){
    val prod = child.recipe.productions.keys()
    val cons = recipe.materials.keys()

    if (!cons.contains(item)) throw IllegalArgumentException("This recipe does not consume item $item.")
    if (!prod.contains(item)) throw IllegalArgumentException("Used recipe does not product item $item.")

    inputs[item] = child
    child.outputs.get(item){ Seq() }.add(this)
  }

  fun setOutput(item: RecipeItem<*>, parent: RecipeGraphNode){
    parent.setInput(item, this)
  }

  fun disInput(item: RecipeItem<*>, removeHovering: Boolean = true) {
    val child = inputs.get(item)?: return
    inputs.remove(item)

    val parents = child.outputs.get(item)?: return
    parents.remove(this)

    if (parents.isEmpty) child.outputs.remove(item)

    if (removeHovering) {
      if (isHovering()) graph?.removeNode(this)
      if (child.isHovering()) graph?.removeNode(child)
    }
  }

  fun hasInput(item: RecipeItem<*>) = inputs.containsKey(item)

  fun visitTree(
    currDepth: Int = 0,
    visitedSet: MutableSet<RecipeGraphNode> = mutableSetOf(),
    block: Cons2<Int, RecipeGraphNode>
  ): Set<RecipeGraphNode> {
    visit(currDepth, visitedSet, block)

    return visitedSet
  }

  private fun visit(
    currDepth: Int,
    visited: MutableSet<RecipeGraphNode>,
    block: Cons2<Int, RecipeGraphNode>
  ){
    if (visited.add(this)) {
      block.get(currDepth, this)
      inputs.values().forEach {
        it.visit(currDepth + 1, visited, block)
      }
    }
  }

}