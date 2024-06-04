package tmi.recipe.parser

import arc.math.Mathf
import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.AttributeCrafter
import tmi.recipe.Recipe
import tmi.recipe.Recipe.Companion.getDefaultEff
import tmi.recipe.RecipeType
import kotlin.math.min

class AttributeCrafterParser : ConsumerParser<AttributeCrafter>() {
  init {
    excludes.add(GenericCrafterParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is AttributeCrafter
  }

  override fun parse(content: AttributeCrafter): Seq<Recipe> {
    val res = Recipe(RecipeType.factory)
      .setEff(getDefaultEff(content.baseEfficiency))
      .setBlock(getWrap(content))
      .setTime(content.craftTime)

    registerCons(res, *content.consumers)

    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      val eff = min(
        (content.boostScale*content.size*content.size*block.attributes[content.attribute]),
        content.maxBoost
      )

      res.addMaterialRaw(getWrap(block), (content.size*content.size).toFloat())
        .setAttribute()
        .setOptional(content.baseEfficiency > 0.001f)
        .setEff(eff)
        .setFormat { "[#98ffa9]" + (if (content.baseEfficiency > 0.001f) "+" else "") + Mathf.round(eff*100) + "%" }
    }

    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProduction(getWrap(content.outputItem.item), content.outputItem.amount).setAltPersecFormat()
    }
    else {
      for (item in content.outputItems) {
        res.addProduction(getWrap(item.item), item.amount).setAltPersecFormat()
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
