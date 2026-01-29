package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import tmi.TooManyItems
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser
import tmi.recipe.RecipeType
import tmi.util.Consts

class BuildingParser: RecipeParser<Block>() {
  override fun isTarget(content: Block): Boolean = content.requirements.isNotEmpty() && content.placeablePlayer

  override fun parse(content: Block): Seq<Recipe> {
    val recipe = Recipe(
      RecipeType.building,
      TooManyItems.itemsManager.getItem(content),
      Consts.buildTimeAlter.get(content) as Float
    )

    for (stack in content.requirements) {
      recipe.addMaterialInteger(TooManyItems.itemsManager.getItem(stack.item), stack.amount)
    }

    return Seq.with(recipe)
  }
}