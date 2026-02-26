package tmi.recipe.parser

import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.power.ThermalGenerator
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.RecipeParser
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.recipe.types.PowerMark

open class ThermalGeneratorParser : ConsumerParser<ThermalGenerator>() {
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(GeneratorParser::class.java)

  override fun isTarget(content: Block): Boolean {
    return content is ThermalGenerator
  }

  override fun parse(content: ThermalGenerator): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = content.getWrap()
    ).setBaseEff(0f)

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction/content.displayEfficiencyScale)
      .setType(RecipeItemType.POWER)

    val attrGroup = RecipeItemGroup()
    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0) continue

      val eff = content.size*content.size*block.attributes[content.attribute]
      if (eff <= content.minEfficiency) continue
      res.addMaterial(block.getWrap(), (content.size*content.size) as Number)
        .setEfficiency(eff*content.displayEfficiencyScale)
        .setType(RecipeItemType.ATTRIBUTE)
        .efficiencyFormat(eff*content.displayEfficiencyScale)
        .setGroup(attrGroup)
    }

    if (content.outputLiquid != null) res.addProductionPersec(
      content.outputLiquid.liquid.getWrap(),
      content.outputLiquid.amount/content.displayEfficiencyScale
    )

    return Seq.with(res)
  }
}
