package tmi.recipe.parser

import arc.func.Boolf
import arc.func.Cons
import arc.func.Cons3
import arc.struct.ObjectMap
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.consumers.*
import tmi.invoke
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
      for (entry in consumerParsers) {
        if (entry.key[consume]) entry.value[recipe, consume, handle]
      }
    }
  }

  companion object {
    protected var consumerParsers: ObjectMap<Boolf<Consume>, Cons3<Recipe, Consume, Cons<RecipeItemStack<*>>>> =
      ObjectMap()

    fun registerVanillaConsParser(type: Boolf<Consume>, handle: Cons3<Recipe, Consume, Cons<RecipeItemStack<*>>>) {
      consumerParsers.put(type, handle)
    }

    fun registerVanillaConsumeParser() {
      //items
      registerVanillaConsParser(
        { c -> c is ConsumeItems },
        { recipe: Recipe, consume: Consume, handle ->
          for (item in (consume as ConsumeItems).items) {
            handle(
              recipe.addMaterialInteger(item.item.getWrap(), item.amount)
                .setOptional(consume.optional)
            )
          }
        })
      registerVanillaConsParser(
        { c -> c is ConsumeItemFilter && c !is ConsumeItemExplode },
        { recipe, consume, handle ->
          consume as ConsumeItemFilter
          val consumeGroup = RecipeItemGroup()
          for (item in Vars.content.items().select { i -> consume.filter[i] }) {
            val eff = consume.itemEfficiencyMultiplier(item)
            handle(
              recipe.addMaterialInteger(item.getWrap(), 1)
                .setOptional(consume.optional)
                .setEff(eff)
                .efficiencyFormat(eff)
                .setGroup(consumeGroup)
            )
          }
        })

      //liquids
      registerVanillaConsParser(
        { c -> c is ConsumeLiquids },
        { recipe, consume, handle ->
          for (liquid in (consume as ConsumeLiquids).liquids) {
            handle(recipe.addMaterialPersec(liquid.liquid.getWrap(), liquid.amount)
              .setOptional(consume.optional))
          }
        })
      registerVanillaConsParser(
        { c -> c is ConsumeLiquid },
        { recipe, consume, handle ->
          handle(recipe.addMaterialPersec((consume as ConsumeLiquid).liquid.getWrap(), consume.amount)
            .setOptional(consume.optional))
        })
      registerVanillaConsParser(
        { c -> c is ConsumeLiquidFilter },
        { recipe, consume, handle ->
          consume as ConsumeLiquidFilter
          val consumeGroup = RecipeItemGroup()
          for (liquid in Vars.content.liquids().select { i -> consume.filter[i] }) {
            val eff = consume.liquidEfficiencyMultiplier(liquid)
            handle(
              recipe.addMaterialPersec(liquid.getWrap(), consume.amount)
                .setOptional(consume.optional)
                .setEff(eff)
                .boostAndConsFormat(eff)
                .setGroup(consumeGroup)
            )
          }
        })

      //payloads
      registerVanillaConsParser(
        { c -> c is ConsumePayloads },
        { recipe, consume, handle ->
          for (stack in (consume as ConsumePayloads).payloads) {
            if (stack.amount > 1) handle(recipe.addMaterialInteger(stack.item.getWrap(), stack.amount))
            else handle(
              recipe.addMaterialInteger(stack.item.getWrap(), 1)
                .setOptional(consume.optional)
            )
          }
        })

      //power
      registerVanillaConsParser(
        { c -> c is ConsumePower },
        { recipe, consume, handle ->
          handle(
            recipe.addMaterialPersec(PowerMark, (consume as ConsumePower).usage)
              .setType(RecipeItemType.POWER)
              .setOptional(consume.optional)
          )
        })
    }
  }
}
