package tmi

import kotlin.reflect.KClass

/**入口点绑定标记，注释在mod主类型上以标记此mod的TMI配方适配器的入口类型
 *
 * @param value 配方适配器类型，填写你的适配器主类即可*/
@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class RecipeEntryPoint(
  val value: KClass<out RecipeEntry>
)