package tmi.recipe.types

import arc.Core
import arc.util.Time
import tmi.TooManyItems
import tmi.recipe.RecipeType

abstract class SingleItemMark(name: String) : RecipeItem<String>(name) {
  init {
    Time.run(0f){
      TooManyItems.itemsManager.addItemWrap(name, this)
      RecipeType.generator.addPower(this)
    }
  }

  override val ordinal = -1
  override val typeOrdinal = -1
  override val typeID = -1
  override val name = item
  override val localizedName: String = Core.bundle["tmi.$item"]
  override val hidden = false
}