package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.units.UnitAssembler
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

open class UnitAssemblerParser : ConsumerParser<UnitAssembler>() {
  override fun isTarget(content: Block): Boolean {
    return content is UnitAssembler
  }

  override fun parse(content: UnitAssembler): Seq<Recipe> {
    val res = Seq<Recipe>()

    for (plan in content.plans) {
      val recipe = Recipe(
        recipeType = RecipeType.factory,
        ownerBlock = content.getWrap(),
        craftTime = plan.time
      )

      recipe.addProductionInteger(plan.unit.getWrap(), 1)

      for (stack in plan.requirements) {
        recipe.addMaterialInteger(stack.item.getWrap(), stack.amount)
      }

      registerCons(recipe, *content.consumers)

      res.add(recipe)
    }

    return res
  }
}
