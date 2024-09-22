package tmi.recipe.parser

import arc.math.Mathf
import arc.struct.Seq
import arc.util.Strings
import mindustry.world.Block
import mindustry.world.blocks.production.*
import mindustry.world.meta.StatUnit
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

open class SeparatorParser : ConsumerParser<Separator>() {
  override fun isTarget(content: Block): Boolean {
    return content is Separator
  }

  override fun parse(content: Separator): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.factory,
      ownerBlock = getWrap(content),
      craftTime = content.craftTime
    )

    registerCons(res, *content.consumers)

    var n = 0f
    for (stack in content.results) {
      n += stack.amount.toFloat()
    }
    for (item in content.results) {
      res.addProduction(getWrap(item.item), (item.amount/n/content.craftTime) as Number)
        .setFormat { f -> Mathf.round(f*100*res.craftTime).toString() + "%" }
        .setAltFormat { f -> Strings.autoFixed(f*60, 1) + StatUnit.perSecond.localized() }
    }

    return Seq.with(res)
  }
}
