package tmi.recipe

import arc.math.Mathf
import mindustry.ctype.UnlockableContent
import tmi.recipe.AmountFormatter.Companion.emptyFormatter
import tmi.recipe.AmountFormatter.Companion.floatFormatter
import tmi.recipe.AmountFormatter.Companion.integerFormatter
import tmi.recipe.AmountFormatter.Companion.timeToFormatter
import tmi.recipe.AmountFormatter.Companion.timedAmountFormatter
import tmi.recipe.AmountFormatter.Companion.unitTimedFormatter
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import kotlin.ReplaceWith

/**保存一个材料项目数据的结构类型，在[Recipe]中作数据记录对象使用 */
class RecipeItemStack<T>(
  /**该条目表示的[UnlockableContent] */
  val item: RecipeItem<T>,
  /**条目附加的数量信息，这将被用作生产计算和显示数据的文本格式化 */
  var amount: Float = 0f,
) {
  /**此条目的效率系数，该倍数用于在效率计算时参与数据计算，所在的计算区由[isBooster]和[isAttribute]两个变量决定：
   *
   * */
  var efficiency = 1f
    private set
  /**条目数据显示的文本格式化函数，这里返回的文本将显示在条目上以表示数量信息 */
  var amountFormat: AmountFormatter = AmountFormatter { "" }
    private set
  /**在按下TMI热键时显示的备选数目文本的格式化函数，例如每秒消耗的备选文本为单次配方工作的总消耗，而总消耗的备选文本为每秒消耗等*/
  var alternativeFormat: AmountFormatter? = null
    private set
  /**该条目是否为可选消耗项，若为真则该条目无输入时不会影响最终效率*/
  var isOptional = false
    private set
  /**此条目的条目类型，这会决定该条目在效率计算时数据所在的计算区域。
   * @see tmi.recipe.types.RecipeItemType*/
  var itemType: RecipeItemType = RecipeItemType.NORMAL

  /**该条目从属的组，在计算效率时，同属一个条目组的条目将取其中最终效率最高的一个参与效率计算，其余将被无视。
   *
   * 该属性默认为空，表示条目不属于任何组，始终独立进行计算。
   *
   * @see setGroup
   * @see clearGroup*/
  var group: RecipeItemGroup? = null
    private set

  /**获取此堆所保存的而物品类型*/
  fun item(): RecipeItem<T> = item
  /**获取经过格式化的表示数量的文本信息 */
  fun getAmount() = amountFormat.format(amount)

  //属性设置的工具方法
  fun setEfficiency(efficiency: Float) = also { this.efficiency = efficiency }
  @JvmOverloads
  fun setOptional(optionalCons: Boolean = true) = also { this.isOptional = optionalCons }
  fun setType(type: RecipeItemType) = also { this.itemType = type }

  /**为该条目设置组，如果已经从属于一个组，则会将此条目从前一个组中移除*/
  fun setGroup(group: RecipeItemGroup) = also {
    this.group?.unsetItem(this)
    this.group = group
    group.addItem(this)
  }
  /**清除该条目所属的条目组。*/
  fun clearGroup() = also {
    group?.unsetItem(this)
    this.group = null
  }
  fun setFormat(format: AmountFormatter) = also { amountFormat = format }
  fun setAltFormat(format: AmountFormatter) = also { alternativeFormat = format }

  //Utils
  fun emptyFormat() = also { setFormat(emptyFormatter()) }
  @JvmOverloads
  fun floatFormat(mul: Float = 1f) = also { setFormat(floatFormatter(mul)) }
  @JvmOverloads
  fun integerFormat(mul: Float = 1f) = also { setFormat(integerFormatter(mul)) }
  fun unitTimedFormat() = also { setFormat( unitTimedFormatter()) }
  fun timeToFormat() = also { setFormat( timeToFormatter()) }
  fun timedFormat() = also { setFormat( timedAmountFormatter()) }

  fun efficiencyFormat(eff: Float) = also {
    setFormat { _ ->
      "${if (eff >= 1) "[#98ffa9]" else "[#ff9584]"}${Mathf.round(eff*100)}%"
    }
  }
  fun efficiencyFormat(baseEff: Float, eff: Float) = also {
    setFormat { _ ->
      "${if (baseEff > 0.001f || eff >= 1) "[#98ffa9]" else "[#ff9584]"}${if (baseEff > 0.001f) "+" else ""}${Mathf.round(eff*100)}%"
    }
  }
  fun boostAndConsFormat(eff: Float) = also {
    val old = amountFormat

    setFormat { f ->
      """
      ${old.format(f)}
      [#98ffa9]${Mathf.round(eff*100)}%
      """.trimIndent()
    }
  }
  fun effAndConsFormat(eff: Float) = also {
    val old = amountFormat

    setFormat { f ->
      """
      ${old.format(f)}
      ${if (eff >= 1) "[#98ffa9]" else "[#ff9584]"}${Mathf.round(eff*100)}%
      """.trimIndent()
    }
  }
  fun extraEffAndConsFormat(eff: Float) = also {
    val old = amountFormat

    setFormat { f ->
      """
      ${old.format(f)}
      [#98ffa9]+${Mathf.round(eff*100)}%
      """.trimIndent()
    }
  }

  //Deprecated
  @Deprecated(message = "Use isOptional instead.", replaceWith = ReplaceWith("isOptional"))
  var optionalCons by ::isOptional
  /**该条目是否为属性乘区计算项，若为真则在计算效率基础倍率时进行加算*/
  @Deprecated(message = "Use RecipeItemType in constructor to declare type, not post.")
  var isAttribute = false
    private set
  /**该条目是否是倍增乘区计算项，若为真则在计算效率基础倍率上进行乘算*/
  @Deprecated(message = "Use RecipeItemType in constructor to declare type, not post.")
  var isBooster = false
    private set
  /**条目从属的属性组，一个属性组内的项目在工作效率计算时，会以最高的那一个作为计算结果。
   *
   * 属性组的划分按照提供的对象确定，任意时候当两个条目的属性组对象[Object.equals]为真时就会被视为从属于同一属性组。
   * 该字段默认空，为空时表示该条目不从属于任何属性组 */
  @Deprecated(
    message = "Group no longer only apply on attributes, use generic group.",
    replaceWith = ReplaceWith("group")
  )
  var attributeGroup by ::group
  @JvmOverloads
  @Deprecated(
    message = "Use RecipeItemType to declare type, not post.",
    replaceWith = ReplaceWith("setType(RecipeItemType.ATTRIBUTE)")
  )
  fun setAttribute(isAttr: Boolean = true) = also { isAttribute = isAttr }
  @JvmOverloads
  @Deprecated(
    message = "Use RecipeItemType to declare type, not post.",
    replaceWith = ReplaceWith("setType(RecipeItemType.BOOSTER)")
  )
  fun setBooster(boost: Boolean = true) = also { isBooster = boost }
  @Deprecated(
    message = "Group no longer only apply on attributes, use generic group.",
    replaceWith = ReplaceWith("setGroup(group)")
  )
  fun setAttribute(group: Any?){ /**no action*/ }
  @Deprecated(
    message = "Method name standardized, use setEfficiency() instead.",
    replaceWith = ReplaceWith("setEfficiency(efficiency)")
  )
  fun setEff(efficiency: Float) = setEfficiency(efficiency)

  override fun toString() = "(item: $item amount: ${amountFormat.format(amount)})"

  fun copy() = RecipeItemStack(item, amount).also{
    it.amountFormat = amountFormat
    it.alternativeFormat = alternativeFormat
    it.efficiency = efficiency
    it.isOptional = isOptional
    it.itemType = itemType
    it.group = group
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val stack = other as RecipeItemStack<*>
    return amount == stack.amount
           && item == stack.item
           && itemType == stack.itemType
           && group == stack.group
  }

  override fun hashCode(): Int {
    var result = item.hashCode()
    result = 31*result + amount.hashCode()
    result = 31*result + itemType.hashCode()
    result = 31*result + (group?.hashCode() ?: 0)
    return result
  }

}
