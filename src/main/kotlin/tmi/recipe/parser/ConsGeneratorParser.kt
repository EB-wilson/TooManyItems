package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.power.ConsumeGenerator
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.PowerMark

class ConsGeneratorParser : ConsumerParser<ConsumeGenerator>() {
  init {
    excludes.add(GeneratorParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is ConsumeGenerator
  }

  override fun parse(content: ConsumeGenerator): Seq<Recipe> {
    val res = Recipe(RecipeType.generator)
      .setBlock(getWrap(content))
      .setTime(content.itemDuration)

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)

    if (content.outputLiquid != null) {
      res.addProductionPersec(getWrap(content.outputLiquid.liquid), content.outputLiquid.amount)
    }

    return Seq.with(res)
  }
}
