package tmi.recipe.parser

import arc.math.Mathf
import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.Fracker
import tmi.recipe.Recipe
import tmi.recipe.Recipe.Companion.getDefaultEff
import tmi.recipe.RecipeType

class FrackerParser : ConsumerParser<Fracker>() {
  init {
    excludes.add(PumpParser::class.java)
    excludes.add(SolidPumpParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is Fracker
  }

  override fun parse(content: Fracker): Seq<Recipe> {
    val res = Recipe(RecipeType.collecting)
      .setEfficiency(getDefaultEff(content.baseEfficiency))
      .setBlock(getWrap(content))
      .setTime(content.consumeTime)

    res.addProductionPresec(getWrap(content.result), content.pumpAmount)

    registerCons(res, *content.consumers)

    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      val eff = block.attributes[content.attribute]
      res.addMaterialRaw(getWrap(block), (content.size*content.size).toFloat())
        .setOptionalCons(content.baseEfficiency > 0.001f)
        .setEfficiency(eff)
        .setAttribute()
        .setFormat { "[#98ffa9]" + (if (content.baseEfficiency > 0.001f) "+" else "") + Mathf.round(eff*100) + "%" }
    }

    return Seq.with(res)
  }
}
