package tmi.recipe

import arc.func.Cons
import arc.math.Mathf
import arc.scene.ui.layout.Table
import arc.struct.ObjectFloatMap
import arc.struct.OrderedMap
import mindustry.world.Block
import tmi.invoke
import tmi.recipe.types.RecipeItem
import tmi.set
import java.util.*
import kotlin.math.max

/**配方信息的存储类，该类的每一个实例都表示为一个单独的配方，用于显示配方或者计算生产数据
 *
 * @param recipeType [Recipe.recipeType]
 * @param ownerBlock [Recipe.ownerBlock]
 * @param craftTime [Recipe.craftTime]*/
class Recipe @JvmOverloads constructor(
  /**该配方的类型，请参阅[RecipeType] */
  val recipeType: RecipeType,
  /**该配方从属的方块（可以是执行者/建造目标），具体含义取决于配方的类型*/
  val ownerBlock: RecipeItem<*>? = null,
  /**配方的标准耗时，具体来说即该配方在100%的工作效率下执行一次生产的耗时，任意小于0的数字都被认为生产过程是连续的*/ //meta
  val craftTime: Float = -1f,
) {
  companion object {
    /**@see Recipe.getDefaultEff
     */
    val oneEff: EffFunc = getDefaultEff(1f)

    /**@see Recipe.getDefaultEff
     */
    val zeroEff: EffFunc = getDefaultEff(0f)

    /**生成一个适用于vanilla绝大多数工厂与设备的效率计算器，若[配方解析器][RecipeParser]正确的解释了方块，这个函数应当能够正确计算方块的实际工作效率 */
    @JvmStatic
    fun getDefaultEff(baseEff: Float): EffFunc {
      val attrGroups = ObjectFloatMap<Any?>()

      return object : EffFunc {
        override fun calculateEff(recipe: Recipe, env: EnvParameter, mul: Float): Float {
          var eff = 1f

          attrGroups.clear()

          recipe.materials.values().forEach { stack ->
            if (stack!!.isBooster || stack.isAttribute) return@forEach

            if (stack.attributeGroup != null) {
              val e = attrGroups[stack.attributeGroup, 1f]*stack.efficiency*Mathf.clamp(env.getInputs(stack.item)/(stack.amount*mul))

              if (stack.maxAttribute) attrGroups[stack.attributeGroup] = max(attrGroups[stack.attributeGroup, 0f], e)
              else attrGroups.increment(stack.attributeGroup, 0f, e)
            }
            else eff *= max(
              (stack.efficiency*Mathf.clamp(env.getInputs(stack.item)/(stack.amount*mul))),
              (if (stack.optionalCons) 1f else 0f)
            )
          }

          val v = attrGroups.values()
          while (v.hasNext()) {
            eff *= v.next()
          }

          return eff*mul
        }

        override fun calculateMultiple(recipe: Recipe, env: EnvParameter): Float {
          attrGroups.clear()

          var attr = 0f
          var boost = 1f

          recipe.materials.values().forEach { stack ->
            if (!stack!!.isBooster && !stack.isAttribute) return@forEach

            if (stack.isAttribute) {
              val a = stack.efficiency*Mathf.clamp(env.getAttribute(stack.item)/stack.amount)

              if (stack.maxAttribute) attr = max(attr, a)
              else attr += a
            }
            else {
              if (stack.attributeGroup != null) {
                val e = attrGroups[stack.attributeGroup, 1f]*stack.efficiency*Mathf.clamp(env.getInputs(stack.item)/stack.amount)

                if (stack.maxAttribute) attrGroups[stack.attributeGroup] = max(attrGroups[stack.attributeGroup, 0f], e)
                else attrGroups.increment(stack.attributeGroup, 0f, e)
              }
              else boost *= max(
                (stack.efficiency*Mathf.clamp(env.getInputs(stack.item)/stack.amount)),
                (if (stack.optionalCons) 1f else 0f)
              )
            }
          }

          val v = attrGroups.values()
          while (v.hasNext()) {
            boost *= v.next()
          }
          return boost*(baseEff + attr)
        }
      }
    }
  }

  val productions = OrderedMap<RecipeItem<*>, RecipeItemStack>()
  val materials = OrderedMap<RecipeItem<*>, RecipeItemStack>()

  //infos
  /**配方的效率计算函数，用于给定一个输入环境参数和配方数据，计算出该配方在这个输入环境下的工作效率 */
  var efficiencyFunc = oneEff
    private set

  var subInfoBuilder: Cons<Table>? = null
    private set

  /**用配方当前使用的效率计算器计算该配方在给定的环境参数下的运行效率 */
  @JvmOverloads
  fun calculateEfficiency(parameter: EnvParameter, multiplier: Float = calculateMultiple(parameter)): Float {
    return efficiencyFunc.calculateEff(this, parameter, multiplier)
  }

  fun calculateMultiple(parameter: EnvParameter): Float {
    return efficiencyFunc.calculateMultiple(this, parameter)
  }

  @Deprecated("Block must be provided val in constructor", replaceWith = ReplaceWith("constructor parameter"))
  fun setBlock(block: RecipeItem<*>?): Recipe = this
  @Deprecated("Time must be provided val in constructor", replaceWith = ReplaceWith("constructor parameter"))
  fun setTime(time: Float): Recipe = this

  fun setEff(func: EffFunc) = this.also { efficiencyFunc = func }
  fun setSubInfo(builder: Cons<Table>) = this.also { subInfoBuilder = builder }
  fun prependSubInfo(appendBuilder: Cons<Table>) = this.also {
    val last = subInfoBuilder
    subInfoBuilder = Cons{
      appendBuilder(it)
      last?.get(it)
    }
  }
  fun appendSubInfo(appendBuilder: Cons<Table>) = this.also {
    val last = subInfoBuilder
    subInfoBuilder = Cons{
      last?.get(it)
      appendBuilder(it)
    }
  }

  @Deprecated("Utility function naming has been standardize to addXxxxFfff", replaceWith = ReplaceWith("addMaterialFloat(item, amount)"))
  fun addMaterial(item: RecipeItem<*>, amount: Float) = addMaterialFloat(item, amount)
  @Deprecated("Utility function naming has been standardize to addXxxxFfff", replaceWith = ReplaceWith("addMaterialInt(item, amount)"))
  fun addMaterial(item: RecipeItem<*>, amount: Int) = addMaterialInteger(item, amount)
  @Deprecated("Utility function naming has been standardize to addXxxxFfff", replaceWith = ReplaceWith("addMaterial(item, amount)"))
  fun addMaterialRaw(item: RecipeItem<*>, amount: Float) = addMaterial(item, amount as Number)
  @Deprecated("Utility function naming has been standardize to addXxxxFfff", replaceWith = ReplaceWith("addProductionFloat(item, amount)"))
  fun addProduction(item: RecipeItem<*>, amount: Float) = addProductionFloat(item, amount)
  @Deprecated("Utility function naming has been standardize to addXxxxFfff", replaceWith = ReplaceWith("addProductionInteger(item, amount)"))
  fun addProduction(item: RecipeItem<*>, amount: Int) = addProductionInteger(item, amount)
  @Deprecated("Utility function naming has been standardize to addXxxxFfff", replaceWith = ReplaceWith("addProduction(item, amount)"))
  fun addProductionRaw(item: RecipeItem<*>, amount: Float) = addProduction(item, amount as Number)

  fun addMaterial(item: RecipeItem<*>, amount: Number) =
    RecipeItemStack(item, amount.toFloat()).also { materials.put(item, it) }
  fun addProduction(item: RecipeItem<*>, amount: Number) =
    RecipeItemStack(item, amount.toFloat()).also { productions.put(item, it) }

  //utils
  fun addMaterialInteger(item: RecipeItem<*>, amount: Int) =
    (if (craftTime > 0) RecipeItemStack(item, amount/craftTime).integerFormat(craftTime)
    else RecipeItemStack(
      item,
      amount.toFloat()
    ).integerFormat()).also {
      it.setAltFormat(AmountFormatter.persecFormatter())
      materials.put(item, it)
    }

  fun addMaterialFloat(item: RecipeItem<*>, amount: Float) =
    (if (craftTime > 0) RecipeItemStack(item, amount/craftTime).floatFormat(craftTime)
    else RecipeItemStack(
      item,
      amount
    ).floatFormat()).also {
      it.setAltFormat(AmountFormatter.persecFormatter())
      materials.put(item, it)
    }

  fun addMaterialPersec(item: RecipeItem<*>, persec: Float) =
    RecipeItemStack(item, persec).persecFormat().also {
      if (craftTime > 0) it.setAltFormat(AmountFormatter.floatFormatter(craftTime))
      materials.put(item, it)
    }

  fun addProductionInteger(item: RecipeItem<*>, amount: Int) =
    (if (craftTime > 0) RecipeItemStack(item, amount/craftTime).integerFormat(craftTime)
    else RecipeItemStack(
      item,
      amount.toFloat()
    ).integerFormat()).also {
      it.setAltFormat(AmountFormatter.persecFormatter())
      productions.put(item, it)
    }

  fun addProductionFloat(item: RecipeItem<*>, amount: Float) =
    (if (craftTime > 0) RecipeItemStack(item, amount/craftTime).floatFormat(craftTime)
    else RecipeItemStack(
      item,
      amount
    ).floatFormat()).also {
      it.setAltFormat(AmountFormatter.persecFormatter())
      productions.put(item, it)
    }

  fun addProductionPersec(item: RecipeItem<*>, perSec: Float) =
    RecipeItemStack(item, perSec).persecFormat().also {
      if (craftTime > 0) it.setAltFormat(AmountFormatter.floatFormatter(craftTime))
      productions.put(item, it)
    }

  fun containsProduction(production: RecipeItem<*>) = productions.containsKey(production)
  fun containsMaterial(material: RecipeItem<*>?) = materials.containsKey(material)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Recipe) return false
    if (other.recipeType !== recipeType || other.ownerBlock !== ownerBlock) return false

    if (other.materials.size != materials.size || other.productions.size != productions.size) return false

    return other.materials == materials && other.productions == productions
  }

  override fun hashCode(): Int {
    return Objects.hash(recipeType, productions.orderedKeys(), materials.orderedKeys(), ownerBlock)
  }

  interface EffFunc {
    fun calculateEff(recipe: Recipe, env: EnvParameter, mul: Float): Float
    fun calculateMultiple(recipe: Recipe, env: EnvParameter): Float
  }
}
