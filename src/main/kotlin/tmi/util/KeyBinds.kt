package tmi.util

import arc.input.KeyBind
import arc.input.KeyCode

class KeyBinds {
  val hotKey = KeyBind.add("tmi_hot_key", KeyCode.controlLeft, "tmi")

  fun load() {
    hotKey.load()
  }

  fun reset(name: String?) {
    when (name) {
      "hot_key" -> hotKey.resetToDefault()
    }
    save()
  }

  fun save() {
    hotKey.save()
  }

  fun resetAll() {
    hotKey.resetToDefault()
    save()
  }
}
