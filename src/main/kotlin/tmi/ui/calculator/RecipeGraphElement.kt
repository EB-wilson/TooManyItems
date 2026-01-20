package tmi.ui.calculator

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.math.geom.Vec2
import arc.scene.style.BaseDrawable
import arc.scene.ui.Button
import arc.scene.ui.layout.Scl
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.recipe.types.RecipeItem
import tmi.ui.TmiUI

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

}
