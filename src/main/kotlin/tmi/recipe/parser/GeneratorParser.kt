package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.power.PowerGenerator
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser.Companion.getWrap
import tmi.recipe.RecipeType
import tmi.recipe.types.PowerMark

open class GeneratorParser : ConsumerParser<PowerGenerator>() {
  override fun isTarget(content: Block): Boolean {
    return content is PowerGenerator
  }

  override fun parse(content: PowerGenerator): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = content.getWrap()
    )

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)

    return Seq.with(res)
  }
}
