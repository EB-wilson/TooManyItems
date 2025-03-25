package tmi.recipe.parser

import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.payloads.Constructor
import tmi.recipe.Recipe
import tmi.recipe.RecipeParser
import tmi.recipe.RecipeType
import tmi.util.Consts

open class ConstructorParser: RecipeParser<Constructor>() {
  override fun isTarget(content: Block): Boolean {
    return content is Constructor
  }

  override fun parse(content: Constructor): Seq<Recipe> {
    val res = Seq<Recipe>()
    Vars.content.blocks()
      .select { content.canProduce(it) }
      .forEach { b ->
        val recipe = Recipe(
          recipeType = RecipeType.factory,
          ownerBlock = content.getWrap(),
          craftTime = (Consts.buildTimeAlter.get(b) as Float)/content.buildSpeed
        )

        recipe.addProductionInteger(b.getWrap(), 1)

        b.requirements.forEach { stack ->
          recipe.addMaterialInteger(stack.item.getWrap(), stack.amount)
        }

        res.add(recipe)
      }

    return res
  }
}