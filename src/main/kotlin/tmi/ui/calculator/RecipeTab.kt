package tmi.ui.calculator

import arc.graphics.Color
import arc.graphics.g2d.Lines
import arc.math.geom.Vec2
import arc.scene.ui.Button
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import mindustry.gen.Icon
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.ui.CellType
import tmi.ui.RecipeItemCell
import tmi.ui.RecipesDialog
import tmi.ui.TmiUI
import tmi.util.enterSt
import tmi.util.exitSt

class RecipeTab(
  override val node: RecipeGraphLayout.RecNode,
  val view: CalculatorView
): Table(), RecipeGraphElement {
  val graphNode = node.targetNode

  private val materialCells = ObjectMap<RecipeItem<*>, RecipeItemCell>()
  private val productionCells = ObjectMap<RecipeItem<*>, RecipeItemCell>()

  private val materialPos = Seq<Lazy<Vec2>>()
  private val productionPos = Seq<Lazy<Vec2>>()
  private lateinit var blockPos : Lazy<Vec2>

  override val nodeWidth: Float by::width
  override val nodeHeight: Float by::height
  override var nodeX: Float by this::x
  override var nodeY: Float by this::y

  init {
    build()
    validate()
    pack()

    // initial pos
    materialPos.forEach { it.value }
    productionPos.forEach { it.value }
    blockPos.value
  }

  override fun inputOffset(item: RecipeItem<*>): Vec2 {
    val node = materialCells.get(item)?:
      throw IllegalArgumentException("This node item does not match to line item.")

    val cent = Vec2(node.width/2f, node.height/2f)
    node.localToAscendantCoordinates(this, cent)

    return cent
  }
  override fun outputOffset(item: RecipeItem<*>): Vec2 {
    val node = productionCells.get(item)?:
      throw IllegalArgumentException("This node item does not match to line item.")

    val cent = Vec2(node.width/2f, node.height/2f)
    node.localToAscendantCoordinates(this, cent)

    return cent
  }

  override fun setupInputOverListener(line: CalculatorView.LinkLine) {
    val item = line.item
    val node = materialCells.get(item)?:
      throw IllegalArgumentException("This node item does not match to line item.")

    node.enterSt { line.isOver = true }
    node.exitSt { line.isOver = false }
  }

  override fun setupOutputOverListener(line: CalculatorView.LinkLine) {
    val item = line.item
    val node = productionCells.get(item)?:
      throw IllegalArgumentException("This node item does not match to line item.")

    node.enterSt { line.isOver = true }
    node.exitSt { line.isOver = false }
  }

  private fun buildCell(type: CellType, vararg groupItems: RecipeItemStack<*>) = RecipeItemCell(type, *groupItems){ stack, type, mode ->
    if (type == CellType.MATERIAL
        && (stack.itemType == RecipeItemType.ATTRIBUTE || TooManyItems.recipesManager.anyProduction(stack.item))
        && stack.itemType != RecipeItemType.POWER
    ) {
      if (stack.itemType == RecipeItemType.ATTRIBUTE && groupItems.size == 1){
        TmiUI.recipesDialog.showWith {
          setCurrSelecting(stack.item)
        }
      }
      else if (mode != RecipesDialog.Mode.RECIPE) {
        resetLockedItem()
        if (stack.itemType == RecipeItemType.ATTRIBUTE) {
          graphNode.attributes.remove(stack.item)
        }
        else {
          graphNode.disInput(stack.item)
          view.graphUpdated()
        }
      }
      else {
        if (stack.itemType == RecipeItemType.ATTRIBUTE) {
          lockedItem?.also {
            graphNode.attributes.remove(it)
          }

          setLockedItem(stack.item)
          graphNode.attributes.add(stack.item)
        }
        else {
          view.showRecipeSelector(this, stack.item, graphNode)
        }
      }
    }
    else if (type == CellType.BLOCK && mode != RecipesDialog.Mode.RECIPE) {
      graphNode.remove()
      view.graphUpdated()
    }
    else if (type == CellType.PRODUCTION && mode != RecipesDialog.Mode.RECIPE) {
      TmiUI.recipesDialog.showWith {
        setCurrSelecting(stack.item, RecipesDialog.Mode.USAGE, true)
        setFilter{ it.recipeType != RecipeType.building }
        callbackRecipe(Icon.add) {
          view.graph.addNode(RecipeGraphNode(it))
          view.graphUpdated()
          hide()
        }
      }
    }
    else {
      TmiUI.recipesDialog.showWith {
        setCurrSelecting(stack.item)
      }
    }
  }.also {
    if (groupItems.first().itemType == RecipeItemType.ATTRIBUTE) {
      it.style = Button.ButtonStyle(Styles.togglet).also { s -> s.checked = s.over }
      it.update { it.isChecked = groupItems.size <= 1 || it.lockedItem != null }
    }

    val posProv = lazy {
      val cent = Vec2(it.width/2f, it.height/2f)
      it.localToAscendantCoordinates(this, cent)
    }

    if (type == CellType.MATERIAL) {
      groupItems.forEach { i -> materialCells.put(i.item, it) }
      materialPos.add(posProv)
    }
    if (type == CellType.PRODUCTION) {
      groupItems.forEach { i -> productionCells.put(i.item, it) }
      productionPos.add(posProv)
    }
    if (type == CellType.BLOCK) blockPos = posProv
  }

  private fun build() {
    val recipe = graphNode.recipe

    val blockStack = RecipeItemStack(recipe.ownerBlock, 1f)

    val materials = recipe.materialGroups
    val productions = recipe.productions

    val mats = materials.filter { it.first().itemType != RecipeItemType.POWER }
    val prod = productions.filter { it.itemType != RecipeItemType.POWER }

    fill { x, y, w, h ->
      val from = blockPos.value

      Lines.stroke(Scl.scl(5f), Color.gray)
      productionPos.forEach {
        val off = it.value

        Lines.line(
          x + from.x, y + from.y,
          x + from.x, y + from.y + Scl.scl(80f),
        )
        Lines.line(
          x + from.x, y + from.y + Scl.scl(80f),
          x + off.x, y + from.y + Scl.scl(80f),
        )
        Lines.line(
          x + off.x, y + from.y + Scl.scl(80f),
          x + off.x, y + off.y,
        )
      }
      materialPos.forEach {
        val off = it.value

        Lines.line(
          x + from.x, y + from.y,
          x + from.x, y + from.y - Scl.scl(80f),
        )
        Lines.line(
          x + from.x, y + from.y - Scl.scl(80f),
          x + off.x, y + from.y - Scl.scl(80f),
        )
        Lines.line(
          x + off.x, y + from.y - Scl.scl(80f),
          x + off.x, y + off.y,
        )
      }
    }

    table{ outputs ->
      outputs.table { main ->
        main.left()
        if (prod.size <= 5) {
          prod.forEach { stack ->
            val cell = buildCell(CellType.PRODUCTION, stack)

            main.add(cell).size(80f).pad(8f)
          }
        }
        else {
          val up = mutableListOf<RecipeItemStack<*>>()
          val down = mutableListOf<RecipeItemStack<*>>()
          prod.forEachIndexed { i, s ->
            if (i % 2 == 0) up.add(s)
            else down.add(s)
          }

          main.table { upTab ->
            upTab.left().add().width(40f).pad(8f)

            up.forEach { stack ->
              val cell = buildCell(CellType.PRODUCTION, stack)

              upTab.add(cell).size(80f).pad(8f)
            }
          }
          main.row()
          main.table { downTab ->
            downTab.left()
            down.forEach { stack ->
              val cell = buildCell(CellType.PRODUCTION, stack)

              downTab.add(cell).size(80f).pad(8f)
            }
          }
        }
      }
    }
    row()
    table{ block ->
      val cell = buildCell(CellType.BLOCK, blockStack)
      block.add(cell).size(80f).pad(8f)
    }.padTop(64f).padBottom(64f)
    row()
    table{ inputs ->
      inputs.table { main ->
        main.left()

        if (mats.size <= 5) {
          mats.forEach { stack ->
            val cell = buildCell(CellType.MATERIAL, *stack.toTypedArray())

            main.add(cell).size(80f).pad(8f)
          }
        }
        else {
          val up = mutableListOf<List<RecipeItemStack<*>>>()
          val down = mutableListOf<List<RecipeItemStack<*>>>()
          mats.forEachIndexed { i, s ->
            if (i % 2 == 0) up.add(s)
            else down.add(s)
          }

          main.table { upTab ->
            upTab.left()
            up.forEach { stack ->
              val cell = buildCell(CellType.MATERIAL, *stack.toTypedArray())

              upTab.add(cell).size(80f).pad(8f)
            }
          }.growX()
          main.row()
          main.table { downTab ->
            downTab.left().add().width(40f).pad(4f)

            down.forEach { stack ->
              val cell = buildCell(CellType.MATERIAL, *stack.toTypedArray())

              downTab.add(cell).size(80f).pad(8f)
            }
          }.growX()
        }
      }
    }
  }
}