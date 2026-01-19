package tmi.recipe

import arc.struct.Seq

class RecipeItemGroup {
  private val items = Seq<RecipeItemStack<*>>()

  fun items() = items.toList()

  fun addItem(stack: RecipeItemStack<*>) {
    items.add(stack)
  }

  fun unsetItem(stack: RecipeItemStack<*>) {
    items.remove(stack)
  }
}