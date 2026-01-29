package tmi.recipe.parser

import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.WallCrafter
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType

open class WallCrafterParser : ConsumerParser<WallCrafter>() {
  override fun isTarget(content: Block): Boolean {
    return content is WallCrafter
  }

  override fun parse(content: WallCrafter): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.collecting,
      ownerBlock = content.getWrap(),
      craftTime = content.drillTime
    ).setBaseEff(0f)

    res.addProductionInteger(content.output.getWrap(), 1)

    val itemCons = content.itemConsumer
    registerCons(res, { c, s ->
      if (c == itemCons){
        s.amount = s.amount*content.drillTime/content.boostItemUseTime
        s.integerFormat(content.boostItemUseTime)
      }
    }, *content.consumers)

    val attrGroup = RecipeItemGroup()
    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      val eff = block.attributes[content.attribute]
      res.addMaterial(block.getWrap(), block.size as Number)
        .setEff(eff)
        .setType(RecipeItemType.ATTRIBUTE)
        .efficiencyFormat(eff)
        .setGroup(attrGroup)
    }

    return Seq.with(res)
  }
}
