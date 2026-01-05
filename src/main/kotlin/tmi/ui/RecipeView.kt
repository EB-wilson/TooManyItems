package tmi.ui

import arc.func.Cons
import arc.func.Cons3
import arc.func.Prov
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.math.geom.Vec2
import arc.scene.Group
import arc.struct.FloatSeq
import arc.struct.ObjectMap
import arc.struct.Seq
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem

/**配方表显示的布局元素，用于为添加的[RecipeNode]设置正确的位置并将他们显示到界面容器当中 */
class RecipeView @JvmOverloads constructor(
  val recipe: Recipe,
  val noOptional: Boolean,
  defClicked: Cons3<RecipeItemStack<*>, NodeType, RecipesDialog.Mode>? = null,
  nodeListenerBuilder: Cons<RecipeNode>? = null
) : Group() {
  private val bound = Vec2()
  private val nodes = Seq<RecipeNode>()

  private val outputNodes = ObjectMap<RecipeItem<*>, RecipeNode>()
  private val inputNodes = ObjectMap<RecipeItem<*>, RecipeNode>()

  val lines = Seq<LineMeta>()
  private val backGroup = object : Group() {}
  private val childGroup = object : Group() {}

  init {
    addChild(backGroup)

    for (content in recipe.materials.values()) {
      if (noOptional && content.optionalCons) continue

      val node = RecipeNode(NodeType.MATERIAL, content, defClicked)
      nodes.add(node)
      inputNodes.put(content.item, node)
    }
    for (content in recipe.productions.values()) {
      val node = RecipeNode(NodeType.PRODUCTION, content, defClicked)
      nodes.add(node)
      outputNodes.put(content.item, node)
    }

    if (recipe.ownerBlock != null){
      nodes.add(RecipeNode(NodeType.BLOCK, RecipeItemStack(recipe.ownerBlock), defClicked))
    }

    nodes.each { actor ->
      this.addChild(actor)

      nodeListenerBuilder?.get(actor)
    }

    addChild(childGroup)
  }

  fun getOutputNode(item: RecipeItem<*>): RecipeNode? = outputNodes.get(item)
  fun getInputNode(item: RecipeItem<*>): RecipeNode? = inputNodes.get(item)

  override fun layout() {
    super.layout()
    backGroup.clear()
    childGroup.clear()

    lines.clear()
    bound.set(recipe.recipeType.initial(recipe, noOptional))

    val center = nodes.find { it.type == NodeType.BLOCK }
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
