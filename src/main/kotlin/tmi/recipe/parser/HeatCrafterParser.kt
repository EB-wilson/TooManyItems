package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.production.HeatCrafter
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark

open class HeatCrafterParser : ConsumerParser<HeatCrafter>() {
  init {
    excludes.add(GenericCrafterParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is HeatCrafter
  }

  override fun parse(content: HeatCrafter): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.factory,
      ownerBlock = +content,
      craftTime = content.craftTime,
    )

    res.addMaterial(HeatMark, content.heatRequirement as Number).floatFormat()

    registerCons(res, *content.consumers)

    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProductionInteger(
        +content.outputItem.item, content.outputItem.amount
      )
    }
    else {
      for (item in content.outputItems) {
        res.addProductionInteger(+item.item, item.amount)
      }
    }

    if (content.outputLiquids == null) {
      if (content.outputLiquid != null) res.addProductionPersec(
        +content.outputLiquid.liquid, content.outputLiquid.amount
      )
    }
    else {
      for (liquid in content.outputLiquids) {
        res.addProductionPersec(+liquid.liquid, liquid.amount)
      }
    }

    return Seq.with(res)
  }
}
