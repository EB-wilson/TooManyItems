package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.production.GenericCrafter
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

class GenericCrafterParser : ConsumerParser<GenericCrafter>() {
  override fun isTarget(content: Block): Boolean {
    return content is GenericCrafter
  }

  override fun parse(content: GenericCrafter): Seq<Recipe> {
    val res = Recipe(RecipeType.factory)
      .setBlock(getWrap(content))
      .setTime(content.craftTime)

    registerCons(res, *content.consumers)

    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProduction(getWrap(content.outputItem.item), content.outputItem.amount)
    }
    else {
      for (item in content.outputItems) {
        res.addProduction(getWrap(item.item), item.amount)
      }
    }

    if (content.outputLiquids == null) {
      if (content.outputLiquid != null) res.addProductionPresec(
        getWrap(content.outputLiquid.liquid),
        content.outputLiquid.amount
      )
    }
    else {
      for (liquid in content.outputLiquids) {
        res.addProductionPresec(getWrap(liquid.liquid), liquid.amount)
      }
    }

    return Seq.with(res)
  }
}
