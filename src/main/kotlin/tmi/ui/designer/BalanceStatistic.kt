package tmi.ui.designer

import arc.math.Mathf
import arc.struct.ObjectIntMap
import arc.struct.ObjectSet
import arc.struct.OrderedMap
import tmi.TooManyItems
import tmi.forEach
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.set

class BalanceStatistic(private val ownerView: DesignerView) {
  companion object{
    private const val ZERO = 0.0001f
  }

  private val allRecipes = ObjectIntMap<Recipe>()

  private val inputTypes = ObjectSet<RecipeItem<*>>()
  private val outputTypes = ObjectSet<RecipeItem<*>>()

  private val inputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val outputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val redundant = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val missing = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()

  private val globalOutputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val globalInputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()

  private val buildMaterials = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()

  fun reset(){
    allRecipes.clear()
    inputs.clear()
    outputs.clear()
    redundant.clear()
    missing.clear()

    inputTypes.clear()
    outputTypes.clear()

    globalInputs.values().forEach { it.amount = 0f }
    globalOutputs.values().forEach { it.amount = 0f }
  }

  fun setGlobal(input: Iterable<RecipeItem<*>>, output: Iterable<RecipeItem<*>>) {
    globalInputs.clear()
    globalOutputs.clear()
    input.forEach { globalInputs[it] = RecipeItemStack(it, 0f).unitTimedFormat() }
    output.forEach { globalOutputs[it] = RecipeItemStack(it, 0f).unitTimedFormat() }
  }

  fun updateStatistic() {
    data class CardIO(
      val card: Card,
      val inputs: List<RecipeItemStack<*>>,
      val outputs: List<RecipeItemStack<*>>,
      val realIn: List<RecipeItemStack<*>>,
      val realOut: List<RecipeItemStack<*>>,
    ){
      inline fun eachIn(block: (RecipeItemStack<*>, RecipeItemStack<*>) -> Unit) {
        for (i in inputs.indices) {
          block(inputs[i], realIn[i])
        }
      }

      inline fun eachOut(block: (RecipeItemStack<*>, RecipeItemStack<*>) -> Unit) {
        for (i in outputs.indices) {
          block(outputs[i], realOut[i])
        }
      }
    }

    val allCards = (ownerView.cards + ownerView.foldCards).filter { it.balanceValid }
    val cardIOs = allCards.map { card ->
      val input = card.inputs()
      val output = card.outputs()

      val realIn = input.map l@{ s ->
        val linker = card.linkerIns.find { it.item == s.item }
        if (linker == null || !linker.isNormalized) return@l RecipeItemStack(s.item, 0f)

        var expect = 0f
        linker.links.forEach { l, e ->
          expect += (if (linker.links.size == 1) 1f else e.rate)*l.expectAmount
        }

        RecipeItemStack(linker.item, expect)
      }
      val realOut = output.map l@{ s ->
        val linker = card.linkerOuts.find { it.item == s.item }
        if (linker == null) return@l RecipeItemStack(s.item, 0f)

        var expect = 0f
        linker.links.forEach { l, _ ->
          if (!l.isNormalized) return@forEach
          val rate = if (l.links.size == 1) 1f else l.links[linker].rate
          expect += rate*l.expectAmount
        }

        RecipeItemStack(linker.item, expect)
      }
      CardIO(card, input, output, realIn, realOut)
    }

    val recipeCards = allCards.filterIsInstance<RecipeCard>().filter { it.balanceValid }
    val inputCards = allCards.filterIsInstance<IOCard>().filter { it.isInput }
    val outputCards = allCards.filterIsInstance<IOCard>().filter { !it.isInput }

    recipeCards.forEach { c ->
      allRecipes.put(c.recipe, c.mul)

      TooManyItems.recipesManager
        .getRecipesByBuilding(c.recipe.ownerBlock as RecipeItem<*>)
        .firstOpt()?.let { rec ->
          rec.materials.values().forEach {
            val stack = buildMaterials.get(it.item) {
              RecipeItemStack(it.item, 0f).integerFormat()
            }

            stack.amount += it.amount * rec.craftTime
          }
        }
    }

    inputCards.forEach {
      it.outputs()/* card input means linker output */.forEach { s ->
        inputTypes.add(s.item)
        val stack = inputs.get(s.item) {
          RecipeItemStack(s.item, 0f).unitTimedFormat()
        }

        stack.amount += s.amount
      }
    }
    outputCards.forEach {
      it.inputs()/* card output means linker input */.forEach { s ->
        outputTypes.add(s.item)
        val stack = outputs.get(s.item) {
          RecipeItemStack(s.item, 0f).unitTimedFormat()
        }

        stack.amount += s.amount
      }
    }

    cardIOs.forEach { io ->
      io.eachOut { s, rs ->
        outputTypes.add(s.item)
        val red = globalOutputs[s.item]?:redundant.get(s.item){
          RecipeItemStack(s.item, 0f).unitTimedFormat()
        }

        red.amount += Mathf.maxZero(s.amount - rs.amount)
      }

      io.eachIn { s, rs ->
        inputTypes.add(s.item)
        val mis = globalInputs[s.item]?:missing.get(s.item) {
          RecipeItemStack(s.item, 0f).unitTimedFormat()
        }

        mis.amount += Mathf.maxZero(s.amount - rs.amount)
      }
    }
  }

  fun inputTypes(): Set<RecipeItem<*>> = inputTypes.toSet()
  fun outputTypes(): Set<RecipeItem<*>> = outputTypes.toSet()

  fun resultInputs(): List<RecipeItemStack<*>> = inputs.values().filter { it.amount > ZERO }
  fun resultOutputs(): List<RecipeItemStack<*>> = outputs.values().filter { it.amount > ZERO }
  fun resultMissing(): List<RecipeItemStack<*>> = missing.values().filter { it.amount > ZERO }
  fun resultRedundant(): List<RecipeItemStack<*>> = redundant.values().filter { it.amount > ZERO }
  fun resultGlobalInputs(): List<RecipeItemStack<*>> = globalInputs.values().filter { it.amount > ZERO }
  fun resultGlobalOutputs(): List<RecipeItemStack<*>> = globalOutputs.values().filter { it.amount > ZERO }
  fun resultBuildMaterials(): List<RecipeItemStack<*>> = buildMaterials.values().filter { it.amount > ZERO }

  @Suppress("UNCHECKED_CAST")
  fun allBlocks(): List<RecipeItemStack<*>> = allRecipes.map {
    RecipeItemStack(it.key.ownerBlock as RecipeItem<Any>, it.value.toFloat()).integerFormat()
  }

  override fun toString(): String {
    val res = StringBuilder()

    inputs.values().filter { it.amount > ZERO }.also {
      if (it.isEmpty()) return@also
      res.append("inputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }
    outputs.values().filter { it.amount > ZERO }.also {
      if (it.isEmpty()) return@also
      res.append("outputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }

    globalInputs.values().filter { it.amount > ZERO }.also {
      if (it.isEmpty()) return@also
      res.append("global inputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }
    globalOutputs.values().filter { it.amount > ZERO }.also {
      if (it.isEmpty()) return@also
      res.append("global outputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }

    redundant.values().filter { it.amount > ZERO }.also {
      if (it.isEmpty()) return@also
      res.append("redundant:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }
    missing.values().filter { it.amount > ZERO }.also {
      if (it.isEmpty()) return@also
      res.append("missing:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }

    return res.toString()
  }
}