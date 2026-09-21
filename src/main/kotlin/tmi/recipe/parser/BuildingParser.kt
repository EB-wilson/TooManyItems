package tmi.recipe.parser

import arc.struct.Seq
import mindustry.world.Block
import tmi.TooManyItems
import tmi.recipe.AmountFormatter
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeParser
import tmi.recipe.RecipeType
import tmi.util.Consts
import kotlin.math.roundToInt

class BuildingParser: RecipeParser<Block>() {
  override fun isTarget(content: Block): Boolean = content.requirements.isNotEmpty() && content.placeablePlayer

  override fun parse(content: Block): Seq<Recipe> {
    val recipe = Recipe(
      RecipeType.building,
      TooManyItems.itemsManager.getItem(content),
      Consts.buildTimeAlter.get(content) as Float
    )

    for (stack in content.requirements) {
      recipe.addMaterial(TooManyItems.itemsManager.getItem(stack.item), stack.amount)
        .setFormat(AmountFormatter.integerFormatter())
    }

    return Seq.with(recipe)
  }
}