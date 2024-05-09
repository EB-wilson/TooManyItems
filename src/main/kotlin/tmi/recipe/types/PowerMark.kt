package tmi.recipe.types

import arc.Core
import arc.graphics.g2d.TextureRegion
import mindustry.gen.Icon
import tmi.TooManyItems

object PowerMark: SingleItemMark("power-mark") {
  override fun icon(): TextureRegion = Icon.power.region
}
