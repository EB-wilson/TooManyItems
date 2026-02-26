package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.power.VariableReactor
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark
import tmi.recipe.types.PowerMark

open class VariableReactorParser : ConsumerParser<VariableReactor>() {
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(GeneratorParser::class.java)

  override fun isTarget(content: Block): Boolean {
    return content is VariableReactor
  }

  override fun parse(content: VariableReactor): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = content.getWrap()
    )

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)
      .setType(RecipeItemType.POWER)

    if (content.maxHeat > 0) {
      res.addProduction(HeatMark, content.maxHeat as Number)
        .setType(RecipeItemType.POWER)
        .floatFormat()
    }

    return Seq.with(res)
  }
}
