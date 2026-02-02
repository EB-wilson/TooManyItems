package tmi.ui.calculator

import arc.func.Cons2
import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import tmi.recipe.EnvParameter
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.util.set
import kotlin.math.max

class RecipeGraphNode(
  val recipe: Recipe
) {
  private val outputs = ObjectMap<RecipeItem<*>, Seq<RecipeGraphNode>>()
  private val inputs = ObjectMap<RecipeItem<*>, RecipeGraphNode>()

  internal var graphIndex = 0
  internal var graph: RecipeGraph? = null
  internal var contextDepth = 0

  /**Only usable on root nodes*/
  var targetAmount = 1

  var balanceAmount = -1f

  var multiplier = 1f
  var efficiency = 1f
  val attributes = ObjectSet<RecipeItem<*>>()
  val optionals = ObjectSet<RecipeItem<*>>()

  val envParameter = EnvParameter()

  fun updateEfficiency(){
    multiplier = recipe.calculateMultiple(envParameter)
    efficiency = recipe.calculateEfficiency(envParameter, multiplier)
  }

  fun updateBalance(){
    if (contextDepth > 0) {
      var amount = 0f
      parentsWithItem().forEach { (item, parents) ->
        val out = recipe.getProduction(item)!!

        var requireAmount = 0f
        parents.forEach { parent ->
          parent.recipe.getMaterial(item)?.also { stack ->
            val mul = if (stack.itemType == RecipeItemType.BOOSTER || stack.itemType == RecipeItemType.NORMAL) parent.multiplier else 1f
            requireAmount += stack.amount*parent.balanceAmount*mul
          }
        }
        val balance = requireAmount/(out.amount*efficiency)

        amount = max(amount, balance)
      }

      balanceAmount = amount
    }
  }

  fun children() = inputs.values().toList()
  fun childrenWithItem() = inputs.map { it.key to it.value }

  fun parents() = outputs.values().flatMap { it }
  fun parentsWithItem() = outputs.map { it.key to it.value.copy() }.filter { it.second.any() }

  fun isHovering() = inputs.isEmpty && outputs.sumOf { it.value.size } <= 0

  fun remove() {
    parentsWithItem().forEach { (i, nodes) -> nodes.forEach { it.disInput(i, false) } }
    childrenWithItem().forEach { (i, _) -> disInput(i) }

    graph?.removeNode(this)
  }

  fun setInput(item: RecipeItem<*>, child: RecipeGraphNode){
    if (!recipe.containsMaterial(item)) throw IllegalArgumentException("This recipe does not consume item $item.")
    if (!child.recipe.containsProduction(item)) throw IllegalArgumentException("Used recipe does not product item $item.")

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
      if (child.isHovering()) graph?.removeNode(child)
    }
  }

  fun hasInput(item: RecipeItem<*>) = inputs.containsKey(item)
  fun hasOutput(item: RecipeItem<*>) = outputs.get(item)?.any()?:false

  fun getInput(item: RecipeItem<*>): RecipeGraphNode? = inputs.get(item)
  fun getOutputs(item: RecipeItem<*>): List<RecipeGraphNode>? = outputs.get(item)?.toList()

  fun visit(
    currDepth: Int = 0,
    block: Cons2<Int, RecipeGraphNode>
  ){
    block.get(currDepth, this)
    inputs.values().forEach {
      it.visit(currDepth + 1, block)
    }
  }

}