package tmi.recipe.types

import kotlin.math.max
import kotlin.math.min

enum class CalculateMethod(
  val base: Float
) {
  ADD(0f),
  MULTIPLE(1f),
  MIN(Float.POSITIVE_INFINITY),
  MAX(Float.NEGATIVE_INFINITY);

  fun <T> calculate(objects: Iterable<T>, numeric: (T) -> Float): Float {
    if (!objects.any()) return 0f

    var result = base
    when(this){
      ADD -> objects.forEach { result += numeric(it) }
      MULTIPLE -> objects.forEach { result *= numeric(it) }
      MIN -> objects.forEach { result = min(numeric(it), result) }
      MAX -> objects.forEach { result = max(numeric(it), result) }
    }

    return result
  }

  fun calculate(objects: FloatArray): Float {
    if (!objects.any()) return 0f

    var result = base
    when(this){
      ADD -> objects.forEach { result += it }
      MULTIPLE -> objects.forEach { result *= it }
      MIN -> objects.forEach { result = min(it, result) }
      MAX -> objects.forEach { result = max(it, result) }
    }

    return result
  }

  fun <T> cleanOptional(
    objects: Iterable<T>,
    optional: Iterable<T>,
    numeric: (T) -> Float,
    clean: T.(Float) -> Unit,
  ) {
    if (!objects.any() || !optional.any()) return

    when (this) {
      ADD -> optional.forEach { obj -> if (numeric(obj) < 0) clean(obj, base) }
      MULTIPLE -> optional.forEach { obj -> if (numeric(obj) < 1) clean(obj, base) }
      MIN -> {
        val min = objects.minOf(numeric)
        optional.forEach { obj -> if (numeric(obj) <= min) clean(obj, base) }
      }
      MAX -> { /*no action*/ }
    }
  }
}