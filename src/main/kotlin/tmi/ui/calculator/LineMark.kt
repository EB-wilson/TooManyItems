package tmi.ui.calculator

import arc.math.geom.Vec2
import tmi.recipe.types.RecipeItem

class LineMark(
  override val node: RecipeGraphLayout.LineMark,
  val from: RecipeTab,
  val to: RecipeTab,
): RecipeGraphElement {
  override val nodeWidth: Float get() = 0f
  override val nodeHeight: Float get() = 0f
  override var nodeX: Float = 0f
  override var nodeY: Float = 0f

  override fun centerOffset(): Vec2 = Vec2()
  override fun outputOffset(item: RecipeItem<*>) = Vec2()
  override fun inputOffset(item: RecipeItem<*>) = Vec2()

  override fun setupInputOverListener(line: CalculatorView.LinkLine) = to.setupInputOverListener(line)
  override fun setupOutputOverListener(line: CalculatorView.LinkLine) = from.setupOutputOverListener(line)
}