package tmi.util

import arc.func.Prov

class ProviderWrap<T>(private val provider: Prov<T>){
  private var _value: T? = null

  val value: T get() = if (_value == null) provider.get().also { _value = it } else _value!!

  fun invalidate() {
    _value = null
  }

  fun refresh() {
    _value = provider.get()
  }
}

fun <T> wrap(provider: Prov<T>) = ProviderWrap(provider)