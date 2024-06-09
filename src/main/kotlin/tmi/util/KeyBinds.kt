package tmi.util

import arc.Core
import arc.Input
import arc.func.Cons
import arc.input.KeyCode
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.struct.Seq

class KeyBinds {
  @JvmField
  var hotKey = KeyCode.controlLeft

  fun load() {
    hotKey = KeyCode.byOrdinal(Core.settings.getInt("tmi_hotkey", KeyCode.controlLeft.ordinal))
  }

  fun reset(name: String?) {
    when (name) {
      "hot_key" -> hotKey = KeyCode.controlLeft
    }
    save()
  }

  fun save() {
    Core.settings.put("tmi_hotkey", hotKey.ordinal)
  }

  fun resetAll() {
    hotKey = KeyCode.controlLeft
    save()
  }
}
