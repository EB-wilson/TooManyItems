package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.heat.HeatProducer
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark

open class HeatProducerParser : ConsumerParser<HeatProducer>() {
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(GenericCrafterParser::class.java)

  override fun isTarget(content: Block): Boolean {
    return content is HeatProducer
  }

  override fun parse(content: HeatProducer): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = content.getWrap(),
      craftTime = content.craftTime,
    )

    registerCons(res, *content.consumers)

    res.addProduction(HeatMark, content.heatOutput as Number)
      .setType(RecipeItemType.POWER)
      .floatFormat()

    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProductionInteger(content.outputItem.item.getWrap(), content.outputItem.amount)
    }
    else {
      for (item in content.outputItems) {
        res.addProductionInteger(item.item.getWrap(), item.amount)
      }
    }

    if (content.outputLiquids == null) {
      if (content.outputLiquid != null) res.addProductionPersec(
        content.outputLiquid.liquid.getWrap(),
        content.outputLiquid.amount
      )
    }
    else {
      for (liquid in content.outputLiquids) {
        res.addProductionPersec(liquid.liquid.getWrap(), liquid.amount)
      }
    }

    return Seq.with(res)
  }
}
