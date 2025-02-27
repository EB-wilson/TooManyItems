package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import mindustry.world.blocks.units.UnitFactory
import tmi.recipe.Recipe
import tmi.recipe.RecipeType

open class UnitFactoryParser : ConsumerParser<UnitFactory>() {
  override fun isTarget(content: Block): Boolean {
    return content is UnitFactory
  }

  override fun parse(content: UnitFactory): Seq<Recipe> {
    val res = Seq<Recipe>()

    for (plan in content.plans) {
      val recipe = Recipe(
        recipeType = RecipeType.factory,
        ownerBlock = +content,
        craftTime = plan.time
      )

      recipe.addProductionInteger(+plan.unit, 1)

      for (stack in plan.requirements) {
        recipe.addMaterialInteger(+stack.item, stack.amount)
      }

      registerCons(recipe, *content.consumers)

      res.add(recipe)
    }

    return res
  }
}
