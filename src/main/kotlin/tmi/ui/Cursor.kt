package tmi.ui

import arc.Graphics

class Cursor : Graphics.Cursor {
  override fun dispose() {
  }

  companion object {
    var recipe: Graphics.Cursor? = null
    var search: Graphics.Cursor? = null

    fun init() {
    }
  }
}
