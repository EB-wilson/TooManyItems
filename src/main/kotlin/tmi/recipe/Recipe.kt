package tmi.recipe

import arc.func.Cons
import arc.scene.ui.layout.Table
import arc.struct.ObjectFloatMap
import arc.struct.OrderedMap
import tmi.util.invoke
import tmi.recipe.types.CalculateMethod
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.util.set
import tmi.util.mto
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**配方信息的存储类，该类的每一个实例都表示为一个单独的配方，用于显示配方或者计算生产数据
 *
 * @param recipeType [Recipe.recipeType]
 * @param ownerBlock [Recipe.ownerBlock]
 * @param craftTime [Recipe.craftTime]*/
open class Recipe @JvmOverloads constructor(
  /**该配方的类型，请参阅[RecipeType] */
  val recipeType: RecipeType,
  /**该配方从属的方块（可以是执行者/建造目标），具体含义取决于配方的类型*/
  val ownerBlock: RecipeItem<*>,
  /**配方的标准耗时，具体来说即该配方在100%的工作效率下执行一次生产的耗时，任意小于0的数字都被认为生产过程是连续的*/ //meta
  val craftTime: Float = -1f,
) {
  private var completed = false
  private var hash = -1

  private val productionMap = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()
  private val materialMap = OrderedMap<RecipeItem<*>, RecipeItemStack<*>>()

  val productions get() = productionMap.values().toList()
  val materials get() = materialMap.values().toList()

  val materialGroups: List<List<RecipeItemStack<*>>> get() = run {
    var n = 0
    materials.groupBy { it.group?:n++ }.map { it.value.toList() }
  }

  //infos
  /**[tmi.recipe.types.RecipeItemType.NORMAL]计算区的计算方法
   * @see tmi.recipe.types.CalculateMethod*/
  var normalMethod = CalculateMethod.MIN
    private set
  /**[tmi.recipe.types.RecipeItemType.POWER]计算区的计算方法
   * @see CalculateMethod*/
  var powerMethod = CalculateMethod.MULTIPLE
    private set
  /**[tmi.recipe.types.RecipeItemType.ATTRIBUTE]计算区的计算方法
   * @see CalculateMethod*/
  var attributeMethod = CalculateMethod.MIN
    private set
  /**[tmi.recipe.types.RecipeItemType.BOOSTER]计算区的计算方法
   * @see CalculateMethod*/
  var boosterMethod = CalculateMethod.MIN
    private set
  /**[tmi.recipe.types.RecipeItemType.ISOLATED]计算区的计算方法
   * @see CalculateMethod*/
  var isolatedMethod = CalculateMethod.MIN
    private set

  var baseEfficiency = 1f
    private set

  var subInfoBuilder: Cons<Table>? = null
    private set

  fun complete(){
    if (completed) return
    productionMap.orderedKeys().sort()
    materialMap.orderedKeys().sort()

    hash = Objects.hash(
      recipeType.id,
      productionMap.keys().toList(),
      materialMap.keys().toList(),
      ownerBlock
    )

    completed = true
  }

  /**用配方当前使用的效率计算器计算该配方在给定的环境参数下的运行效率 */
  @JvmOverloads
  open fun calculateEfficiency(parameter: EnvParameter, multiplier: Float = calculateMultiple(parameter)): Float {
    if (multiplier <= 0f) return 0f

    val matN = materials.filter { it.itemType == RecipeItemType.NORMAL }
    val matI = materials.filter { it.itemType == RecipeItemType.ISOLATED }
    val normal = if (matN.any()) calculateZone(
      matN,
      normalMethod,
      parameter,
      multiplier
    ) else 1f
    val booster = calculateZone(
      materials.filter { it.itemType == RecipeItemType.BOOSTER },
      boosterMethod,
      parameter,
      multiplier
    )
    val isolated = if (matI.any()) calculateZone(
      matI,
      isolatedMethod,
      parameter,
      1f
    ) else 1f

    return normal*max(booster, 1f)*multiplier*isolated
  }

  open fun calculateMultiple(parameter: EnvParameter, multiplier: Float = 1f): Float {
    val powerM = materials.filter { it.itemType == RecipeItemType.POWER }
    val attr = calculateZone(
      materials.filter { it.itemType == RecipeItemType.ATTRIBUTE },
      attributeMethod,
      parameter,
      multiplier
    )
    val power = if (powerM.any()) calculateZone(
      powerM,
      powerMethod,
      parameter,
      multiplier
    ) else 1f

    return (baseEfficiency + attr)*power
  }

  internal fun calculateZone(
    filteredStacks: List<RecipeItemStack<*>>,
    method: CalculateMethod,
    parameter: EnvParameter,
    multiplier: Float = 1f
  ): Float {
    val groupEff = ObjectFloatMap<RecipeItemGroup>()

    val effs = filteredStacks.map { it mto it.efficiencyBy(parameter, multiplier) }
    method.cleanOptional(
      effs, effs.filter { it.first.isOptional },
      { it.second }, { second = it }
    )

    effs.forEach {
      if (it.second.isInfinite()) return@forEach
      it.first.group?.also { group ->
        groupEff[group] = max(groupEff.get(group, it.second), it.second)
      }
    }

    val arr = groupEff.values().toArray().also { seq ->
      seq.addAll(*effs.filter { it.first.group == null }.map { it.second }.toFloatArray())
    }

    return method.calculate(arr.toArray())
  }

  //properties
  fun setNormalMethod(attr: CalculateMethod) = also { normalMethod = attr }
  fun setPowerMethod(attr: CalculateMethod) = also { powerMethod = attr }
  fun setAttributeMethod(attr: CalculateMethod) = also { attributeMethod = attr }
  fun setBoosterMethod(attr: CalculateMethod) = also { boosterMethod = attr }
  fun setIsolatedMethod(attr: CalculateMethod) = also { isolatedMethod = attr }
  fun setBaseEff(baseEfficiency: Float) = also { this.baseEfficiency = baseEfficiency }
  fun setSubInfo(builder: Cons<Table>) = also { subInfoBuilder = builder }
  fun prependSubInfo(prependBuilder: Cons<Table>) = also {
    val last = subInfoBuilder
    subInfoBuilder = Cons{
      prependBuilder(it)
      last?.get(it)
    }
  }
  fun appendSubInfo(appendBuilder: Cons<Table>) = also {
    val last = subInfoBuilder
    subInfoBuilder = Cons{
      last?.get(it)
      appendBuilder(it)
    }
  }

  //handles
  fun addMaterial(item: RecipeItem<*>, amount: Number) =
    RecipeItemStack(item, amount.toFloat()).also { materialMap[item] = it }
  fun addMaterial(stack: RecipeItemStack<*>){ materialMap[stack.item] = stack }
  fun addProduction(item: RecipeItem<*>, amount: Number) =
    RecipeItemStack(item, amount.toFloat()).also { productionMap[item] = it }
  fun addProduction(stack: RecipeItemStack<*>){ productionMap[stack.item] = stack }

  fun getMaterial(item: RecipeItem<*>): RecipeItemStack<*>? = materialMap[item]
  fun getProduction(item: RecipeItem<*>): RecipeItemStack<*>? = productionMap[item]

  //utils
  fun addMaterialInteger(item: RecipeItem<*>, amount: Int) =
    (if (craftTime > 0) RecipeItemStack(item, amount/craftTime).integerFormat(craftTime)
    else RecipeItemStack(
      item,
      amount.toFloat()
    ).integerFormat()).also {
      it.setAltFormat(AmountFormatter.timedAmountFormatter())
      materialMap[item] = it
    }

  fun addMaterialFloat(item: RecipeItem<*>, amount: Float) =
    (if (craftTime > 0) RecipeItemStack(item, amount/craftTime).floatFormat(craftTime)
    else RecipeItemStack(
      item,
      amount
    ).floatFormat()).also {
      it.setAltFormat(AmountFormatter.timedAmountFormatter())
      materialMap[item] = it
    }

  fun addMaterialPersec(item: RecipeItem<*>, persec: Float) =
    RecipeItemStack(item, persec).unitTimedFormat().also {
      if (craftTime > 0) it.setAltFormat(AmountFormatter.timedAmountFormatter())
      materialMap[item] = it
    }

  fun addProductionInteger(item: RecipeItem<*>, amount: Int) =
    (if (craftTime > 0) RecipeItemStack(item, amount/craftTime).integerFormat(craftTime)
    else RecipeItemStack(
      item,
      amount.toFloat()
    ).integerFormat()).also {
      it.setAltFormat(AmountFormatter.timedAmountFormatter())
      productionMap[item] = it
    }

  fun addProductionFloat(item: RecipeItem<*>, amount: Float) =
    (if (craftTime > 0) RecipeItemStack(item, amount/craftTime).floatFormat(craftTime)
    else RecipeItemStack(
      item,
      amount
    ).floatFormat()).also {
      it.setAltFormat(AmountFormatter.timedAmountFormatter())
      productionMap[item] = it
    }

  fun addProductionPersec(item: RecipeItem<*>, perSec: Float) =
    RecipeItemStack(item, perSec).unitTimedFormat().also {
      if (craftTime > 0) it.setAltFormat(AmountFormatter.timedAmountFormatter())
      productionMap[item] = it
    }

  fun containsProduction(production: RecipeItem<*>) = productionMap.containsKey(production)
  fun containsMaterial(material: RecipeItem<*>?) = materialMap.containsKey(material)

  fun RecipeItemStack<*>.efficiencyBy(env: EnvParameter, multiplier: Float): Float =
    if (itemType == RecipeItemType.ATTRIBUTE) efficiency*min(env.getAttribute(item)/(amount*multiplier), 1f)
    else efficiency*min(env.getInputs(item)/(amount*multiplier), 1f)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Recipe) return false
    if (other.recipeType !== recipeType || other.ownerBlock !== ownerBlock) return false

    if (other.materialMap.size != materialMap.size || other.productionMap.size != productionMap.size) return false

    return other.materialMap == materialMap && other.productionMap == productionMap
  }

  override fun hashCode(): Int {
    if (!completed) throw IllegalStateException("Recipe is not completed")
    return hash
  }

  override fun toString(): String {
    return "recipe(type: $recipeType block: $ownerBlock, time: $craftTime){materials: ${materialMap.orderedKeys()}, productions: ${productionMap.orderedKeys()}}"
  }

  /**配方的效率计算函数，用于给定一个输入环境参数和配方数据，计算出该配方在这个输入环境下的工作效率 */
  @Deprecated("Use standard efficiency calculate method.")
  var efficiencyFunc = object: EffFunc{
    override fun calculateEff(recipe: Recipe, env: EnvParameter, mul: Float) = 0f
    override fun calculateMultiple(recipe: Recipe, env: EnvParameter) = 0f
  }
  @Deprecated("Use standard efficiency calculate method.")
  fun setEff(func: EffFunc) = this.also { efficiencyFunc = func }
  @Deprecated("Use standard efficiency calculate method.")
  interface EffFunc {
    fun calculateEff(recipe: Recipe, env: EnvParameter, mul: Float): Float
    fun calculateMultiple(recipe: Recipe, env: EnvParameter): Float
  }
}
