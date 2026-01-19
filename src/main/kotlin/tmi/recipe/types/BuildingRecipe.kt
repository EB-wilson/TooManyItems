package tmi.recipe.types

import arc.Core
import arc.scene.ui.layout.Table
import mindustry.gen.Icon
import mindustry.world.Block
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.ui.CellType
import tmi.ui.RecipeView
import tmi.util.ifInst

class BuildingRecipe : RecipeType() {
  override fun RecipeView.BuilderScope.buildRecipeView(view: Table, recipe: Recipe) {
    view.table { mats ->
      recipe.materials.forEachIndexed { i, mat ->
        if (i > 0 && i % 4 == 0) {
          mats.row()
        }

        mats.itemCell(CellType.MATERIAL, mat).size(80f).pad(6f)
      }
    }
    view.table { build ->
      build.table { img ->
        img.image(Icon.hammer).size(80f).padTop(48f)
        img.row()
        img.timeTab().pad(4f)
      }.pad(12f)
      build.itemCell(CellType.BLOCK, RecipeItemStack(recipe.ownerBlock!!, 1f)).size(160f)

      recipe.ownerBlock.ifInst<Block> { b ->
        build.row()
        build.table { detail ->
          detail.left()
          detail.add("${Core.bundle["misc.blockSize"]} ${b.size}x${b.size}")
        }.colspan(1)
      }
    }
  }
}
