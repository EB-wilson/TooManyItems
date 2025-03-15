package tmi.recipe.parser

import arc.math.Mathf
import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.SolidPump
import tmi.recipe.Recipe
import tmi.recipe.Recipe.Companion.getDefaultEff
import tmi.recipe.RecipeType

open class SolidPumpParser : ConsumerParser<SolidPump>() {
  init {
    excludes.add(PumpParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is SolidPump
  }

  override fun parse(content: SolidPump): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.collecting,
      ownerBlock = content.getWrap(),
      craftTime = content.consumeTime
    ).setEff(getDefaultEff(content.baseEfficiency))

    res.addProductionPersec(content.result.getWrap(), content.pumpAmount)

    registerCons(res, *content.consumers)

    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      val eff = block.attributes[content.attribute]
      res.addMaterial(block.getWrap(), (content.size*content.size) as Number)
        .setOptional(content.baseEfficiency > 0.001f)
        .setEff(eff)
        .setAttribute()
        .setFormat { "[#98ffa9]" + (if (content.baseEfficiency > 0.001f) "+" else "") + Mathf.round(eff*100) + "%" }
    }

    return Seq.with(res)
  }
}
