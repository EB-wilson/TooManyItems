package tmi.recipe

import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.math.geom.Vec2
import arc.scene.Group
import arc.scene.ui.layout.Scl
import arc.struct.Seq
import tmi.recipe.types.BuildingRecipe
import tmi.recipe.types.CollectingRecipe
import tmi.recipe.types.FactoryRecipe
import tmi.recipe.types.GeneratorRecipe
import tmi.ui.RecipeNode
import tmi.ui.RecipeView
import tmi.ui.RecipeView.LineMeta
import kotlin.reflect.KVisibility

/**配方表类型，用于描述一个配方如何被显示或者计算等 */
abstract class RecipeType {
  companion object {
    val all = Seq<RecipeType>()

    @JvmStatic
    val factory by lazy { FactoryRecipe().also { all.add(it) } }
    @JvmStatic
    val building by lazy { BuildingRecipe().also { all.add(it) } }
    @JvmStatic
    val collecting by lazy { CollectingRecipe().also { all.add(it) } }
    @JvmStatic
    val generator by lazy { GeneratorRecipe().also { all.add(it) } }
  }

  /**此类型的ID，必须是唯一的，此类型的所有实例共用此id*/
  abstract val id: Int

  /**生成[配方视图][RecipeView]前对上下文数据进行初始化，并计算布局尺寸
   *
   * @return 表示该布局的长宽尺寸的二元向量
   */
  abstract fun initial(recipe: Recipe): Vec2

  /**为参数传入的[RecipeNode]设置坐标以完成布局 */
  abstract fun layout(recipeNode: RecipeNode)

  /**生成从给定起始节点到目标节点的[线条信息][tmi.ui.RecipeView.LineMeta] */
  abstract fun line(from: RecipeNode, to: RecipeNode): LineMeta

  /**向配方显示器内添加显示部件的入口 */
  open fun buildView(view: Group) {}

  fun drawLine(recipeView: RecipeView) {
    Draw.scl(recipeView.scaleX, recipeView.scaleY)

    for (line in recipeView.lines) {
      if (line.vertices.size < 2) continue

      val a = Draw.getColor().a
      Lines.stroke(Scl.scl(5f)*recipeView.scaleX, line.color.get())
      Draw.alpha(Draw.getColor().a*a)

      if (line.vertices.size <= 4) {
        val x1 = line.vertices.items[0] - recipeView.width/2
        val y1 = line.vertices.items[1] - recipeView.height/2
        val x2 = line.vertices.items[2] - recipeView.width/2
        val y2 = line.vertices.items[3] - recipeView.height/2
        Lines.line(
          recipeView.x + recipeView.width/2 + x1*recipeView.scaleX,
          recipeView.y + recipeView.height/2 + y1*recipeView.scaleY,
          recipeView.x + recipeView.width/2 + x2*recipeView.scaleX,
          recipeView.y + recipeView.height/2 + y2*recipeView.scaleY
        )
        continue
      }

      Lines.beginLine()
      var i = 0
      while (i < line.vertices.size) {
        val x1 = line.vertices.items[i] - recipeView.width/2
        val y1 = line.vertices.items[i + 1] - recipeView.height/2

        Lines.linePoint(
          recipeView.x + recipeView.width/2 + x1*recipeView.scaleX,
          recipeView.y + recipeView.height/2 + y1*recipeView.scaleY
        )
        i += 2
      }
      Lines.endLine()
    }

    Draw.reset()
  }

  override fun hashCode(): Int {
    return id
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    return true
  }
}
