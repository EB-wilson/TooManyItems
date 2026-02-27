package tmi.recipe.types

import kotlin.math.max
import kotlin.math.min

/**该枚举记录了一系列用于计算一串数字的简单公式。
 *
 * @author EBwilson
 * @since 3.0*/
enum class CalculateMethod(
  val base: Float
) {
  /**加法，此方法会将接收的所有数字进行累加求和
   *
   * ```
   * add(v1, v2, v3, ..., vn) -> v1 + v2 + v3 + ... + vn
   * ```*/
  ADD(0f),
  /**乘法，此方法会依次对接收的所有数字求积
   *
   * ```
   * MULTIPLE(v1, v2, v3, ..., vn) -> v1 * v2 * v3 * ... * vn
   * ```*/
  MULTIPLE(1f),
  /**取最小值，此方法用于取接收到的所有数字中的最小值
   *
   * ```
   * MIN(v1, v2, v3, ..., vn) -> min(v1, v2, v3, ..., vn)
   * ```*/
  MIN(Float.POSITIVE_INFINITY),
  /**取最大值，此方法用于取接收到的所有数字中的最大值
   *
   * ```
   * MAX(v1, v2, v3, ..., vn) -> max(v1, v2, v3, ..., vn)
   * ```*/
  MAX(Float.NEGATIVE_INFINITY);

  /**接收若干个对象，并使用`numeric`函数获取其数字作为数字序列，对此数字序列执行计算。*/
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

  /**接收一个浮点值组成的数组，对此数组内的浮点数执行计算。*/
  fun calculate(objects: FloatArray): Float {
    if (!objects.any()) return 0f

    var result = base
    when(this){
      ADD -> objects.forEach { result += it }
      MULTIPLE -> objects.forEach { result *= it }
      MIN -> objects.forEach { result = min(it, result) }
      MAX -> objects.forEach { result = max(it, result) }
    }

    return if (result.isInfinite()) 0f else result
  }

  /**清理序列中的可选的无效数据，接收若干个对象和其中的可选项，在可选项中查找会导致计算结果偏小的数据，
   * 并将可无效化此数据的数字通过回调函数回传，并用于调整原始数据序列。*/
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
        optional.forEach { obj -> if (numeric(obj) < min) clean(obj, base) }
      }
      MAX -> { /*no action*/ }
    }
  }
}