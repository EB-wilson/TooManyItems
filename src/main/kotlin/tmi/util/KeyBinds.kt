package tmi.util

import arc.Core
import arc.input.KeyCode

class KeyBinds {
  @JvmField
  var hotKey: KeyCode = KeyCode.controlLeft

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
