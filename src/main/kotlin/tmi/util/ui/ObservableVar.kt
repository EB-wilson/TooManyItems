package tmi.util.ui

import kotlin.reflect.KProperty

class ObservableVar<V>(
  private var _value: V
) {
  private val observers = mutableListOf<Behavior>()

  operator fun getValue(thisRef: Any?, property: KProperty<*>): V = _value
  operator fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
    if (_value != value) {
      _value = value
      observers.forEach { it.observedMuted() }
    }
  }

  fun observed(observer: Behavior) {
    observers.add(observer)
  }

  fun unobserved(observer: Behavior) {
    observers.remove(observer)
  }
}

fun <T> observed(value: T) = ObservableVar(value)
