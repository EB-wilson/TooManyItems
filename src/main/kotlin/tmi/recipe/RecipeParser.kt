package tmi.recipe

import arc.struct.Seq
import mindustry.world.Block
import tmi.TooManyItems
import tmi.recipe.types.RecipeItem

abstract class RecipeParser<T : Block> {
  /**互斥解析器的类型列表 */
  var excludes = Seq<Class<out RecipeParser<*>>>()

  /**在处理前初始化该分析器 */
  open fun init() {}

  /**该分析器对于参数给定的那个分析器是否是互斥的，该方法返回true会阻止另一个配方分析器分析此方块。
   *
   * 例如一个方块可以被两个分析器分析，但是该分析器与另一个互斥，那么那一个分析器将被跳过而不解释这一方块 */
  fun exclude(parser: RecipeParser<*>): Boolean {
    return excludes.contains { e: Class<out RecipeParser<*>> -> e.isAssignableFrom(parser.javaClass) }
  }

  /**给出的方块是否是此配方分析器工作的目标方块，若是，且没有另一个同样以此方块为目标的解析器与这个解析器[互斥][RecipeParser.exclude]，则该解析器将被应用于该方块 */
  abstract fun isTarget(content: Block): Boolean

  /**分析一个方块，将其可执行的所有配方以一个[Seq]的形式返回。生成的配方应当和设施在实际运转中的各数据保持一致。 */
  abstract fun parse(content: T): Seq<Recipe>

  companion object {
    @JvmStatic
    protected fun <T> T.getWrap(): RecipeItem<T> {
      return wrapItem(this)
    }

    @JvmStatic
    protected fun <I> wrapItem(item: I): RecipeItem<I> {
      return TooManyItems.itemsManager.getItem(item)
    }
  }
}
