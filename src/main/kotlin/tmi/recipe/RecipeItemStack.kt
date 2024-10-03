package tmi.recipe

import arc.math.Mathf
import arc.util.Strings
import mindustry.core.UI
import mindustry.ctype.UnlockableContent
import mindustry.world.meta.StatUnit
import tmi.recipe.AmountFormatter.Companion.emptyFormatter
import tmi.recipe.AmountFormatter.Companion.floatFormatter
import tmi.recipe.AmountFormatter.Companion.integerFormatter
import tmi.recipe.AmountFormatter.Companion.persecFormatter
import tmi.recipe.types.RecipeItem

/**保存一个材料项目数据的结构类型，在[Recipe]中作数据记录对象使用 */
class RecipeItemStack(
  /**该条目表示的[UnlockableContent] */
  val item: RecipeItem<*>,
  /**条目附加的数量信息，这将被用作生产计算和显示数据的文本格式化 */
  var amount: Float = 0f
) {
  /**条目数据显示的文本格式化函数，这里返回的文本将显示在条目上以表示数量信息 */
  var amountFormat: AmountFormatter = AmountFormatter { "" }
    private set
  /**在按下TMI热键时显示的备选数目文本的格式化函数，例如每秒消耗的备选文本为单次配方工作的总消耗，而总消耗的备选文本为每秒消耗等*/
  var alternativeFormat: AmountFormatter? = null
    private set
  /**此条目的效率系数，应当绑定为该条目在生产工作中可用时的最高效率倍率，以参与生产计算 */
  var efficiency = 1f
    private set
  /**该条目是否为可选消耗项，应当与实际情况同步 */
  var optionalCons = false
    private set
  /**该条目是否为属性项目，通常用于计算覆盖/关联的方块提供的属性增益 */
  var isAttribute = false
    private set
  /**该条目是否是增幅项目，若为增幅项目则被单独计算boost倍率，该倍率将影响输入物数量计算并直接乘在最终效率上 */
  var isBooster = false
    private set
  /**条目从属的属性组，一个属性组内的项目在工作效率计算时，会以最高的那一个作为计算结果。
   * <br></br>
   * 属性组的划分按照提供的对象确定，任意时候当两个条目的属性组对象[Object.equals]为真时就会被视为从属于同一属性组。
   * 该字段默认空，为空时表示该条目不从属于任何属性组 */
  var attributeGroup: Any? = null
    private set
  /**若为真，此消耗项的属性效率计算会按属性组的最大效率计算，否则会对属性效率求和 */
  var maxAttribute = false
    private set

  @Suppress("UNCHECKED_CAST")
  fun <T, RT: RecipeItem<T>> item(): RT = item as RT
  /**获取经过格式化的表示数量的文本信息 */
  fun getAmount() = amountFormat.format(amount)

  //Deprecated
  @Deprecated(message = "this is a unstandardized function", replaceWith = ReplaceWith("emptyFormat()"), level = DeprecationLevel.ERROR)
  fun setEmptyFormat() = emptyFormat()
  @Deprecated(message = "this is a unstandardized function", replaceWith = ReplaceWith("floatFormat()"), level = DeprecationLevel.ERROR)
  @JvmOverloads
  fun setFloatFormat(mul: Float = 1f) = floatFormat(mul)
  @Deprecated(message = "this is a unstandardized function", replaceWith = ReplaceWith("integerFormat()"), level = DeprecationLevel.ERROR)
  @JvmOverloads
  fun setIntegerFormat(mul: Float = 1f) = integerFormat(mul)
  @Deprecated(message = "this is a unstandardized function", replaceWith = ReplaceWith("persecFormat()"), level = DeprecationLevel.ERROR)
  fun setPersecFormat() = persecFormat()
  @Deprecated(message = "this is a unstandardized function", replaceWith = ReplaceWith("setAltFormat(format)"), level = DeprecationLevel.ERROR)
  fun setAltPersecFormat() = setFormat(persecFormatter())

  //属性设置的工具方法
  fun setEff(efficiency: Float): RecipeItemStack = also { this.efficiency = efficiency }
  @JvmOverloads
  fun setOptional(optionalCons: Boolean = true) = also { this.optionalCons = optionalCons }
  @JvmOverloads
  fun setAttribute(isAttr: Boolean = true) = also { isAttribute = isAttr }
  @JvmOverloads
  fun setBooster(boost: Boolean = true) = also { isBooster = boost }
  @JvmOverloads
  fun setMaxAttr(isMax: Boolean = true) = also { maxAttribute = isMax }
  fun setAttribute(group: Any?) = also { attributeGroup = group }
  fun setFormat(format: AmountFormatter) = also { amountFormat = format }
  fun setAltFormat(format: AmountFormatter) = also { alternativeFormat = format }

  // utils
  fun emptyFormat() = also { setFormat(emptyFormatter()) }
  @JvmOverloads
  fun floatFormat(mul: Float = 1f) = also { setFormat(floatFormatter(mul)) }
  @JvmOverloads
  fun integerFormat(mul: Float = 1f) = also { setFormat(integerFormatter(mul)) }
  fun persecFormat() = also { setFormat(persecFormatter()) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val stack = other as RecipeItemStack
    return amount == stack.amount
        && isAttribute == stack.isAttribute
        && isBooster == stack.isBooster
        && maxAttribute == stack.maxAttribute
        && item == stack.item
        && attributeGroup == stack.attributeGroup
  }

  override fun hashCode(): Int {
    var result = item.hashCode()
    result = 31*result + amount.hashCode()
    result = 31*result + isAttribute.hashCode()
    result = 31*result + isBooster.hashCode()
    result = 31*result + (attributeGroup?.hashCode() ?: 0)
    result = 31*result + maxAttribute.hashCode()
    return result
  }

}
