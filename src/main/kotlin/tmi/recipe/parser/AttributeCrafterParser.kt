package tmi.recipe.parser

import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.AttributeCrafter
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import kotlin.math.min

open class AttributeCrafterParser : ConsumerParser<AttributeCrafter>() {
  init {
    excludes.add(GenericCrafterParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is AttributeCrafter
  }

  override fun parse(content: AttributeCrafter): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.factory,
      ownerBlock = content.getWrap(),
      craftTime = content.craftTime,
    ).setBaseEff(content.baseEfficiency)

    registerCons(res, *content.consumers)

    val attrGroup = RecipeItemGroup()
    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      val eff = min(
        (content.boostScale*content.size*content.size*block.attributes[content.attribute]),
        content.maxBoost
      )

      res.addMaterial(block.getWrap(), (content.size*content.size) as Number)
        .setType(RecipeItemType.ATTRIBUTE)
        .setOptional(content.baseEfficiency > 0.001f)
        .setEff(eff)
        .efficiencyFormat(content.baseEfficiency, eff)
        .setGroup(attrGroup)
    }

    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProductionInteger(content.outputItem.item.getWrap(), content.outputItem.amount)
    }
    else {
      for (item in content.outputItems) {
        res.addProductionInteger(item.item.getWrap(), item.amount)
      }
    }

    if (content.outputLiquids == null) {
      if (content.outputLiquid != null) res.addProductionPersec(
        content.outputLiquid.liquid.getWrap(),
        content.outputLiquid.amount
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
