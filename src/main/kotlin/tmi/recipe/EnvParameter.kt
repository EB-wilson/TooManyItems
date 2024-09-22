package tmi.recipe

import arc.func.Cons2
import arc.struct.ObjectFloatMap
import tmi.recipe.types.HeatMark
import tmi.recipe.types.PowerMark
import tmi.recipe.types.RecipeItem

class EnvParameter {
  private val inputs = ObjectFloatMap<RecipeItem<*>>()
  private val attributes = ObjectFloatMap<RecipeItem<*>>()

  fun getInputs(b: RecipeItem<*>?): Float {
    return inputs[b, 0f]
  }

  fun getAttribute(b: RecipeItem<*>?): Float {
    return attributes[b, 0f]
  }

  fun add(item: RecipeItemStack): EnvParameter {
    return add(item.item, item.amount, item.isAttribute)
  }

  fun add(item: RecipeItem<*>?, amount: Float, isAttribute: Boolean): EnvParameter {
    if (isAttribute) {
      attributes.increment(item, 0f, amount)
    }
    else inputs.increment(item, 0f, amount)

    return this
  }

  @JvmOverloads
  fun set(other: EnvParameter, over: Boolean = false): EnvParameter {
    if (over) clear()
    other.attributes.each { e: ObjectFloatMap.Entry<RecipeItem<*>?> -> add(e.key, e.value, true) }
    other.inputs.each { e: ObjectFloatMap.Entry<RecipeItem<*>?> -> add(e.key, e.value, false) }
    return this
  }

  @JvmOverloads
  fun setInputs(other: EnvParameter, over: Boolean = false): EnvParameter {
    if (over) clearInputs()
    other.inputs.each { e: ObjectFloatMap.Entry<RecipeItem<*>?> -> add(e.key, e.value, false) }
    return this
  }

  @JvmOverloads
  fun setAttributes(other: EnvParameter, over: Boolean = false): EnvParameter {
    if (over) clearAttr()
    other.attributes.each { e: ObjectFloatMap.Entry<RecipeItem<*>?> -> add(e.key, e.value, true) }
    return this
  }

  fun resetInput(item: RecipeItem<*>?): EnvParameter {
    inputs.remove(item, 0f)
    return this
  }

  fun resetAttr(item: RecipeItem<*>?): EnvParameter {
    attributes.remove(item, 0f)
    return this
  }

  fun clearInputs(): EnvParameter {
    inputs.clear()
    return this
  }

  fun clearAttr(): EnvParameter {
    attributes.clear()
    return this
  }

  fun clear(): EnvParameter {
    clearInputs()
    clearAttr()
    return this
  }

  fun applyFullRecipe(recipe: Recipe, fillOptional: Boolean, applyAttribute: Boolean, multiplier: Float): EnvParameter {
    for (stack in recipe.materials.values) {
      if (!fillOptional && stack.optionalCons) continue
      if (!applyAttribute && stack.isAttribute) continue

      inputs.put(stack.item, stack.amount*multiplier)
    }

    return this
  }

  fun addPower(power: Float): EnvParameter {
    add(PowerMark, power + inputs[PowerMark, 0f], false)
    return this
  }

  fun addHeat(heat: Float): EnvParameter {
    add(HeatMark, heat + inputs[HeatMark, 0f], false)
    return this
  }

  fun hasInput(): Boolean {
    return !inputs.isEmpty
  }

  fun hasAttrs(): Boolean {
    return !attributes.isEmpty
  }

  fun eachInputs(cons: Cons2<RecipeItem<*>?, Float?>) {
    inputs.each { e: ObjectFloatMap.Entry<RecipeItem<*>?> -> cons[e.key, e.value] }
  }

  fun eachAttribute(cons: Cons2<RecipeItem<*>?, Float?>) {
    attributes.each { e: ObjectFloatMap.Entry<RecipeItem<*>?> -> cons[e.key, e.value] }
  }

  fun copy(): EnvParameter{
    val copy = EnvParameter()
    copy.inputs.putAll(inputs)
    copy.attributes.putAll(attributes)
    return copy
  }
}
