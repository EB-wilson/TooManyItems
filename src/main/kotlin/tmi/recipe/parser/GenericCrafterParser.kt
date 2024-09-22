package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.production.GenericCrafter
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

open class GenericCrafterParser : ConsumerParser<GenericCrafter>() {
  override fun isTarget(content: Block): Boolean {
    return content is GenericCrafter
  }

  override fun parse(content: GenericCrafter): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.factory,
      ownerBlock = getWrap(content),
      craftTime = content.craftTime
    )

    registerCons(res, *content.consumers)

    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProductionInteger(getWrap(content.outputItem.item), content.outputItem.amount)
    }
    else {
      for (item in content.outputItems) {
        res.addProductionInteger(getWrap(item.item), item.amount)
      }
    }

    if (content.outputLiquids == null) {
      if (content.outputLiquid != null) res.addProductionPersec(
        getWrap(content.outputLiquid.liquid),
        content.outputLiquid.amount
      )
    }
    else {
      for (liquid in content.outputLiquids) {
        res.addProductionPersec(getWrap(liquid.liquid), liquid.amount)
      }
    }

    return Seq.with(res)
  }
}
