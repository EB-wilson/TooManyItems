package tmi.recipe

import arc.math.Mathf
import arc.util.Strings
import mindustry.core.UI
import tmi.util.Utils
import kotlin.math.roundToInt

fun interface AmountFormatter {
  companion object{
    @JvmStatic
    fun emptyFormatter() = AmountFormatter { "" }

    @JvmStatic
    @JvmOverloads
    fun floatFormatter(multiplier: Float = 1f) = AmountFormatter{ f ->
      if (f*multiplier > 1000) UI.formatAmount(Mathf.round(f*multiplier).toLong())
      else Strings.autoFixed(f*multiplier, 1)
    }

    @JvmStatic
    @JvmOverloads
    fun integerFormatter(multiplier: Float = 1f) = AmountFormatter{ f ->
      if (f*multiplier > 1000) UI.formatAmount(Mathf.round(f*multiplier).toLong())
      else Mathf.round(f*multiplier).toString()
    }

    @JvmStatic
    fun timedAmountFormatter() = AmountFormatter{ f ->
      if (f > 0) {
        if (f < 1f/Utils.Unit.MIN.multi) {
          val (value, unit) = Utils.timeTo(f)

          (if (value > 1000) UI.formatAmount((value).toLong())
          else if (value < 100) Strings.autoFixed(value, 1)
          else value.roundToInt().toString()) + unit.colorCode + unit.strify
        }
        else {
          val (value, unit) = Utils.unitTimed(f)

          (if (value > 1000) UI.formatAmount((value).toLong())
          else if (value < 100) Strings.autoFixed(value, 1)
          else value.roundToInt().toString()) + unit.colorCode + "/" + unit.strify
        }
      }
      else "--"
    }

    @JvmStatic
    fun unitTimedFormatter() = AmountFormatter{ f ->
      val (value, unit) = Utils.unitTimed(f)

      if (value > 0) {
        (if (value > 1000) UI.formatAmount((value).toLong())
        else if (value < 100) Strings.autoFixed(value, 1)
        else value.roundToInt().toString()) + unit.colorCode + "/" + unit.strify
      }
      else "--"
    }

    @JvmStatic
    fun timeToFormatter() = AmountFormatter{ f ->
      val (value, unit) = Utils.timeTo(f)

      if (value > 0) {
        (if (value > 1000) UI.formatAmount((value).toLong())
        else if (value < 100) Strings.autoFixed(value, 1)
        else value.roundToInt().toString()) + unit.colorCode + unit.strify
      }
      else "--"
    }

    @Deprecated(
      message = "standardized function name to unitTimedFormatter",
      replaceWith = ReplaceWith("unitTimedFormatter()")
    )
    @JvmStatic
    fun persecFormatter() = AmountFormatter{ f ->
      (if (f*60 > 1000) UI.formatAmount((f*60).toLong())
      else Strings.autoFixed(f*60, 1)) + "[gray]/sec"
    }
  }

  fun format(f: Float): String
}