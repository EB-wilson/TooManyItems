package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.units.UnitAssembler
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

class UnitAssemblerParser : ConsumerParser<UnitAssembler>() {
  override fun isTarget(content: Block): Boolean {
    return content is UnitAssembler
  }

  override fun parse(content: UnitAssembler): Seq<Recipe> {
    val res = Seq<Recipe>()

    for (plan in content.plans) {
      val recipe = Recipe(RecipeType.factory)
        .setBlock(getWrap(content))
        .setTime(plan.time)

      recipe.addProduction(getWrap(plan.unit), 1).setAltPersecFormat()

      for (stack in plan.requirements) {
        recipe.addMaterial(getWrap(stack.item), stack.amount).setAltPersecFormat()
      }

      registerCons(recipe, *content.consumers)

      res.add(recipe)
    }

    return res
  }
}
