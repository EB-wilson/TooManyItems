package tmi.util

import arc.input.KeyBind
import arc.input.KeyCode

object KeyBinds {

  // Single Hot Key
  val hotKey = KeyBind.add("tmi_hot_key", KeyCode.controlLeft, "tmi")

  // CombinedKeys bind
  val save = CombinedKeys(KeyCode.controlLeft, KeyCode.s)
  val saveAs = CombinedKeys(KeyCode.altLeft, KeyCode.s)
  val saveAll = CombinedKeys(KeyCode.controlLeft, KeyCode.shiftLeft, KeyCode.s)
  val undo = CombinedKeys(KeyCode.controlLeft, KeyCode.z)
  val redo = CombinedKeys(KeyCode.controlLeft, KeyCode.y)

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
