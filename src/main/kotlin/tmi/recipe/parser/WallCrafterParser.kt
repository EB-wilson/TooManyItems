package tmi.recipe.parser

import arc.math.Mathf
import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.WallCrafter
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

class WallCrafterParser : ConsumerParser<WallCrafter>() {
  override fun isTarget(content: Block): Boolean {
    return content is WallCrafter
  }

  override fun parse(content: WallCrafter): Seq<Recipe> {
    val res = Recipe(RecipeType.collecting)
      .setBlock(getWrap(content))
      .setTime(content.drillTime)

    res.addProduction(getWrap(content.output), 1)

    registerCons(res, *content.consumers)

    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      val eff = block.attributes[content.attribute]
      res.addMaterialRaw(getWrap(block), block.size.toFloat())
        .setEfficiency(eff)
        .setAttribute()
        .setFormat { "[#98ffa9]" + Mathf.round(eff*100) + "%" }
    }

    return Seq.with(res)
  }
}
