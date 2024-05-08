package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.power.HeaterGenerator
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark
import tmi.recipe.types.PowerMark

class HeatGeneratorParser : ConsumerParser<HeaterGenerator>() {
  init {
    excludes.add(GeneratorParser::class.java)
    excludes.add(ConsGeneratorParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is HeaterGenerator
  }

  override fun parse(content: HeaterGenerator): Seq<Recipe> {
    val res = Recipe(RecipeType.generator)
      .setBlock(getWrap(content))
      .setTime(content.itemDuration)

    registerCons(res, *content.consumers)

    res.addProductionPresec(PowerMark.INSTANCE, content.powerProduction)

    if (content.heatOutput > 0) {
      res.addProductionRaw(HeatMark.INSTANCE, content.heatOutput).setFloatFormat()
    }

    if (content.outputLiquid != null) {
      res.addProductionPresec(getWrap(content.outputLiquid.liquid), content.outputLiquid.amount)
    }

    return Seq.with(res)
  }
}
