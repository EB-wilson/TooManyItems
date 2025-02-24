package tmi.recipe.parser

import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.payloads.Constructor
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser
import tmi.recipe.RecipeType

open class ConstructorParser: RecipeParser<Constructor>() {
  override fun isTarget(content: Block): Boolean {
    return content is Constructor
  }

  override fun parse(content: Constructor): Seq<Recipe> {
    val res = Seq<Recipe>()
    Vars.content.blocks()
      .filter { content.canProduce(it) }
      .forEach { b ->
        val recipe = Recipe(
          recipeType = RecipeType.factory,
          ownerBlock = +content,
          craftTime = b.buildTime / content.buildSpeed
        )

        recipe.addProductionInteger(+b, 1)

        b.requirements.forEach { stack ->
          recipe.addMaterialInteger(+stack.item, stack.amount)
        }

        res.add(recipe)
      }

    return res
  }
}