package tmi.util

import arc.Core
import arc.Input
import arc.input.KeyCode

class KeyBinds {
  @JvmField
  var hotKey = KeyCode.controlLeft
  @JvmField
  var undo = MultiKeyBind(KeyCode.controlLeft, KeyCode.z)
  @JvmField
  var redo = MultiKeyBind(KeyCode.controlLeft, KeyCode.shiftLeft, KeyCode.z)

  fun load() {
    hotKey = KeyCode.byOrdinal(Core.settings.getInt("tmi_hotkey", KeyCode.controlLeft.ordinal))

    undo = MultiKeyBind(Core.settings.getString("tmi_undo", undo.toString()))
    redo = MultiKeyBind(Core.settings.getString("tmi_redo", redo.toString()))
  }

  fun reset(name: String?) {
    when (name) {
      "hot_key" -> hotKey = KeyCode.controlLeft
      "undo" -> undo = MultiKeyBind(KeyCode.controlLeft, KeyCode.z)
      "redo" -> redo = MultiKeyBind(KeyCode.controlLeft, KeyCode.shiftLeft, KeyCode.z)
    }
    save()
  }

  fun save() {
    Core.settings.put("tmi_hotkey", hotKey.ordinal)
    Core.settings.put("tmi_undo", undo.toString())
    Core.settings.put("tmi_redo", redo.toString())
  }

  fun resetAll() {
    hotKey = KeyCode.controlLeft
    save()
  }
}

class MultiKeyBind{
  val isCtrl: Boolean
  val isAlt: Boolean
  val isShift: Boolean
  val key: KeyCode

  constructor(vararg keys: KeyCode){
    val ctrl = keys.filter { it == KeyCode.controlLeft || it == KeyCode.controlRight }
    val alt = keys.filter { it == KeyCode.altLeft || it == KeyCode.altRight }
    val shift = keys.filter { it == KeyCode.shiftLeft || it == KeyCode.shiftRight }
    val normal = keys.filter { !ctrl.contains(it) && !alt.contains(it) && !shift.contains(it) }

    isCtrl = ctrl.isNotEmpty()
    isAlt = alt.isNotEmpty()
    isShift = shift.isNotEmpty()
    key = normal.lastOrNull()?:throw IllegalArgumentException("No key specified")
  }

  constructor(str: String){
    val list = str.split("+").map { it.trim() }
    isCtrl = list.contains("Ctrl")
    isAlt = list.contains("Alt")
    isShift = list.contains("Shift")
    key = KeyCode.valueOf(
      list.lastOrNull { it != "Ctrl" && it != "Alt" && it != "Shift" }
        ?:throw IllegalArgumentException("Invalid keybind string: $str")
    )
  }

  fun isDown(input: Input): Boolean{
    if ((isCtrl && !input.ctrl()) || (!isCtrl && input.alt())) return false
    if ((isAlt && !input.alt()) || (!isAlt && input.alt())) return false
    if ((isShift && !input.shift()) || (!isShift && input.shift())) return false
    return input.keyDown(key)
  }

  fun isReleased(input: Input): Boolean{
    if ((isCtrl && !input.ctrl()) || (!isCtrl && input.alt())) return false
    if ((isAlt && !input.alt()) || (!isAlt && input.alt())) return false
    if ((isShift && !input.shift()) || (!isShift && input.shift())) return false
    return input.keyRelease(key)
  }

  fun isTap(input: Input): Boolean{
    if ((isCtrl && !input.ctrl()) || (!isCtrl && input.alt())) return false
    if ((isAlt && !input.alt()) || (!isAlt && input.alt())) return false
    if ((isShift && !input.shift()) || (!isShift && input.shift())) return false
    return input.keyTap(key)
  }

  override fun toString(): String {
    val builder = StringBuilder()

    if (isCtrl) builder.append("Ctrl").append(" + ")
    if (isAlt) builder.append("Alt").append(" + ")
    if (isShift) builder.append("Shift").append(" + ")

    builder.append(key.name)

    return builder.toString()
  }

  companion object {
    fun toString(res: Iterable<KeyCode>): String {
      val builder = StringBuilder()

      if (res.contains(KeyCode.controlLeft) || res.contains(KeyCode.controlRight)) builder.append("Ctrl").append(" + ")
      if (res.contains(KeyCode.altLeft) || res.contains(KeyCode.altRight)) builder.append("Alt").append(" + ")
      if (res.contains(KeyCode.shiftLeft) || res.contains(KeyCode.shiftRight)) builder.append("Shift").append(" + ")

      builder.append(
        res.lastOrNull {
          it != KeyCode.controlLeft && it != KeyCode.controlRight
          && it != KeyCode.altLeft && it != KeyCode.altRight
          && it != KeyCode.shiftLeft && it != KeyCode.shiftRight
        }?:""
      )

      return builder.toString()
    }
  }
}
