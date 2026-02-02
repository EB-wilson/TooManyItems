package tmi.util

import arc.struct.IntMap
import arc.struct.ObjectFloatMap
import arc.struct.ObjectIntMap
import arc.struct.ObjectMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass

data class MutablePair<A, B>(
  var first: A,
  var second: B
){
  override fun toString(): String = "($first, $second)"
}

data class MutableTriple<A, B, C>(
  var first: A,
  var second: B,
  var third: C
){
  override fun toString(): String = "($first, $second, $third)"
}

infix fun <A, B> A.mto(that: B): MutablePair<A, B> = MutablePair(this, that)
infix fun <A, B, C> MutablePair<A, B>.mto(that: C) = MutableTriple(this.first, this.second, that)

@OptIn(ExperimentalContracts::class)
inline fun <reified T> Any.ifInst(block: (T) -> Unit){
  contract {
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
  }
  if (this is T) block(this)
}

@OptIn(ExperimentalContracts::class)
inline fun <reified T: Any, R> Any.runInst(clazz: KClass<T>, block: (T) -> R): R?{
  contract {
    callsInPlace(block, InvocationKind.AT_MOST_ONCE)
  }
  return if (this is T) block(this) else null
}

operator fun <K, V> ObjectMap<K, V>.set(key: K, value: V): V = put(key, value)
operator fun <K> ObjectIntMap<K>.set(key: K, value: Int) = put(key, value)
operator fun <K> ObjectFloatMap<K>.set(key: K, value: Float) = put(key, value)
operator fun <V> IntMap<V>.set(key: Int, value: V): V = put(key, value)
inline fun <K, V> ObjectMap<K, V>.forEach(block: (K, V) -> Unit){
  for (e in this){
    block(e.key, e.value)
  }
}