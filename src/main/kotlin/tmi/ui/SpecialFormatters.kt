package tmi.ui

import arc.struct.ObjectMap
import tmi.recipe.AmountFormatter
import tmi.recipe.types.RecipeItem
import tmi.util.set

object SpecialFormatters {
  private val specialFormatters = ObjectMap<RecipeItem<*>, AmountFormatter>()

  fun setSpecialFormatter(item: RecipeItem<*>, spec: AmountFormatter) {
    specialFormatters[item] = spec
  }

  fun getFormatter(item: RecipeItem<*>): AmountFormatter? = specialFormatters[item]
  fun getFormatter(item: RecipeItem<*>, def: AmountFormatter): AmountFormatter = specialFormatters[item]?:def
}