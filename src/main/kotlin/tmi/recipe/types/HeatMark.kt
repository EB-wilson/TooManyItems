package tmi.recipe.types

import arc.Core
import arc.graphics.g2d.TextureRegion
import mindustry.gen.Icon
import tmi.recipe.AmountFormatter
import tmi.ui.SpecialFormatters
import tmi.ui.calculator.CalculatorView
import tmi.util.Consts

object HeatMark: SingleItemMark("heat-mark") {
  init { SpecialFormatters.setSpecialFormatter(this, AmountFormatter.floatFormatter()) }

  override val icon: TextureRegion get() = Icon.waves.region
  override val typeTag: String get() = Core.bundle["type.power.name"]
  override val ordinal: Int get() = 20000
  override val ownMod: String get() = Consts.VANILLA
}
