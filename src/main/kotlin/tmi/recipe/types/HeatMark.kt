package tmi.recipe.types

import arc.graphics.g2d.TextureRegion
import mindustry.gen.Icon

object HeatMark: SingleItemMark("heat-mark") {
  override fun icon(): TextureRegion = Icon.waves.region
}
