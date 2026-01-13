package tmi.ui.calculator

import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.style.BaseDrawable
import arc.scene.style.Drawable
import arc.scene.ui.Button
import arc.scene.ui.layout.Table
import arc.util.Align
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItem
import tmi.ui.NodeType
import tmi.ui.NodeType.*
import tmi.ui.RecipeView
import tmi.ui.RecipesDialog
import tmi.ui.TmiUI

interface RecipeGraphElement {
  val node: RecipeGraphLayout.Node
  val nodeWidth: Float
  val nodeHeight: Float
  var nodeX: Float
  var nodeY: Float

  fun outputOffset(item: RecipeItem<*>): Vec2
  fun inputOffset(item: RecipeItem<*>): Vec2

  class RecipeTab(
    override val node: RecipeGraphLayout.RecNode,
    val view: CalculatorView
  ): Table(), RecipeGraphElement {
    val graphNode = node.targetNode
    val recipeView = RecipeView(node.recipe, true, { stack, type, mode ->
      if (type == MATERIAL
      && TooManyItems.recipesManager.anyProduction(stack.item)
      && !RecipeType.generator.isPower(stack.item)) {
        if (mode != RecipesDialog.Mode.RECIPE) {
          graphNode.disInput(stack.item)
          view.updateGraph()
        }
        else {
          view.showRecipeSelector(stack.item, graphNode)
        }
      }
      else if (type == BLOCK && mode != RecipesDialog.Mode.RECIPE) {
        graphNode.remove()
        view.updateGraph()
      }
      else {
        TmiUI.recipesDialog.showWith {
          setCurrSelecting(stack.item)
        }
      }
    }) { node ->
      if (node.type == PRODUCTION
      || (node.type == MATERIAL
          && (!TooManyItems.recipesManager.anyProduction(node.stack.item) || RecipeType.generator.isPower(node.stack.item)))){
        node.style = Button.ButtonStyle(Styles.defaultb).also {
          it.up = Tex.buttonDisabled
        }
      }
      else if (node.type == MATERIAL) {
        node.style = Button.ButtonStyle(Styles.defaultb).also {
          it.up = object: BaseDrawable(Tex.buttonDown) {
            private fun setColor() {
              if (graphNode.hasInput(node.stack.item)) Draw.color(Color.darkGray)
              else Draw.color(Pal.accent, Color.darkGray, Mathf.absin(10f, 1f))
            }

            override fun draw(x: Float, y: Float, width: Float, height: Float) {
              setColor()
              Tex.buttonDown.draw(x, y, width, height)
              Draw.color()
            }

            override fun draw(
              x: Float,
              y: Float,
              originX: Float,
              originY: Float,
              width: Float,
              height: Float,
              scaleX: Float,
              scaleY: Float,
              rotation: Float
            ) {
              setColor()
              Tex.buttonDown.draw(x, y, originX, originY, width, height, scaleX, scaleY, rotation)
              Draw.color()
            }
          }
        }
      }
    }

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
      pack()
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
    }
  }

  class LineMark(
    override val node: RecipeGraphLayout.LineMark
  ): RecipeGraphElement {
    override val nodeWidth: Float get() = 0f
    override val nodeHeight: Float get() = 0f
    override var nodeX: Float = 0f
    override var nodeY: Float = 0f

    override fun outputOffset(item: RecipeItem<*>) = Vec2()
    override fun inputOffset(item: RecipeItem<*>) = Vec2()
  }
}
