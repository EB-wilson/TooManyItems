package tmi.recipe.parser

import arc.func.Boolf
import arc.func.Cons
import arc.func.Cons2
import arc.func.Cons3
import arc.struct.ObjectMap
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.consumers.*
import tmi.util.invoke
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeParser
import tmi.recipe.types.PowerMark

/**[RecipeParser]的扩展实现，该类型创建了若干工具函数用于快速地将[Consume]注册为配方的条目，当您的解析器需要对[Consume]进行处理时，
 * 可以令您的配方解析器直接扩展该类型，以使用本类型中提供的相关工具。
 *
 * 该类型中主要提供了[registerCons]函数用于接收[Recipe]和[Consume]，并按照注册的消耗项解析规则向[Recipe]中添加配方条目。
 *
 * 该类型包含对Mindustry内大部分默认[Consume]类型的解析记录，若您有自定义的消耗项实现，该类的伴生单例对象中定义了若干关于定义消耗项解析条目的方法。
 * 您可以调用[ConsumerParser.registerConsumeParser]及其重载函数来添加消耗项解析记录。
 *
 * @author EBwilson
 * @since 2.4*/
abstract class ConsumerParser<T : Block> : RecipeParser<T>() {
  /**[registerCons]函数的重载，该函数无回调函数。
   *
   * @see registerCons*/
  protected fun registerCons(recipe: Recipe, vararg cons: Consume) {
    registerCons(recipe, {}, *cons)
  }

  /**[registerCons]函数的重载，区别在于其回调函数不需要接收正在解析的消耗项。
   * 
   * @see registerCons*/
  protected fun registerCons(recipe: Recipe, handle: Cons<RecipeItemStack<*>>, vararg cons: Consume) {
    for (consume in cons) {
      for (entry in consumeParsers) {
        if (entry.key[consume]) entry.value[recipe, consume, handle]
      }
    }
  }

  /**向接收到的[Recipe]注册消耗项材料，对[Consume]的解析需要有匹配的解析记录，若未找到匹配项则会不进行任何操作。
   *
   * 该函数接收一个回调函数`handle`，在解析消耗项时，所有向配方中添加的配方条目均会通过此函数进行回调以便为各条目设置其属性。
   *
   * 简单的用例：
   *
   * ```
   * fun sample(block: Block){
   *   val recipe = Recipe(/*...*/)
   *
   *   registerCons(recipe, { consume, stack ->
   *     stack.setEfficiency(1.1f)
   *   }, block.consumes)
   * }
   * ```
   *
   * @param recipe 注册操作的目标配方
   * @param handle 新增配方条目的回调函数
   * @param cons 将要注册的消耗项列表*/
  protected fun registerCons(recipe: Recipe, handle: Cons2<Consume, RecipeItemStack<*>>, vararg cons: Consume) {
    for (consume in cons) {
      for (entry in consumeParsers) {
        if (entry.key[consume]) entry.value[recipe, consume, { handle(consume, it) }]
      }
    }
  }

  companion object {
    protected var consumeParsers: ObjectMap<Boolf<Consume>, Cons3<Recipe, Consume, Cons<RecipeItemStack<*>>>> =
      ObjectMap()

    /**注册消耗项解析记录，用于在为配方注册消耗材料时分析传入的与过滤器相匹配的[Consume]。
     *
     * 对[Consume]的解析实现由`block`参数接收的回调函数块内提供，该函数体会接收三个参数：
     *
     * - `recipe`: 目标配方
     * - `consume`: 当前正在处理的[Consume]
     * - `handle`: 外层函数回调
     *
     * 其中，`handle`函数为调用[ConsumerParser.registerCons]时提供的外层回调函数，用于修饰新增的配方条目。
     *
     * 您应当在此函数体中按照消耗项描述的内容，向接收到的`recipe`添加相应的配方条目作为材料，并且需要将该条目堆通过调用`handle`函数回传到外层。
     *
     * 一个简单的例子：
     *
     * ```kotlin
     * registerConsumeParser({ it is ItemConsume }) { recipe, consume, handle ->
     *   for (item in consume.items) {
     *     handle(
     *       recipe.addMaterialInteger(item.item.getWrap(), item.amount)
     *         .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
     *         .setOptional(consume.optional)
     *     )
     *   }
     * }
     * ```
     *
     * @param filter 用于匹配[Consume]的过滤器，仅当此函数对接收的消耗项返回true时，该条记录才会去解析此[Consume]
     * @param block 消耗项解析函数，当满足条件时，接收的[Consume]将会传入到该函数中并进行处理。*/
    @JvmStatic
    fun registerConsumeParser(
      filter: Boolf<Consume>,
      block: Cons3<Recipe, Consume, Cons<RecipeItemStack<*>>>
    ) {
      consumeParsers.put(filter, block)
    }

    /**注册消耗项解析条目方法的类型安全重载，该函数作用同[registerConsumeParser]，但是接收一个类型参数，并基于传入的类型来对[Consume]进行过滤。
     *
     * 该函数依然能够接收过滤器，其需要消耗项与提供的类型相匹配同时，还与此过滤器相匹配才视为匹配通过。
     *
     * @see registerConsumeParser*/
    @JvmStatic
    @JvmOverloads
    inline fun <reified Type: Consume> registerTypedConsumeParser(
      filter: Boolf<Type> = { true },
      block: Cons3<Recipe, Type, Cons<RecipeItemStack<*>>>
    ){
      registerConsumeParser(
        { it is Type && filter.get(it) },
      ){ recipe, consume, handle ->
        block.get(recipe, consume as Type, handle)
      }
    }

    fun registerVanillaConsumeParser() {
      //items
      registerTypedConsumeParser<ConsumeItems> { recipe, consume, handle ->
        for (item in consume.items) {
          handle(
            recipe.addMaterialInteger(item.item.getWrap(), item.amount)
              .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
              .setOptional(consume.optional)
          )
        }
      }
      registerTypedConsumeParser<ConsumeItemFilter>(
        { c -> c !is ConsumeItemExplode }
      ) { recipe, consume, handle ->
        val consumeGroup = RecipeItemGroup()
        for (item in Vars.content.items().select { i -> consume.filter[i] }) {
          val eff = consume.itemEfficiencyMultiplier(item)
          handle(
            recipe.addMaterialInteger(item.getWrap(), 1)
              .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
              .setOptional(consume.optional)
              .setEfficiency(eff)
              .efficiencyFormat(eff)
              .setGroup(consumeGroup)
          )
        }
      }

      //liquids
      registerTypedConsumeParser<ConsumeLiquids> { recipe, consume, handle ->
        for (liquid in consume.liquids) {
          handle(
            recipe.addMaterialPersec(liquid.liquid.getWrap(), liquid.amount)
              .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
              .setOptional(consume.optional)
          )
        }
      }
      registerTypedConsumeParser<ConsumeLiquid>{ recipe, consume, handle ->
        handle(
          recipe.addMaterialPersec(consume.liquid.getWrap(), consume.amount)
            .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
            .setOptional(consume.optional)
        )
      }
      registerTypedConsumeParser<ConsumeLiquidFilter>{ recipe, consume, handle ->
        val consumeGroup = RecipeItemGroup()
        for (liquid in Vars.content.liquids().select { i -> consume.filter[i] }) {
          val eff = consume.liquidEfficiencyMultiplier(liquid)
          handle(
            recipe.addMaterialPersec(liquid.getWrap(), consume.amount)
              .setOptional(consume.optional)
              .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
              .setEfficiency(eff)
              .boostAndConsFormat(eff)
              .setGroup(consumeGroup)
          )
        }
      }

      //payloads
      registerTypedConsumeParser<ConsumePayloads> { recipe, consume, handle ->
        for (stack in consume.payloads) {
          if (stack.amount > 1) handle(recipe.addMaterialInteger(stack.item.getWrap(), stack.amount))
          else handle(
            recipe.addMaterialInteger(stack.item.getWrap(), 1)
              .setOptional(consume.optional)
              .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
          )
        }
      }

      //power
      registerTypedConsumeParser<ConsumePower> { recipe, consume, handle ->
        handle(
          recipe.addMaterialPersec(PowerMark, consume.usage)
            .setType(RecipeItemType.POWER)
            .setOptional(consume.optional)
        )
      }
    }
  }
}
