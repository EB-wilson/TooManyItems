package tmi.recipe.types

import arc.Core
import arc.graphics.g2d.TextureRegion
import mindustry.gen.Icon
import tmi.TooManyItems

class PowerMark private constructor() : RecipeItem<String>("power-mark") {
  override fun ordinal(): Int {
    return -1
  }

  override fun typeID(): Int {
    return -1
  }

  override fun name(): String {
    return item
  }

  override fun localizedName(): String {
    return Core.bundle["tmi.$item"]
  }

  override fun icon(): TextureRegion {
    return Icon.power.region
  }

  override fun hidden(): Boolean {
    return false
  }

  companion object {
    @JvmField
    var INSTANCE: PowerMark = TooManyItems.itemsManager.addItemWrap("power-mark", PowerMark())
  }
}
