package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.power.ConsumeGenerator
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.PowerMark

open class ConsGeneratorParser : ConsumerParser<ConsumeGenerator>() {
  init {
    excludes.add(GeneratorParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is ConsumeGenerator
  }

  override fun parse(content: ConsumeGenerator): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = content.getWrap()
    )

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)

    if (content.outputLiquid != null) {
      res.addProductionPersec(content.outputLiquid.liquid.getWrap(), content.outputLiquid.amount)
    }

    return Seq.with(res)
  }
}
