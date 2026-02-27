package tmi.recipe

import arc.func.Cons2
import arc.struct.ObjectFloatMap
import arc.util.io.Reads
import arc.util.io.Writes
import tmi.TooManyItems
import tmi.recipe.types.HeatMark
import tmi.recipe.types.PowerMark
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType

class InputTable {
  private val inputs = ObjectFloatMap<RecipeItem<*>>()

  fun get(b: RecipeItem<*>?): Float {
    return inputs[b, 0f]
  }

  fun add(item: RecipeItemStack<*>): InputTable {
    return add(item.item, item.amount)
  }

  fun add(item: RecipeItem<*>?, amount: Float): InputTable {
    inputs.increment(item, 0f, amount)

    return this
  }

  fun setFull(stack: RecipeItemStack<*>) {
    inputs.put(stack.item, 100000f)
  }

  @JvmOverloads
  fun set(other: InputTable, over: Boolean = false): InputTable {
    if (over) clear()
    other.inputs.each { e: ObjectFloatMap.Entry<RecipeItem<*>?> -> add(e.key, e.value) }
    return this
  }

  @JvmOverloads
  fun setBy(other: InputTable, over: Boolean = false): InputTable {
    if (over) clear()
    other.inputs.each { e: ObjectFloatMap.Entry<RecipeItem<*>?> -> add(e.key, e.value) }
    return this
  }

  fun reset(item: RecipeItem<*>?): InputTable {
    inputs.remove(item, 0f)
    return this
  }

  fun clear(): InputTable {
    inputs.clear()
    return this
  }

  fun applyFullRecipe(recipe: Recipe, fillOptional: Boolean, applyAttribute: Boolean, multiplier: Float): InputTable {
    for (stack in recipe.materials) {
      if (!fillOptional && stack.isOptional) continue
      if (!applyAttribute && stack.itemType == RecipeItemType.ATTRIBUTE) continue

      inputs.put(stack.item, stack.amount*multiplier)
    }

    return this
  }

  fun addPower(power: Float): InputTable {
    add(PowerMark, power + inputs[PowerMark, 0f])
    return this
  }

  fun addHeat(heat: Float): InputTable {
    add(HeatMark, heat + inputs[HeatMark, 0f])
    return this
  }

  fun any(): Boolean {
    return !inputs.isEmpty
  }

  fun each(cons: Cons2<RecipeItem<*>, Float>) {
    inputs.each { e: ObjectFloatMap.Entry<RecipeItem<*>> -> cons[e.key, e.value] }
  }

  fun copy(): InputTable{
    val copy = InputTable()
    copy.inputs.putAll(inputs)
    return copy
  }

  fun write(write: Writes) {
    write.i(inputs.size)

    inputs.each {
      write.str(it.key.name)
      write.f(it.value)
    }
  }

  fun read(read: Reads){
    val ins = read.i()
    val attrs = read.i()

    for (i in 0 until ins) {
      inputs.put(TooManyItems.itemsManager.getByName<Any>(read.str()), read.f())
    }
  }
}
