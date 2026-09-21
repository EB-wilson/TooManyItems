package tmi.ui.calculator

import arc.math.geom.Vec2
import tmi.recipe.types.RecipeItem

/**长边在中间层插的那个占位元素：本身没有尺寸，只把两端的悬停监听转交给真正的卡片。
 *
 * 两端收 [RecipeGraphElement] 而不是具体的卡片类型：断环影子的某条输出也可能跨好几层，
 * 于是长边的提供方那端会是 [ShadowTab]。*/
class LineMark(
  override val node: RecipeGraphLayout.LineMark,
  val from: RecipeGraphElement,
  val to: RecipeGraphElement,
): RecipeGraphElement {
  override val nodeWidth: Float get() = 0f
  override val nodeHeight: Float get() = 0f
  override var nodeX: Float = 0f
  override var nodeY: Float = 0f

  override fun centerOffset(): Vec2 = Vec2()
  override fun outputOffset(item: RecipeItem<*>) = Vec2()
  override fun inputOffset(item: RecipeItem<*>) = Vec2()

  override fun setupInputOverListener(line: CalculatorLayout.LinkLine) = to.setupInputOverListener(line)
  override fun setupOutputOverListener(line: CalculatorLayout.LinkLine) = from.setupOutputOverListener(line)
}