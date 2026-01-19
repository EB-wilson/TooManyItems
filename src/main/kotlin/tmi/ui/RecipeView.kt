package tmi.ui

import arc.func.Cons
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import arc.scene.ui.layout.WidgetGroup
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Align
import arc.util.Strings
import mindustry.core.UI
import mindustry.ui.Styles
import mindustry.world.meta.StatUnit
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.util.Consts
import kotlin.math.min

/**配方表显示的布局元素，用于为添加的[RecipeItemCell]设置正确的位置并将他们显示到界面容器当中 */
class RecipeView @JvmOverloads constructor(
  val recipe: Recipe,
  private val cellClicked: (RecipeItemCell.(RecipeItemStack<*>, CellType, RecipesDialog.Mode) -> Unit)? = null,
  private val nodePost: Cons<RecipeItemCell>? = null
): WidgetGroup() {
  private val nodes = Seq<RecipeItemCell>()
  private val outputNodes = ObjectMap<RecipeItem<*>, RecipeItemCell>()
  private val inputNodes = ObjectMap<RecipeItem<*>, RecipeItemCell>()

  private val view: Table

  fun getOutputNode(item: RecipeItem<*>): RecipeItemCell? = outputNodes.get(item)
  fun getInputNode(item: RecipeItem<*>): RecipeItemCell? = inputNodes.get(item)

  init {
    recipe.recipeType.apply {
      val layout = Table().also { view = it }
      BuilderScope().buildRecipeView(layout, recipe)

      layout.validate()
      layout.pack()

      addChild(layout)
    }
  }

  override fun layout(){
    val viewWidth = view.width
    val viewHeight = view.height

    if (width <= 0f || height <= 0f) return

    val ratioHor = min(viewWidth/width, 1f)
    val ratioVert = min(viewHeight/height, 1f)
    val scl = min(ratioHor, ratioVert)

    if (scl == 1f) {
      view.setOrigin(Align.center)
      view.isTransform = false
      view.setPosition(width/2f, height/2f)
      view.setSize(1f)
    }
    else {
      view.setOrigin(Align.center)
      view.isTransform = true
      view.setPosition(width/2f, height/2f)
      view.setSize(scl)
    }
  }

  override fun getPrefWidth(): Float = view.prefWidth
  override fun getPrefHeight(): Float = view.prefHeight

  inner class BuilderScope{
    val ownerBlock = RecipeItemStack(recipe.ownerBlock, 1f)

    val materials = recipe.materialGroups
    val productions = recipe.productions

    val powerCons = materials.filter { it.first().itemType == RecipeItemType.POWER }
    val attributeCons = materials.filter { group -> group.first().itemType == RecipeItemType.ATTRIBUTE }
    val normalCons = materials.filter { group -> group.first().itemType == RecipeItemType.NORMAL }
    val boosterCons = materials.filter { group -> group.first().itemType == RecipeItemType.BOOSTER }

    val powerProd = productions.filter { it.itemType == RecipeItemType.POWER }
    val mainProd = productions.filter { it.itemType == RecipeItemType.NORMAL }
    val sideProd = productions.filter { it.itemType == RecipeItemType.SIDEPRODUCT }
    val garbage = productions.filter { it.itemType == RecipeItemType.GARBAGE }

    fun Table.itemCell(type: CellType, vararg groupItems: RecipeItemStack<*>): Cell<RecipeItemCell> {
      val recipeCell = RecipeItemCell(type, *groupItems, clickListener = cellClicked)

      nodes.add(recipeCell)
      groupItems.forEach { stack ->
        if (type == CellType.MATERIAL) inputNodes.put(stack.item, recipeCell)
        else if (type == CellType.PRODUCTION) outputNodes.put(stack.item, recipeCell)
      }

      nodePost?.get(recipeCell)

      return add(recipeCell)
    }

    fun Table.timeTab(): Cell<Table> {
      return table { time ->
        val craftTime = recipe.craftTime
        time.image(Consts.time).size(24f).pad(4f)
        time.add(
          (if (craftTime > 3600) UI.formatTime(craftTime)
          else Strings.autoFixed(craftTime/60, 2) + StatUnit.seconds.localized()),
          Styles.outlineLabel
        ).pad(4f)
      }
    }

    fun RecipeItemCell.doRemove() {
      nodes.remove(this)
      groupItems.forEach { stack ->
        if (type == CellType.MATERIAL) outputNodes.remove(stack.item)
        else inputNodes.remove(stack.item)
      }

      remove()
    }
  }
}
