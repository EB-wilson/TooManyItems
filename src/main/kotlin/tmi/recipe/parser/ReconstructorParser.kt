package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.units.Reconstructor
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

class ReconstructorParser : ConsumerParser<Reconstructor>() {
  override fun isTarget(content: Block): Boolean {
    return content is Reconstructor
  }

  override fun parse(content: Reconstructor): Seq<Recipe> {
    val res = Seq<Recipe>()
    for (upgrade in content.upgrades) {
      val recipe = Recipe(RecipeType.factory)
        .setBlock(getWrap(content))
        .setTime(content.constructTime)

      recipe.addMaterial(getWrap(upgrade[0]), 1)
      recipe.addProduction(getWrap(upgrade[1]), 1)

      registerCons(recipe, *content.consumers)

      res.add(recipe)
    }

    return res
  }
}
