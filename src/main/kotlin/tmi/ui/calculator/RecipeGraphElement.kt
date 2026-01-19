package tmi.ui.calculator

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.style.BaseDrawable
import arc.scene.ui.Button
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Align
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.recipe.types.RecipeItemType
import tmi.recipe.types.RecipeItem
import tmi.ui.CellType.*
import tmi.ui.RecipeView
import tmi.ui.RecipesDialog
import tmi.ui.TmiUI
import tmi.util.enterSt
import tmi.util.exitSt

interface RecipeGraphElement {
  val node: RecipeGraphLayout.Node
  val nodeWidth: Float
  val nodeHeight: Float
  var nodeX: Float
  var nodeY: Float

  fun outputOffset(item: RecipeItem<*>): Vec2
  fun inputOffset(item: RecipeItem<*>): Vec2

  fun setupInputOverListener(line: CalculatorView.LinkLine)
  fun setupOutputOverListener(line: CalculatorView.LinkLine)

  class AddRecipeButton(
    val view: CalculatorView
  ): Button(ButtonStyle()), RecipeGraphElement {
    init {
      table{
        it.image(object: BaseDrawable() {
          override fun draw(x: Float, y: Float, width: Float, height: Float) {
            when {
              isPressed -> Draw.color(Color.white)
              isOver -> Draw.color(Pal.accent)
              else -> Draw.color(Color.lightGray)
            }

            Lines.stroke(Scl.scl(8f))
            Lines.circle(x + width/2, y + height/2, width/2)

            Icon.add.draw(x - width/2f, y - height/2f, 0f, 0f, width, height, 2f, 2f, 0f)
          }
        }).size(100f)
        it.row()
        it.add(Core.bundle["dialog.calculator.addRecipe"], Styles.outlineLabel)
          .fontScale(1.2f).color(Color.lightGray).padTop(20f)
      }.size(280f)

      clicked {
        TmiUI.recipesDialog.showWith {
          callbackRecipe(Icon.add) { rec ->
            val node = RecipeGraphNode(rec)
            view.graph.addNode(node)
            view.graphUpdated()
            hide()
          }
          showDoubleRecipe(true)
        }
      }

      validate()
      pack()
    }

    override val node = object: RecipeGraphLayout.Node() {
      override fun parents(): List<RecipeGraphLayout.Node> = emptyList()
      override fun parentsWithItem(): Map<RecipeItem<*>, List<RecipeGraphLayout.Node>> = emptyMap()
      override fun children(): List<RecipeGraphLayout.Node> = emptyList()
      override fun childrenWithItem(): Map<RecipeItem<*>, RecipeGraphLayout.Node> = emptyMap()
      override fun setOutput(item: RecipeItem<*>, ins: RecipeGraphLayout.Node) = throw UnsupportedOperationException()
      override fun unOutput(item: RecipeItem<*>, ins: RecipeGraphLayout.Node) = throw UnsupportedOperationException()
      override fun setInput(item: RecipeItem<*>, ins: RecipeGraphLayout.Node) = throw UnsupportedOperationException()
      override fun unInput(item: RecipeItem<*>) = throw UnsupportedOperationException()
    }
    override val nodeWidth: Float by::width
    override val nodeHeight: Float by::height
    override var nodeX: Float by::x
    override var nodeY: Float by::y

    override fun outputOffset(item: RecipeItem<*>): Vec2 = Vec2()
    override fun inputOffset(item: RecipeItem<*>): Vec2 = Vec2()
    override fun setupInputOverListener(line: CalculatorView.LinkLine) { /*no action*/ }
    override fun setupOutputOverListener(line: CalculatorView.LinkLine) { /*no action*/ }
  }

  class RecipeTab(
    override val node: RecipeGraphLayout.RecNode,
    val view: CalculatorView
  ): Table(), RecipeGraphElement {
    val graphNode = node.targetNode
    val recipeView = RecipeView(node.recipe, { stack, type, mode ->
      if (type == MATERIAL
      && TooManyItems.recipesManager.anyProduction(stack.item)
      && stack.itemType != RecipeItemType.POWER) {
        if (mode != RecipesDialog.Mode.RECIPE) {
          graphNode.disInput(stack.item)
          resetLockedItem()
          view.graphUpdated()
        }
        else {
          view.showRecipeSelector(this, stack.item, graphNode)
        }
      }
      else if (type == BLOCK && mode != RecipesDialog.Mode.RECIPE) {
        graphNode.remove()
        view.graphUpdated()
      }
      else {
        TmiUI.recipesDialog.showWith {
          setCurrSelecting(stack.item)
        }
      }
    }) { node ->
      if (node.type == PRODUCTION || (node.type == MATERIAL &&
           (!TooManyItems.recipesManager.anyProduction(*node.getItems().map { it.item }.toTypedArray())
           || node.currentItem().itemType == RecipeItemType.POWER))
        ){
        node.style = Button.ButtonStyle(Styles.defaultb).also {
          it.up = Tex.buttonDisabled
        }
      }
      else if (node.type == MATERIAL) {
        node.style = Button.ButtonStyle(Styles.defaultb).also {
          it.up = object: BaseDrawable(Tex.buttonDown) {
            private fun setColor() {
              if (graphNode.hasInput(node.currentItem().item)) Draw.color(Color.darkGray)
              else Draw.color(Pal.accent, Color.darkGray, Mathf.absin(10f, 1f))
            }

            override fun draw(x: Float, y: Float, width: Float, height: Float) {
              setColor()
              Tex.buttonDown.draw(x, y, width, height)
              Draw.color()
            }

            override fun draw(
              x: Float, y: Float, originX: Float, originY: Float,
              width: Float, height: Float, scaleX: Float, scaleY: Float,
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

    override val nodeWidth: Float by::width
    override val nodeHeight: Float by::height
    override var nodeX: Float by this::x
    override var nodeY: Float by this::y

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

    override fun setupInputOverListener(line: CalculatorView.LinkLine) {
      val item = line.item
      val node = recipeView.getInputNode(item)?:
        throw IllegalArgumentException("This node item does not match to line item.")

      node.enterSt { line.isOver = true }
      node.exitSt { line.isOver = false }
    }

    override fun setupOutputOverListener(line: CalculatorView.LinkLine) {
      val item = line.item
      val node = recipeView.getOutputNode(item)?:
        throw IllegalArgumentException("This node item does not match to line item.")

      node.enterSt { line.isOver = true }
      node.exitSt { line.isOver = false }
    }

    private fun build() {
      add(recipeView).fill()

      recipeView.validate()
      recipeView.pack()
    }
  }

  class LineMark(
    override val node: RecipeGraphLayout.LineMark,
    val from: RecipeTab,
    val to: RecipeTab,
  ): RecipeGraphElement {
    override val nodeWidth: Float get() = 0f
    override val nodeHeight: Float get() = 0f
    override var nodeX: Float = 0f
    override var nodeY: Float = 0f

    override fun outputOffset(item: RecipeItem<*>) = Vec2()
    override fun inputOffset(item: RecipeItem<*>) = Vec2()

    override fun setupInputOverListener(line: CalculatorView.LinkLine) = to.setupInputOverListener(line)
    override fun setupOutputOverListener(line: CalculatorView.LinkLine) = from.setupOutputOverListener(line)
  }
}
