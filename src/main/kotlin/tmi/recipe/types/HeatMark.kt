package tmi.recipe.types

import arc.graphics.g2d.TextureRegion
import mindustry.gen.Icon

object HeatMark: SingleItemMark("heat-mark") {
  override val icon: TextureRegion get() = Icon.waves.region
  override val ordinal: Int get() = 20000
}
