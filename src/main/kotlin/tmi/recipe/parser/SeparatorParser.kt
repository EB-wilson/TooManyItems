package tmi.recipe.parser

import arc.math.Mathf
import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.production.*
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

class SeparatorParser : ConsumerParser<Separator>() {
  override fun isTarget(content: Block): Boolean {
    return content is Separator
  }

  override fun parse(content: Separator): Seq<Recipe> {
    val res = Recipe(RecipeType.factory)
      .setBlock(getWrap(content))
      .setTime(content.craftTime)

    registerCons(res, *content.consumers)

    var n = 0f
    for (stack in content.results) {
      n += stack.amount.toFloat()
    }
    val fn = n
    for (item in content.results) {
      res.addProduction(getWrap(item.item), item.amount/n)
        .setFormat { Mathf.round(item.amount/fn*100).toString() + "%" }
    }

    return Seq.with(res)
  }
}
