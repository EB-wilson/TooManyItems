package tmi.recipe.types

import arc.graphics.g2d.TextureRegion
import mindustry.gen.Icon
import tmi.recipe.AmountFormatter
import tmi.ui.SpecialFormatters
import tmi.ui.calculator.CalculatorView

object HeatMark: SingleItemMark("heat-mark") {
  init { SpecialFormatters.setSpecialFormatter(this, AmountFormatter.floatFormatter()) }

  override val icon: TextureRegion get() = Icon.waves.region
  override val ordinal: Int get() = 20000
}
