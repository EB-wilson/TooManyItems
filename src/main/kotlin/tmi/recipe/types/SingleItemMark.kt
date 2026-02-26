package tmi.recipe.types

import arc.Core
import arc.util.Time
import tmi.TooManyItems
import tmi.recipe.RecipeType

abstract class SingleItemMark(name: String) : RecipeItem<String>(name) {
  init {
    register()
  }

  internal open fun register(){
    Core.app.post {
      TooManyItems.itemsManager.addItemWrap(name, this)
    }
  }

  override val ordinal = -1
  override val typeOrdinal = -1
  override val typeID = -1
  override val name = item
  override val localizedName: String = Core.bundle["mark.$item.name"]
  override val hidden = false
  override val hasDetails = false
  override val locked = false
}