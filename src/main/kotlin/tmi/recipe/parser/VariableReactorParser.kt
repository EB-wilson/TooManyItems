package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.power.VariableReactor
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark
import tmi.recipe.types.PowerMark

class VariableReactorParser : ConsumerParser<VariableReactor>() {
  init {
    excludes.add(GeneratorParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is VariableReactor
  }

  override fun parse(content: VariableReactor): Seq<Recipe> {
    val res = Recipe(RecipeType.generator)
      .setBlock(getWrap(content))

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)

    if (content.maxHeat > 0) {
      res.addProductionRaw(HeatMark, content.maxHeat).setFloatFormat()
    }

    return Seq.with(res)
  }
}
