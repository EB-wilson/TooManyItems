package tmi.recipe.parser

import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.SolidPump
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.RecipeParser
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType

open class SolidPumpParser : ConsumerParser<SolidPump>() {
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(PumpParser::class.java)

  override fun isTarget(content: Block): Boolean {
    return content is SolidPump
  }

  override fun parse(content: SolidPump): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.collecting,
      ownerBlock = content.getWrap(),
      craftTime = content.consumeTime
    ).setBaseEff(content.baseEfficiency)

    res.addProductionPersec(content.result.getWrap(), content.pumpAmount)

    registerCons(res, *content.consumers)

    val attrGroup = RecipeItemGroup()
    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      val eff = block.attributes[content.attribute]
      res.addMaterial(block.getWrap(), (content.size*content.size) as Number)
        .setOptional(content.baseEfficiency > 0.001f)
        .setEfficiency(eff)
        .setType(RecipeItemType.ATTRIBUTE)
        .efficiencyFormat(content.baseEfficiency, eff)
        .setGroup(attrGroup)
    }

    return Seq.with(res)
  }
}
