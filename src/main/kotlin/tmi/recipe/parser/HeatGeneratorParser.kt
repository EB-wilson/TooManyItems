package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.power.HeaterGenerator
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark
import tmi.recipe.types.PowerMark

open class HeatGeneratorParser : ConsumerParser<HeaterGenerator>() {
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(
    GeneratorParser::class.java,
    ConsumeGeneratorParser::class.java
  )

  override fun isTarget(content: Block): Boolean {
    return content is HeaterGenerator
  }

  override fun parse(content: HeaterGenerator): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = content.getWrap(),
      craftTime = content.itemDuration,
    )

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)
      .setType(RecipeItemType.POWER)

    if (content.heatOutput > 0) {
      res.addProduction(HeatMark, content.heatOutput as Number)
        .setType(RecipeItemType.POWER)
        .floatFormat()
    }

    if (content.outputLiquid != null) {
      res.addProductionPersec(content.outputLiquid.liquid.getWrap(), content.outputLiquid.amount)
        .also { if(content.explodeOnFull) it.setType(RecipeItemType.GARBAGE) }
    }

    return Seq.with(res)
  }
}
