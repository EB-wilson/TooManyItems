package tmi.ui.calculator

import arc.struct.*
import mindustry.content.Items
import tmi.TooManyItems
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import kotlin.math.ceil

class RecipeStatistic(
  private val targetGraph: RecipeGraph
) {
  private val allRecipes = ObjectIntMap<Recipe>()

  private val inputGroups = Seq<Seq<RecipeItemStack<*>>>()
  private val inputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val optionalInputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val outputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val redundant = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()

  private val buildMaterials = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()

  fun reset(){
    allRecipes.clear()
    inputGroups.clear()
    inputs.clear()
    optionalInputs.clear()
    outputs.clear()
    redundant.clear()
    buildMaterials.clear()
  }

  fun updateStatistic() {
    val inputs = mutableMapOf<RecipeGraphNode, MutableSet<RecipeItem<*>>>()
    
    targetGraph.eachNode { node ->
      allRecipes.increment(node.recipe, 1)

      node.recipe.productions.forEach { stack ->
        val item = stack.item
        val out = node.recipe.getProduction(item)!!
        val nodeOut = out.amount*node.balanceAmount*if(out.itemType == RecipeItemType.ISOLATED) 1f else node.efficiency
        val parents = node.getOutputs(item)
        var requireAmount = 0f

        if (!parents.isNullOrEmpty()) {
          parents.forEach { parent ->
            parent.recipe.getMaterial(item)?.also { stack ->
              val mul =
                if (stack.itemType == RecipeItemType.BOOSTER || stack.itemType == RecipeItemType.NORMAL) parent.multiplier
                else 1f

              requireAmount +=
                if (stack.itemType == RecipeItemType.BOOSTER) stack.amount*ceil(parent.balanceAmount)*mul
                else stack.amount*parent.balanceAmount*mul

              inputs.computeIfAbsent(parent){ mutableSetOf() }.add(item)
            }
          }

          redundant.get(item) { RecipeItemStack(item) }.amount += nodeOut - requireAmount
        }
        else {
          outputs.get(item) { RecipeItemStack(item) }.amount += nodeOut
        }
      }
    }

    targetGraph.eachNode { node ->
      val inputSet = inputs[node]?:emptySet()
      node.recipe.materialGroups.filter { it.first().itemType != RecipeItemType.ATTRIBUTE }.forEach { group ->
        if (group.any { inputSet.contains(it.item) }) return@forEach

        val stack = group.first()
        val mul =
          if (stack.itemType == RecipeItemType.BOOSTER || stack.itemType == RecipeItemType.NORMAL) node.multiplier
          else 1f

        if (group.size == 1) {
          val item = group.first()
          if (item.isOptional) {
            if (node.optionals.contains(item.item)) {
              optionalInputs.get(item.item) {
                RecipeItemStack(item.item).also { it.setOptional(true) }
              }.amount += item.amount*mul*node.balanceAmount
            }
          }
          else {
            this.inputs.get(item.item) {
              RecipeItemStack(item.item())
            }.amount += item.amount*mul*node.balanceAmount
          }
        }
        else {
          inputGroups.add(Seq.with(group))
        }
      }
    }

    allBlocks().forEach { block ->
      val buildRecipe = TooManyItems.recipesManager.getRecipesByBuilding(block.item)
      buildRecipe.forEach { r ->
        r.materials.forEach { item ->
          buildMaterials.get(item.item){ RecipeItemStack(item.item) }.amount += item.amount
        }
      }
    }
  }

  fun resultInputs() = inputs.values().filter { it.amount > 0 }.map { listOf(it) } + inputGroups.filter { !it.first().isOptional }.map { it.toList() }
  fun resultOptionalInputs() = optionalInputs.values().filter { it.amount > 0 }.map { listOf(it) } + inputGroups.filter { it.first().isOptional }.map { it.toList() }
  fun resultOutputs() = outputs.values().filter { it.amount > 0 }
  fun resultRedundant() = redundant.values().filter { it.amount > 0 }.sortedBy { -it.amount }

  fun resultBuildMaterials() = buildMaterials.values().filter { it.amount > 0 }

  @Suppress("UNCHECKED_CAST")
  fun allBlocks(): List<RecipeItemStack<*>> = allRecipes.map {
    RecipeItemStack(it.key.ownerBlock as RecipeItem<Any>, it.value.toFloat()).integerFormat()
  }

  override fun toString(): String {
    val res = StringBuilder()

    resultInputs().also { s ->
      res.append("inputs:\n")
      s.forEach {
        if (it.isEmpty()) return@also
        res.append("| [\n")
        it.forEach{ e -> res.append("|  ${e.item.name}: ${e.amount}\n") }
        res.append("| ]\n")
      }
    }
    resultOutputs().also {
      if (it.isEmpty()) return@also
      res.append("outputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }

    resultRedundant().also {
      if (it.isEmpty()) return@also
      res.append("redundant:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }

    return res.toString()
  }
}