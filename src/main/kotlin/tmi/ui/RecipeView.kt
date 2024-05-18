package tmi.ui

import arc.func.Cons3
import arc.func.Prov
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.math.geom.Vec2
import arc.scene.Group
import arc.struct.FloatSeq
import arc.struct.Seq
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack

/**配方表显示的布局元素，用于为添加的[RecipeNode]设置正确的位置并将他们显示到界面容器当中 */
class RecipeView(val recipe: Recipe, nodeClicked: Cons3<RecipeItemStack, NodeType, RecipesDialog.Mode>?) : Group() {
  private val bound = Vec2()
  private val nodes = Seq<RecipeNode>()

  val lines = Seq<LineMeta>()
  private val backGroup = object : Group() {}
  private val childGroup = object : Group() {}

  init {
    addChild(childGroup)

    for (content in recipe.materials.values()) {
      nodes.add(RecipeNode(NodeType.MATERIAL, content, nodeClicked))
    }
    for (content in recipe.productions.values()) {
      nodes.add(RecipeNode(NodeType.PRODUCTION, content, nodeClicked))
    }

    if (recipe.ownerBlock != null) nodes.add(RecipeNode(NodeType.BLOCK, RecipeItemStack(recipe.ownerBlock!!), nodeClicked))

    nodes.each { actor: RecipeNode? -> this.addChild(actor) }

    addChild(childGroup)
  }

  override fun layout() {
    super.layout()
    backGroup.clear()
    childGroup.clear()
    backGroup.invalidate()
    childGroup.invalidate()

    lines.clear()
    bound.set(recipe.recipeType.initial(recipe))

    val center = nodes.find { e: RecipeNode -> e.type == NodeType.BLOCK }
    for (node in nodes) {
      recipe.recipeType.layout(node)
      val line = recipe.recipeType.line(node, center)
      lines.add(line)
    }

    recipe.recipeType.buildView(childGroup)
    recipe.recipeType.buildBack(backGroup)
  }

  override fun draw() {
    validate()

    Draw.alpha(parentAlpha)
    recipe.recipeType.drawLine(this)
    super.draw()
  }

  override fun getPrefWidth(): Float {
    return bound.x
  }

  override fun getPrefHeight(): Float {
    return bound.y
  }

  class LineMeta {
    val vertices: FloatSeq = FloatSeq()
    var color: Prov<Color> = Prov { Color.white }

    fun setVertices(vararg vert: Float) {
      vertices.clear()
      vertices.addAll(*vert)
    }

    fun addVertex(x: Float, y: Float) {
      vertices.add(x, y)
    }
  }
}
