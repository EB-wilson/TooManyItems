package tmi.recipe.parser

import arc.struct.Seq
import mindustry.type.Item
import mindustry.world.Block
import mindustry.world.blocks.power.ConsumeGenerator
import mindustry.world.blocks.power.ImpactReactor
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.recipe.types.PowerMark
import tmi.util.ifInst

open class ImpactReactorParser : ConsumerParser<ImpactReactor>() {
  init {
    excludes.add(GeneratorParser::class.java)
  }

  override fun isTarget(content: Block): Boolean {
    return content is ImpactReactor
  }

  override fun parse(content: ImpactReactor): Seq<Recipe> {
    val res = Recipe(
      recipeType = RecipeType.generator,
      ownerBlock = content.getWrap(),
      craftTime = content.itemDuration
    )

    registerCons(res, *content.consumers)

    res.addProductionPersec(PowerMark, content.powerProduction)
      .setType(RecipeItemType.POWER)

    return Seq.with(res)
  }
}
