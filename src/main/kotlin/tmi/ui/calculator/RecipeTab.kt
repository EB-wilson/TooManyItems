package tmi.ui.calculator

import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.ui.layout.Table
import arc.util.Align
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItem
import tmi.ui.RecipeView

interface RecipeGraphElement {
  val node: RecipeGraphNode
  val nodeWidth: Float
  val nodeHeight: Float
  var nodeX: Float
  var nodeY: Float

  fun outputOffset(item: RecipeItem<*>): Vec2
  fun inputOffset(item: RecipeItem<*>): Vec2

  class RecipeTab(
    val recipe: Recipe,
    override val node: RecipeGraphNode = RecipeGraphNode(recipe)
  ): Table(), RecipeGraphElement {
    val recipeView = RecipeView(recipe, true)

    override val nodeWidth: Float get() = width
    override val nodeHeight: Float get() = height
    override var nodeX: Float
      get() = x
      set(value) { x = value }
    override var nodeY: Float
      get() = y
      set(value) { y = value }

    init {
      build()
    }

    override fun outputOffset(item: RecipeItem<*>): Vec2 =
      recipeView.getOutputNode(item)?.let {
        Vec2(it.getX(Align.center), it.getY(Align.center))
      }?: Vec2()
    override fun inputOffset(item: RecipeItem<*>): Vec2 =
      recipeView.getInputNode(item)?.let {
        Vec2(it.getX(Align.center), it.getY(Align.center))
      }?: Vec2()

    private fun build() {
      add(recipeView).fill()
      recipeView.validate()
      recipeView.pack()

      pack()
    }
  }

  class LineMark(
    override val node: RecipeGraphNode
  ): RecipeGraphElement {
    override val nodeWidth: Float get() = 0f
    override val nodeHeight: Float get() = 0f
    override var nodeX: Float = 0f
    override var nodeY: Float = 0f

    override fun outputOffset(item: RecipeItem<*>) = Vec2()
    override fun inputOffset(item: RecipeItem<*>) = Vec2()
  }
}
