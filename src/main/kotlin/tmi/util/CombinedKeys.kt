package tmi.util

import arc.Input
import arc.input.KeyCode
import java.io.Serializable

/**一个记录特定格式的标准组合键的标记对象
 *
 * 一个组合键由主键和控制键构成，其中控制键为`Ctrl`，*/
class CombinedKeys(vararg keys: KeyCode): Serializable {
  val isCtrl: Boolean
  val isAlt: Boolean
  val isShift: Boolean
  val key: KeyCode

  init {
    val ctrl = keys.filter { it.isCtrl() }
    val alt = keys.filter { it.isAlt() }
    val shift = keys.filter { it.isShift() }
    val normal = keys.filter { !ctrl.contains(it) && !alt.contains(it) && !shift.contains(it) }
    isCtrl = ctrl.isNotEmpty()
    isAlt = alt.isNotEmpty()
    isShift = shift.isNotEmpty()
    key = normal.lastOrNull()?:throw IllegalArgumentException("No key specified")
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

    builder.append(key.value)

    return builder.toString()
  }

  override fun hashCode(): Int {
    var res = key.hashCode()
    res = res*31 + isShift.hashCode()
    res = res*31 + isAlt.hashCode()
    res = res*31 + isCtrl.hashCode()
    return res
  }

  override fun equals(other: Any?): Boolean {
    return other is CombinedKeys
        && other.key == key
        && other.isCtrl == isCtrl
        && other.isAlt == isAlt
        && other.isShift == isShift
  }

  companion object {
    @JvmStatic
    fun toString(res: Iterable<KeyCode>): String {
      val builder = StringBuilder()

      if (res.contains(KeyCode.controlLeft) || res.contains(KeyCode.controlRight)) builder.append("Ctrl").append(" + ")
      if (res.contains(KeyCode.altLeft) || res.contains(KeyCode.altRight)) builder.append("Alt").append(" + ")
      if (res.contains(KeyCode.shiftLeft) || res.contains(KeyCode.shiftRight)) builder.append("Shift").append(" + ")

      builder.append(res.lastOrNull { !it.isControllerKey() }?.value?:"")

      return builder.toString()
    }
  }
}

fun KeyCode.isControllerKey()
    = this == KeyCode.controlLeft || this == KeyCode.controlRight
    || this == KeyCode.altLeft || this == KeyCode.altRight
    || this == KeyCode.shiftLeft || this == KeyCode.shiftRight

fun KeyCode.isCtrl() = this == KeyCode.controlLeft || this == KeyCode.controlRight
fun KeyCode.isAlt() = this == KeyCode.altLeft || this == KeyCode.altRight
fun KeyCode.isShift() = this == KeyCode.shiftLeft || this == KeyCode.shiftRight

