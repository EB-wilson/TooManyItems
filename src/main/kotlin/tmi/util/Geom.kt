package tmi.util

import arc.math.Angles
import arc.util.Align

object Geom {
  fun angleToD4Integer(x: Float, y: Float, width: Float, height: Float) = angleToD4Integer(
    Angles.angle(x, y),
    Angles.angle(width, height)
  )

  fun angleToD4Integer(angle: Float, width: Float, height: Float) = angleToD4Integer(
    angle,
    Angles.angle(width, height)
  )

  fun angleToD4Integer(angle: Float, check: Float = 45f): Int {
    val a = angle%360
    val c = check%360

    return when {
      a > c && a < 180 - c -> 1
      a > 180 - c && a < 180 + c -> 2
      a > 180 + c && a < 360 - c -> 3
      else -> 0
    }
  }
}