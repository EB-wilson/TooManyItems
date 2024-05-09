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

  override fun ordinal() = -1
  override fun typeID() = -1
  override fun name() = item
  override fun localizedName(): String = Core.bundle["tmi.$item"]
  override fun hidden() = false
}