package tmi.recipe.types

import arc.Core
import arc.graphics.g2d.TextureRegion
import mindustry.gen.Icon
import tmi.TooManyItems
import tmi.util.Consts

object PowerMark: SingleItemMark("power-mark") {
  override val icon: TextureRegion get() = Icon.power.region
  override val ordinal: Int get() = 10000
  override val typeTag: String by lazy { Core.bundle["type.power.name"] }
  override val ownMod: String get() = Consts.VANILLA
}
