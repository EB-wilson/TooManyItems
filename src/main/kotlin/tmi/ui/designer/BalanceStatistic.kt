package tmi.ui.designer

import arc.math.Mathf
import arc.struct.ObjectIntMap
import arc.struct.OrderedMap
import tmi.forEach
import tmi.recipe.AmountFormatter
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.set

class BalanceStatistic(private val ownerView: DesignerView) {
  private val allRecipes = ObjectIntMap<Recipe>()

  private val inputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val outputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val redundant = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val missing = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()

  private val globalOutputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val globalInputs = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()

  fun reset(){
    allRecipes.clear()
    inputs.clear()
    outputs.clear()
    redundant.clear()
    missing.clear()

    globalInputs.values().forEach { it.amount = 0f }
    globalOutputs.values().forEach { it.amount = 0f }
  }

  fun setGlobal(input: Iterable<RecipeItem<*>>, output: Iterable<RecipeItem<*>>) {
    globalInputs.clear()
    globalOutputs.clear()
    input.forEach { globalInputs[it] = RecipeItemStack(it, 0f).setFormat(AmountFormatter.persecFormatter()) }
    output.forEach { globalOutputs[it] = RecipeItemStack(it, 0f).setFormat(AmountFormatter.persecFormatter()) }
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

    val allCards = ownerView.cards + ownerView.foldCards
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

    val recipeCards = allCards.filterIsInstance<RecipeCard>()
    val inputCards = allCards.filterIsInstance<IOCard>().filter { it.isInput }
    val outputCards = allCards.filterIsInstance<IOCard>().filter { !it.isInput }

    recipeCards.forEach { allRecipes.put(it.recipe, it.mul) }
    inputCards.forEach {
      it.outputs()/* card input means linker output */.forEach { s ->
        val stack = inputs.get(s.item) {
          RecipeItemStack(s.item, 0f).setFormat(AmountFormatter.persecFormatter())
        }

        stack.amount += s.amount
      }
    }
    outputCards.forEach {
      it.inputs()/* card output means linker input */.forEach { s ->
        val stack = outputs.get(s.item) {
          RecipeItemStack(s.item, 0f).setFormat(AmountFormatter.persecFormatter())
        }

        stack.amount += s.amount
      }
    }

    cardIOs.forEach { io ->
      io.eachOut { s, rs ->
        val red = globalOutputs[s.item]?:redundant.get(s.item){
          RecipeItemStack(s.item, 0f).setFormat(AmountFormatter.persecFormatter())
        }

        red.amount += Mathf.maxZero(s.amount - rs.amount)
      }

      io.eachIn { s, rs ->
        val mis = globalInputs[s.item]?:missing.get(s.item) {
          RecipeItemStack(s.item, 0f).setFormat(AmountFormatter.persecFormatter())
        }

        mis.amount += Mathf.maxZero(s.amount - rs.amount)
      }
    }
  }

  fun resultInputs(): List<RecipeItemStack<*>> = inputs.values().filter { it.amount > 0 }
  fun resultOutputs(): List<RecipeItemStack<*>> = outputs.values().filter { it.amount > 0 }
  fun resultMissing(): List<RecipeItemStack<*>> = missing.values().filter { it.amount > 0 }
  fun resultRedundant(): List<RecipeItemStack<*>> = redundant.values().filter { it.amount > 0 }
  fun resultGlobalInputs(): List<RecipeItemStack<*>> = globalInputs.values().filter { it.amount > 0 }
  fun resultGlobalOutputs(): List<RecipeItemStack<*>> = globalOutputs.values().filter { it.amount > 0 }

  override fun toString(): String {
    val res = StringBuilder()

    inputs.values().filter { it.amount > 0 }.also {
      if (it.isEmpty()) return@also
      res.append("inputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }
    outputs.values().filter { it.amount > 0 }.also {
      if (it.isEmpty()) return@also
      res.append("outputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }

    globalInputs.values().filter { it.amount > 0 }.also {
      if (it.isEmpty()) return@also
      res.append("global inputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }
    globalOutputs.values().filter { it.amount > 0 }.also {
      if (it.isEmpty()) return@also
      res.append("global outputs:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }

    redundant.values().filter { it.amount > 0 }.also {
      if (it.isEmpty()) return@also
      res.append("redundant:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }
    missing.values().filter { it.amount > 0 }.also {
      if (it.isEmpty()) return@also
      res.append("missing:\n")
      it.forEach{ e -> res.append("| ${e.item.name}: ${e.amount}\n") }
    }

    return res.toString()
  }
}