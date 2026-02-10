package tmi.util

object Utils {
  private var globalUnit: Unit? = null

  fun unitTimed(v: Float, uncheckGlobal: Boolean = false): Pair<Float, Unit> {
    if (uncheckGlobal || globalUnit == null) {
      var value = v*60
      var unit = Unit.SEC

      if (value < 1) { value *= 60; unit = Unit.MIN }
      if (value < 1) { value *= 60; unit = Unit.HOUR }
      if (value < 1) { value *= 24; unit = Unit.DAY } // Is this possible?

      return value to unit
    }
    else globalUnit!!.let { return v*it.multi to it }
  }

  fun timeTo(v: Float, uncheckGlobal: Boolean = false): Pair<Float, Unit> {
    if (uncheckGlobal || globalUnit == null) {
      var value = Unit.TICK.multi/v
      var unit = Unit.TICK

      if (value > 100) { value /= 60; unit = Unit.SEC }
      if (value > 100) { value /= 60; unit = Unit.MIN }
      if (value > 100) { value /= 60; unit = Unit.HOUR }
      if (value > 100) { value /= 24; unit = Unit.DAY }

      return value to unit
    }
    else globalUnit!!.let { return v*it.multi to it }
  }

  fun mandatoryGlobalUnit(unit: Unit) {
    globalUnit = unit
  }

  fun releaseGlobalUnit() {
    globalUnit = null
  }

  enum class Unit(
    val colorCode: String,
    val strify: String,
    val multi: Float,
  ) {
    TICK("[white]", "tick", 1f),
    SEC("[gray]", "sec", 60f),
    MIN("[lightgray]", "min", 60f * 60f),
    HOUR("[red]", "hour", 60f * 60f * 60f),
    DAY("[crimson]", "day", 60f * 60f * 60f * 24f);

    fun next(): Unit = when(this) {
      TICK -> SEC
      SEC -> MIN
      MIN -> HOUR
      HOUR -> DAY
      DAY -> TICK
    }
  }
}