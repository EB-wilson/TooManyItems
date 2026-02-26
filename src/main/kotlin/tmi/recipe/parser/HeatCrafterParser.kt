package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.production.HeatCrafter
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.recipe.types.HeatMark

open class HeatCrafterParser : ConsumerParser<HeatCrafter>() {
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(GenericCrafterParser::class.java)

  override fun isTarget(content: Block): Boolean {
    return content is HeatCrafter
  }

  override fun parse(content: HeatCrafter): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.factory,
      ownerBlock = content.getWrap(),
      craftTime = content.craftTime,
    )

    res.addMaterial(HeatMark, content.heatRequirement as Number)
      .setType(RecipeItemType.POWER)
      .floatFormat()

    registerCons(res, *content.consumers)

    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProductionInteger(
        content.outputItem.item.getWrap(), content.outputItem.amount
      )
    }
    else {
      for (item in content.outputItems) {
        res.addProductionInteger(item.item.getWrap(), item.amount)
      }
    }

    if (content.outputLiquids == null) {
      if (content.outputLiquid != null) res.addProductionPersec(
        content.outputLiquid.liquid.getWrap(), content.outputLiquid.amount
      )
    }
    else {
      for (liquid in content.outputLiquids) {
        res.addProductionPersec(liquid.liquid.getWrap(), liquid.amount)
      }
    }

    return Seq.with(res)
  }
}
