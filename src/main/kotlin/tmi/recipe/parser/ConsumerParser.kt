package tmi.recipe.parser

import arc.func.Boolf
import arc.func.Cons
import arc.func.Cons3
import arc.struct.ObjectMap
import mindustry.Vars
import mindustry.type.Item
import mindustry.type.Liquid
import mindustry.world.Block
import mindustry.world.consumers.*
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeParser
import tmi.recipe.types.PowerMark

abstract class ConsumerParser<T : Block> : RecipeParser<T>() {
  protected fun registerCons(recipe: Recipe, vararg cons: Consume) {
    registerCons(recipe, {}, *cons)
  }

  protected fun registerCons(recipe: Recipe, handle: Cons<RecipeItemStack>, vararg cons: Consume) {
    for (consume in cons) {
      for (entry in vanillaConsParser) {
        if (entry.key[consume]) entry.value[recipe, consume, handle]
      }
    }
  }

  companion object {
    protected var vanillaConsParser: ObjectMap<Boolf<Consume>, Cons3<Recipe, Consume, Cons<RecipeItemStack>>> =
      ObjectMap()

    fun registerVanillaConsParser(type: Boolf<Consume>, handle: Cons3<Recipe, Consume, Cons<RecipeItemStack>>) {
      vanillaConsParser.put(type, handle)
    }

    fun registerVanillaConsumeParser() {
      //items
      registerVanillaConsParser(
        { c -> c is ConsumeItems },
        { recipe: Recipe, consume: Consume, handle ->
          for (item in (consume as ConsumeItems).items) {
            handle[recipe.addMaterial(getWrap(item.item), item.amount).setOptionalCons(consume.optional)]
          }
        })
      registerVanillaConsParser(
        { c -> c is ConsumeItemFilter },
        { recipe, consume, handle ->
          val cf = (consume as ConsumeItemFilter)
          for (item in Vars.content.items().select { i -> cf.filter[i] }) {
            handle[recipe.addMaterial(getWrap(item), 1)
              .setOptionalCons(consume.optional)
              .setAttribute(cf)
              .setMaxAttr()]
          }
        })

      //liquids
      registerVanillaConsParser(
        { c -> c is ConsumeLiquids },
        { recipe, consume, handle ->
          for (liquid in (consume as ConsumeLiquids).liquids) {
            handle[recipe.addMaterialPresec(getWrap(liquid.liquid), liquid.amount)
              .setOptionalCons(consume.optional)]
          }
        })
      registerVanillaConsParser(
        { c -> c is ConsumeLiquid },
        { recipe, consume, handle ->
          handle[recipe.addMaterialPresec(getWrap((consume as ConsumeLiquid).liquid), consume.amount)
            .setOptionalCons(consume.optional)]
        })
      registerVanillaConsParser(
        { c -> c is ConsumeLiquidFilter },
        { recipe, consume, handle ->
          val cf = (consume as ConsumeLiquidFilter)
          for (liquid in Vars.content.liquids().select { i -> cf.filter[i] }) {
            handle[recipe.addMaterialPresec(getWrap(liquid), cf.amount)
              .setOptionalCons(consume.optional)
              .setAttribute(cf)
              .setMaxAttr()]
          }
        })

      //payloads
      registerVanillaConsParser(
        { c -> c is ConsumePayloads },
        { recipe, consume, handle ->
          for (stack in (consume as ConsumePayloads).payloads) {
            if (stack.amount > 1) handle[recipe.addMaterial(getWrap(stack.item), stack.amount)]
            else handle[recipe.addMaterial(getWrap(stack.item), 1).setOptionalCons(consume.optional)]
          }
        })

      //power
      registerVanillaConsParser(
        { c -> c is ConsumePower },
        { recipe, consume, handle ->
          handle[recipe.addMaterialPresec(PowerMark.INSTANCE, (consume as ConsumePower).usage)
            .setOptionalCons(consume.optional)]
        })
    }
  }
}
