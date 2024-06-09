package tmi.recipe.parser

import arc.math.Mathf
import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.power.ThermalGenerator
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.PowerMark

class ThermalGeneratorParser : ConsumerParser<ThermalGenerator>() {
  init {
    excludes.add(GeneratorParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is ThermalGenerator
  }

  override fun parse(content: ThermalGenerator): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = getWrap(content)
    ).setEff(Recipe.zeroEff)

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)

    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0) continue

      val eff = content.displayEfficiencyScale*content.size*content.size*block.attributes[content.attribute]
      if (eff <= content.minEfficiency) continue
      res.addMaterial(getWrap(block), (content.size*content.size) as Number)
        .setEff(eff)
        .setAttribute()
        .setFormat { "[#98ffa9]" + Mathf.round(eff*100) + "%" }
    }

    if (content.outputLiquid != null) res.addProductionPersec(
      getWrap(content.outputLiquid.liquid),
      content.outputLiquid.amount
    )

    return Seq.with(res)
  }
}
