package tmi.recipe

import arc.Core
import arc.scene.style.Drawable
import arc.scene.ui.layout.Table
import arc.struct.Seq
import tmi.recipe.types.*
import tmi.ui.RecipeView

/**配方表类型，用于描述一个配方如何被显示或者计算等 */
abstract class RecipeType {
  companion object {
    val all = Seq<RecipeType>()

    @JvmStatic
    val factory = FactoryRecipe()
    @JvmStatic
    val building = BuildingRecipe()
    @JvmStatic
    val collecting = CollectingRecipe()
    @JvmStatic
    val generator = GeneratorRecipe()
    @JvmStatic
    val ammo = AmmoRecipe()
  }

  init {
    all.add(this@RecipeType)
  }

  abstract val name: String
  abstract val icon: Drawable

  /**此类型的ID，必须是唯一的，此类型的所有实例共用此id*/
  open val id: Int get() = name.hashCode()
  open val localizedName: String get() = Core.bundle["recipeType.$name.name"]

  /**构建配方视图的布局空间*/
  abstract fun RecipeView.BuilderScope.buildRecipeView(view: Table, recipe: Recipe)

  final override fun hashCode() = id

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    return true
  }
}
