package tmi.ui.calculator

import arc.func.Cons2
import arc.math.Mathf
import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import tmi.recipe.InputTable
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.util.set
import kotlin.math.ceil
import kotlin.math.max

class RecipeGraphNode(
  val recipe: Recipe
) {
  private val outputs = ObjectMap<RecipeItem<*>, Seq<RecipeGraphNode>>()
  private val inputs = ObjectMap<RecipeItem<*>, RecipeGraphNode>()

  internal var graphIndex = Mathf.random(2147483645)
  internal var graph: RecipeGraph? = null

  /**Only usable on root nodes*/
  var targetAmount = 1

  var balanceAmount = -1f

  var multiplier = 1f
  var efficiency = 1f
  val attributes = ObjectSet<RecipeItem<*>>()
  val optionals = ObjectSet<RecipeItem<*>>()

  val inputTable = InputTable()

  fun updateEfficiency(){
    multiplier = recipe.calculateMultiple(inputTable)
    efficiency = recipe.calculateEfficiency(inputTable, multiplier)
  }

  fun updateBalance(isRoot: Boolean): Boolean{
    if (!isRoot) {
      var amount = 0f
      childrenWithItem().forEach { (item, children) ->
        val out = recipe.getProduction(item)!!

        var requireAmount = 0f
        children.forEach { child ->
          child.recipe.getMaterial(item)?.also { stack ->
            val mul = if (stack.itemType == RecipeItemType.BOOSTER || stack.itemType == RecipeItemType.NORMAL)
              child.multiplier else 1f

            requireAmount +=
              if (stack.itemType == RecipeItemType.BOOSTER) stack.amount*ceil(child.balanceAmount)*mul
              else stack.amount*child.balanceAmount*mul
          }
        }
        val balance = requireAmount/(out.amount*if(out.itemType == RecipeItemType.ISOLATED) 1f else efficiency)

        amount = max(amount, if (balance.isNaN()) 0f else balance)
      }

      if (balanceAmount != amount) {
        balanceAmount = amount
        return true
      }
    }

    return false
  }

  fun parents() = inputs.values().toList()
  fun parentsWithItem() = inputs.map { it.key to it.value }.sortedBy { it.first }

  fun children() = outputs.values().flatMap { it }
  fun childrenWithItem() = outputs.map { it.key to it.value.copy() }.filter { it.second.any() }.sortedBy { it.first }

  fun isHovering() = inputs.isEmpty && outputs.sumOf { it.value.size } <= 0

  fun remove() {
    childrenWithItem().forEach { (i, nodes) -> nodes.forEach { it.disInput(i, false) } }
    parentsWithItem().forEach { (i, _) -> disInput(i, false) }

    graph?.removeNode(this)
  }

  fun setInput(item: RecipeItem<*>, parent: RecipeGraphNode){
    if (!recipe.containsMaterial(item)) throw IllegalArgumentException("This recipe does not consume item $item.")
    if (!parent.recipe.containsProduction(item)) throw IllegalArgumentException("Used recipe does not product item $item.")

    inputs[item] = parent
    parent.outputs.get(item){ Seq() }.add(this)
  }

  fun setOutput(item: RecipeItem<*>, child: RecipeGraphNode){
    child.setInput(item, this)
  }

  fun disInput(item: RecipeItem<*>, removeHovering: Boolean = true) {
    val parent = inputs.get(item)?: return
    inputs.remove(item)

    val consumers = parent.outputs.get(item)?: return
    consumers.remove(this)

    if (consumers.isEmpty) parent.outputs.remove(item)

    if (removeHovering) {
      if (parent.isHovering()) parent.remove()
    }
  }

  fun hasInput(item: RecipeItem<*>) = inputs.containsKey(item)
  fun hasOutput(item: RecipeItem<*>) = outputs.get(item)?.any()?:false

  fun getInput(item: RecipeItem<*>): RecipeGraphNode? = inputs.get(item)
  fun getOutputs(item: RecipeItem<*>): List<RecipeGraphNode>? = outputs.get(item)?.toList()

  @JvmOverloads
  fun visit(
    currDepth: Int = 0,
    visitedSet: MutableSet<RecipeGraphNode> = mutableSetOf(),
    block: Cons2<Int, RecipeGraphNode>,
  ){
    if (visitedSet.add(this)) {
      block.get(currDepth, this)
      visitFlow(currDepth, visitedSet, block)
    }
  }

  private fun visitFlow(
    currDepth: Int,
    visitedSet: MutableSet<RecipeGraphNode>,
    block: Cons2<Int, RecipeGraphNode>,
  ) {
    val queue = mutableListOf<RecipeGraphNode>()
    inputs.values().forEach {
      if (visitedSet.add(it)) {
        block.get(currDepth + 1, it)
        queue.add(it)
      }
    }
    queue.forEach { it.visitFlow(currDepth + 1, visitedSet, block) }
    queue.clear()
    outputs.values().forEach { seq ->
      seq.forEach { p ->
        if (visitedSet.add(p)) {
          block.get(currDepth - 1, p)
          queue.add(p)
        }
      }
    }
    queue.forEach { it.visitFlow(currDepth - 1, visitedSet, block) }
  }
}
