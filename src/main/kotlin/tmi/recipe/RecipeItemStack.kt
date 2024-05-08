package tmi.recipe

import arc.math.Mathf
import arc.util.Nullable
import arc.util.Strings
import mindustry.core.UI
import mindustry.ctype.UnlockableContent
import mindustry.world.meta.StatUnit
import tmi.recipe.types.RecipeItem

/**保存一个材料项目数据的结构类型，在[Recipe]中作数据记录对象使用 */
class RecipeItemStack constructor(
  /**该条目表示的[UnlockableContent] */
  val item: RecipeItem<*>,
  /**条目附加的数量信息，这将被用作生产计算和显示数据的文本格式化 */
  var amount: Float = 0f
) {
  /**条目数据显示的文本格式化函数，这里返回的文本将显示在条目上以表示数量信息 */
  var amountFormat: AmountFormatter = AmountFormatter { "" }

  /**此条目的效率系数，应当绑定为该条目在生产工作中可用时的最高效率倍率，以参与生产计算 */
  var efficiency = 1f

  /**该条目是否为可选消耗项，应当与实际情况同步 */
  var optionalCons = false

  /**该条目是否为属性项目，通常用于计算覆盖/关联的方块提供的属性增益 */
  var isAttribute = false

  /**该条目是否是增幅项目，若为增幅项目则被单独计算boost倍率，该倍率将影响输入物数量计算并直接乘在最终效率上 */
  var isBooster = false

  /**条目从属的属性组，一个属性组内的项目在工作效率计算时，会以最高的那一个作为计算结果。
   * <br></br>
   * 属性组的划分按照提供的对象确定，任意时候当两个条目的属性组对象[Object.equals]为真时就会被视为从属于同一属性组。
   * 该字段默认空，为空时表示该条目不从属于任何属性组 */
  var attributeGroup: Any? = null

  /**若为真，此消耗项的属性效率计算会按属性组的最大效率计算，否则会对属性效率求和 */
  var maxAttr = false

  fun item(): RecipeItem<*> {
    return item
  }

  fun amount(): Float {
    return amount
  }

  /**获取经过格式化的表示数量的文本信息 */
  fun getAmount(): String {
    return amountFormat.format(amount)
  }

  //属性设置的工具方法
  fun setEfficiency(efficiency: Float): RecipeItemStack {
    this.efficiency = efficiency
    return this
  }

  fun setOptionalCons(optionalCons: Boolean): RecipeItemStack {
    this.optionalCons = optionalCons
    return this
  }

  fun setOptionalCons(): RecipeItemStack {
    return setOptionalCons(true)
  }

  fun setAttribute(): RecipeItemStack {
    this.isAttribute = true
    return this
  }

  fun setBooster(): RecipeItemStack {
    isBooster = true
    return this
  }

  fun setBooster(boost: Boolean): RecipeItemStack {
    isBooster = boost
    return this
  }

  fun setMaxAttr(): RecipeItemStack {
    this.maxAttr = true
    return this
  }

  fun setAttribute(group: Any?): RecipeItemStack {
    this.attributeGroup = group
    return this
  }

  fun setFormat(format: AmountFormatter): RecipeItemStack {
    this.amountFormat = format
    return this
  }

  fun setEmptyFormat(): RecipeItemStack {
    this.amountFormat = AmountFormatter { "" }
    return this
  }

  fun setFloatFormat(mul: Float): RecipeItemStack {
    setFormat { f ->
      if (f*mul > 1000) UI.formatAmount(
        Mathf.round(f*mul).toLong()
      )
      else Strings.autoFixed(f*mul, 1)
    }
    return this
  }

  fun setIntegerFormat(mul: Float): RecipeItemStack {
    setFormat { f ->
      if (f*mul > 1000) UI.formatAmount(
        Mathf.round(f*mul).toLong()
      )
      else Mathf.round(f*mul).toString()
    }
    return this
  }

  fun setFloatFormat(): RecipeItemStack {
    setFormat { f -> if (f > 1000) UI.formatAmount(f.toLong()) else Strings.autoFixed(f, 1) }
    return this
  }

  fun setIntegerFormat(): RecipeItemStack {
    setFormat { f ->
      if (f > 1000) UI.formatAmount(f.toLong())
      else f.toInt()
        .toString()
    }
    return this
  }

  fun setPresecFormat(): RecipeItemStack {
    setFormat { f ->
      (if (f*60 > 1000) UI.formatAmount((f*60).toLong())
      else Strings.autoFixed(
        f*60,
        2
      )) + "/" + StatUnit.seconds.localized()
    }
    return this
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val stack = other as RecipeItemStack
    return amount == stack.amount
        && isAttribute == stack.isAttribute
        && isBooster == stack.isBooster
        && maxAttr == stack.maxAttr
        && item == stack.item
        && attributeGroup == stack.attributeGroup
  }

  override fun hashCode(): Int {
    var result = item.hashCode()
    result = 31*result + amount.hashCode()
    result = 31*result + isAttribute.hashCode()
    result = 31*result + isBooster.hashCode()
    result = 31*result + (attributeGroup?.hashCode() ?: 0)
    result = 31*result + maxAttr.hashCode()
    return result
  }

  fun interface AmountFormatter {
    fun format(f: Float): String
  }
}
