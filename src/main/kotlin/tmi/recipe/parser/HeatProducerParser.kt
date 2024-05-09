package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.heat.HeatProducer
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark

class HeatProducerParser : ConsumerParser<HeatProducer>() {
  init {
    excludes.add(GenericCrafterParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is HeatProducer
  }

  override fun parse(content: HeatProducer): Seq<Recipe> {
    val res = Recipe(RecipeType.factory)
      .setBlock(getWrap(content))
      .setTime(content.craftTime)

    registerCons(res, *content.consumers)

    res.addProductionRaw(HeatMark, content.heatOutput).setFloatFormat()

    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProduction(getWrap(content.outputItem.item), content.outputItem.amount)
    }
    else {
      for (item in content.outputItems) {
        res.addProduction(getWrap(item.item), item.amount)
      }
    }

    if (content.outputLiquids == null) {
      if (content.outputLiquid != null) res.addProductionPersec(
        getWrap(content.outputLiquid.liquid),
        content.outputLiquid.amount
      )
    }
    else {
      for (liquid in content.outputLiquids) {
        res.addProductionPersec(getWrap(liquid.liquid), liquid.amount)
      }
    }

    return Seq.with(res)
  }
}
