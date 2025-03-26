package tmi.util

import arc.math.Angles
import arc.math.geom.Vec2

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

  fun getRectNearest(res: Vec2, x: Float, y: Float, botLeft: Vec2, topRight: Vec2): Vec2 {
    val centX = (botLeft.x + topRight.x) / 2
    val centY = (botLeft.y + topRight.y) / 2

    res.x = if (x < centX) botLeft.x else topRight.x
    res.y = if (y < centY) botLeft.y else topRight.y

    return res
  }
}