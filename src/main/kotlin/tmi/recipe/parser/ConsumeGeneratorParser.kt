package tmi.recipe.parser

import arc.struct.Seq
import mindustry.type.Item
import mindustry.world.Block
import mindustry.world.blocks.power.ConsumeGenerator
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.recipe.types.PowerMark
import tmi.util.ifInst

open class ConsumeGeneratorParser : ConsumerParser<ConsumeGenerator>() {
  init {
    excludes.add(GeneratorParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is ConsumeGenerator
  }

  override fun parse(content: ConsumeGenerator): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = content.getWrap(),
      craftTime = content.itemDuration
    )

    val mult = content.itemDurationMultipliers
    registerCons(res, { s ->
      s.item.item?.ifInst<Item> { item ->
        val m = mult.get(item, 1f)
        s.amount /= m
      }
    }, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)
      .setType(RecipeItemType.POWER)

    return Seq.with(res)
  }
}
