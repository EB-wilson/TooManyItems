package tmi.recipe.parser

import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.Fracker
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType

open class FrackerParser : ConsumerParser<Fracker>() {
  init {
    excludes.add(PumpParser::class.java)
    excludes.add(SolidPumpParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is Fracker
  }

  override fun parse(content: Fracker): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.collecting,
      ownerBlock = content.getWrap(),
      craftTime = content.itemUseTime,
    ).setBaseEff(content.baseEfficiency)

    res.addProductionPersec(content.result.getWrap(), content.pumpAmount)

    registerCons(res, *content.consumers)

    val attrGroup = RecipeItemGroup()
    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      val eff = block.attributes[content.attribute]
      res.addMaterial(block.getWrap(), (content.size*content.size) as Number)
        .setOptional(content.baseEfficiency > 0.001f)
        .setEff(eff)
        .setType(RecipeItemType.ATTRIBUTE)
        .efficiencyFormat(content.baseEfficiency, eff)
        .setGroup(attrGroup)
    }

    return Seq.with(res)
  }
}
