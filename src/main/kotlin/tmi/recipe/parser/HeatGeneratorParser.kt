package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.power.HeaterGenerator
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark
import tmi.recipe.types.PowerMark

open class HeatGeneratorParser : ConsumerParser<HeaterGenerator>() {
  init {
    excludes.add(GeneratorParser::class.java)
    excludes.add(ConsGeneratorParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is HeaterGenerator
  }

  override fun parse(content: HeaterGenerator): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = getWrap(content),
      craftTime = content.itemDuration,
    )

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)

    if (content.heatOutput > 0) {
      res.addProduction(HeatMark, content.heatOutput as Number).floatFormat()
    }

    if (content.outputLiquid != null) {
      res.addProductionPersec(getWrap(content.outputLiquid.liquid), content.outputLiquid.amount)
    }

    return Seq.with(res)
  }
}
