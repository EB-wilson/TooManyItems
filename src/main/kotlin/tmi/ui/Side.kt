package tmi.ui

import arc.scene.style.Drawable
import tmi.util.Consts

enum class Side(val drawable: Drawable) {
  LEFT(Consts.side_left),
  RIGHT(Consts.side_right),
  TOP(Consts.side_top),
  BOTTOM(Consts.side_bottom)
}