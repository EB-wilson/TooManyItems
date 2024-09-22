package tmi.util

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.graphics.g2d.TextureRegion
import arc.math.Mathf.*
import kotlin.math.abs

object Shapes {
  /**在指定位置绘制一个扇形
   *
   * @param x 扇形的圆心x坐标
   * @param y 扇形的圆心y坐标
   * @param radius 扇形所属圆形的半径
   * @param angle 扇形的内角
   * @param rotate 扇形的旋转角，即扇形的右侧边相对x轴的角度
   * @param segments 扇形精细度*/
  @JvmStatic
  @JvmOverloads
  fun fan(
    x: Float, y: Float, radius: Float, angle: Float,
    rotate: Float = 0f,
    segments: Int = abs(Lines.circleVertices(radius)*(angle/360f)).toInt(),
  ) {
    val segmentAngle = angle / segments

    for (i in 0 until segments) {
      val currentAngle = rotate + segmentAngle * i

      val startX = x + radius * cosDeg(currentAngle)
      val startY = y + radius * sinDeg(currentAngle)
      val endX = x + radius * cosDeg(currentAngle + segmentAngle)
      val endY = y + radius * sinDeg(currentAngle + segmentAngle)

      Fill.tri(x, y, startX, startY, endX, endY)
    }
  }

  fun line(x: Float, y: Float, c: Color, x2: Float, y2: Float, c2: Color) {
    line(x, y, c, x2, y2, c2, true)
  }

  fun line(x: Float, y: Float, c: Color, x2: Float, y2: Float, c2: Color, cap: Boolean) {
    val hstroke = Lines.getStroke()/2f
    val len = len(x2 - x, y2 - y)
    val diffx = (x2 - x)/len*hstroke
    val diffy = (y2 - y)/len*hstroke

    if (cap) {
      Fill.quad(
        x - diffx - diffy,
        y - diffy + diffx,
        c.toFloatBits(),

        x - diffx + diffy,
        y - diffy - diffx,
        c.toFloatBits(),

        x2 + diffx + diffy,
        y2 + diffy - diffx,
        c2.toFloatBits(),

        x2 + diffx - diffy,
        y2 + diffy + diffx,
        c2.toFloatBits()
      )
    }
    else {
      Fill.quad(
        x - diffy,
        y + diffx,
        c.toFloatBits(),

        x + diffy,
        y - diffx,
        c.toFloatBits(),

        x2 + diffy,
        y2 - diffx,
        c2.toFloatBits(),

        x2 - diffy,
        y2 + diffx,
        c2.toFloatBits()
      )
    }
  }

}