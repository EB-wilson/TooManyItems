package tmi.util

import arc.util.io.Writes

fun Writes.b(vararg bytes: Int) = bytes.forEach { b(it) }
fun Writes.bool(vararg bools: Boolean) = bools.forEach { bool(it) }
fun Writes.d(vararg bytes: Double) = bytes.forEach { d(it) }
fun Writes.f(vararg floats: Float) = floats.forEach { f(it) }
fun Writes.i(vararg ints: Int) = ints.forEach { i(it) }
fun Writes.l(vararg longs: Long) = longs.forEach { l(it) }
fun Writes.s(vararg shorts: Int) = shorts.forEach { s(it) }
fun Writes.str(vararg strings: String) = strings.forEach { str(it) }