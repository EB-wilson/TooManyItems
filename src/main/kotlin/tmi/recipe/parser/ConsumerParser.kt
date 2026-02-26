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

abstract class ConsumerParser<T : Block> : RecipeParser<T>() {
  protected fun registerCons(recipe: Recipe, vararg cons: Consume) {
    registerCons(recipe, {}, *cons)
  }

  protected fun registerCons(recipe: Recipe, handle: Cons<RecipeItemStack<*>>, vararg cons: Consume) {
    for (consume in cons) {
      for (entry in consumeParsers) {
        if (entry.key[consume]) entry.value[recipe, consume, handle]
      }
    }
  }

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

    fun registerConsumeParser(
      filter: Boolf<Consume>,
      block: Cons3<Recipe, Consume, Cons<RecipeItemStack<*>>>
    ) {
      consumeParsers.put(filter, block)
    }

    inline fun <reified Type: Consume> registerConsumeParser(
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
      registerConsumeParser<ConsumeItems> { recipe, consume, handle ->
        for (item in consume.items) {
          handle(
            recipe.addMaterialInteger(item.item.getWrap(), item.amount)
              .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
              .setOptional(consume.optional)
          )
        }
      }
      registerConsumeParser<ConsumeItemFilter>(
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
      registerConsumeParser<ConsumeLiquids> { recipe, consume, handle ->
        for (liquid in consume.liquids) {
          handle(
            recipe.addMaterialPersec(liquid.liquid.getWrap(), liquid.amount)
              .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
              .setOptional(consume.optional)
          )
        }
      }
      registerConsumeParser<ConsumeLiquid>{ recipe, consume, handle ->
        handle(
          recipe.addMaterialPersec(consume.liquid.getWrap(), consume.amount)
            .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
            .setOptional(consume.optional)
        )
      }
      registerConsumeParser<ConsumeLiquidFilter>{ recipe, consume, handle ->
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
      registerConsumeParser<ConsumePayloads> { recipe, consume, handle ->
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
      registerConsumeParser<ConsumePower> { recipe, consume, handle ->
        handle(
          recipe.addMaterialPersec(PowerMark, consume.usage)
            .setType(RecipeItemType.POWER)
            .setOptional(consume.optional)
        )
      }
    }
  }
}
