package tmi.ui.calculator

import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.style.BaseDrawable
import arc.scene.ui.Button
import arc.scene.ui.TextField
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.recipe.AmountFormatter
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.ui.CellType
import tmi.ui.RecipeItemCell
import tmi.ui.RecipesDialog
import tmi.ui.TmiUI
import tmi.util.Consts
import tmi.util.enterSt
import tmi.util.exitSt

class RecipeTab(
  override val node: RecipeGraphLayout.RecNode,
  val view: CalculatorView
): Table(), RecipeGraphElement {
  val graphNode = node.targetNode

  private val allCells = Seq<RecipeItemCell>()

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

  fun updateNodeEfficiency() {
    val env = graphNode.envParameter
    env.clear()

    graphNode.recipe.materialGroups
      .filter { it.first().itemType == RecipeItemType.POWER }
      .forEach { p -> p.forEach { env.setFull(it) } }

    if (node.contextDepth == 0) graphNode.balanceAmount = graphNode.targetAmount

    materialCells.values()
      .toSet()
      .forEach { cell ->
        var stack = cell.chosenItem?.let { cell.currentItem() }

        if (stack == null) {
          stack = cell.groupItems.first()
          if (stack.isOptional) return@forEach
        }

        env.setFull(stack)
      }

    allCells.forEach { it.updateText() }
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
    cellCallback(type, stack, groupItems, mode)
  }.also { postHandleCell(it, groupItems, type) }

  private fun postHandleCell(
    cell: RecipeItemCell,
    groupItems: Array<out RecipeItemStack<*>>,
    type: CellType,
  ) {
    allCells.add(cell)

    val posProv = lazy {
      val cent = Vec2(cell.width/2f, cell.height/2f)
      cell.localToAscendantCoordinates(this, cent)
    }

    if (groupItems.first().itemType == RecipeItemType.ATTRIBUTE) {
      if (groupItems.size == 1) cell.setChosenItem(groupItems.first().item)
      else groupItems.find { s -> graphNode.attributes.contains(s.item) }?.also { s -> cell.setChosenItem(s.item) }
    }
    else if (groupItems.first().isOptional){
      groupItems.find { s -> graphNode.optionals.contains(s.item) }?.also { s -> cell.setChosenItem(s.item) }
    }

    if (type != CellType.BLOCK && groupItems.first().itemType == RecipeItemType.NORMAL) {
      cell.setFormatter(AmountFormatter.unitTimedFormatter())
    }

    when (type) {
      CellType.MATERIAL -> {
        when (groupItems.first().itemType) {
          RecipeItemType.ATTRIBUTE, RecipeItemType.POWER -> cell.setMultiplier { graphNode.balanceAmount.toFloat() }
          else -> cell.setMultiplier { graphNode.multiplier*graphNode.balanceAmount }
        }

        groupItems.forEach { i -> materialCells.put(i.item, cell) }
        materialPos.add(posProv)

        groupItems.find { s -> graphNode.hasInput(s.item) }?.also { s -> cell.setChosenItem(s.item) }
        cell.style = Button.ButtonStyle(cell.style).also { s ->
          s.up = object : BaseDrawable(s.up) {
            override fun draw(x: Float, y: Float, width: Float, height: Float) {
              if (cell.chosenItem != null) Draw.color(Color.darkGray)
              else Draw.color(Color.darkGray, Pal.accent, Mathf.absin(10f, 1f))
              Tex.buttonDown.draw(x, y, width, height)
            }
          }
        }
      }

      CellType.PRODUCTION -> {
        cell.setMultiplier { graphNode.balanceAmount*graphNode.efficiency }

        groupItems.forEach { i -> productionCells.put(i.item, cell) }
        productionPos.add(posProv)
      }

      CellType.BLOCK -> {
        cell.style = Styles.cleart
        cell.margin(6f)
      }
    }
  }

  private fun RecipeItemCell.cellCallback(
    type: CellType,
    stack: RecipeItemStack<*>,
    groupItems: Array<out RecipeItemStack<*>>,
    mode: RecipesDialog.Mode,
  ) {
    if (type == CellType.MATERIAL
        && (stack.itemType == RecipeItemType.ATTRIBUTE || TooManyItems.recipesManager.anyProduction(stack.item))
        && stack.itemType != RecipeItemType.POWER
    ) {
      if (stack.itemType == RecipeItemType.ATTRIBUTE && groupItems.size == 1) {
        TmiUI.recipesDialog.showWith {
          setCurrSelecting(stack.item)
        }
      }
      else if (mode != RecipesDialog.Mode.RECIPE) {
        resetLockedItem()
        if (stack.itemType == RecipeItemType.ATTRIBUTE) {
          graphNode.attributes.remove(stack.item)
          view.balanceUpdated()
        }
        else if (stack.isOptional && !graphNode.hasInput(stack.item)) {
          graphNode.optionals.remove(stack.item)
          view.balanceUpdated()
        }
        else {
          if (stack.isOptional) graphNode.optionals.remove(stack.item)
          graphNode.disInput(stack.item)
          view.graphUpdated()
        }
      }
      else {
        if (stack.itemType == RecipeItemType.ATTRIBUTE) {
          chosenItem?.also {
            graphNode.attributes.remove(it)
          }

          setChosenItem(stack.item)
          graphNode.attributes.add(stack.item)
          view.balanceUpdated()
        }
        else {
          if (stack.isOptional && !graphNode.optionals.contains(stack.item)) {
            graphNode.optionals.add(stack.item)
            setChosenItem(stack.item)
            view.balanceUpdated()
          }
          else {
            view.showRecipeSelector(this, stack.item, graphNode)
          }
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
        setFilter { it.recipeType != RecipeType.building }
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
  }

  private fun build() {
    val recipe = graphNode.recipe

    val blockStack = RecipeItemStack(recipe.ownerBlock, 1f)

    val materials = recipe.materialGroups
    val productions = recipe.productions

    val mats = materials.filter { it.first().itemType != RecipeItemType.POWER }
    val prod = productions.filter { it.itemType != RecipeItemType.POWER }

    fill { x, y, _, _ ->
      val from = blockPos.value
      val centOff = Scl.scl(92f)

      Lines.stroke(Scl.scl(5f), Color.gray)
      productionPos.forEach {
        val off = it.value

        Lines.line(
          x + from.x, y + from.y,
          x + from.x, y + from.y + centOff,
        )
        Lines.line(
          x + from.x, y + from.y + centOff,
          x + off.x, y + from.y + centOff,
        )
        Lines.line(
          x + off.x, y + from.y + centOff,
          x + off.x, y + off.y,
        )
      }
      materialPos.forEach {
        val off = it.value

        Lines.line(
          x + from.x, y + from.y,
          x + from.x, y + from.y - centOff,
        )
        Lines.line(
          x + from.x, y + from.y - centOff,
          x + off.x, y + from.y - centOff,
        )
        Lines.line(
          x + off.x, y + from.y - centOff,
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
    table{ center ->
      val t = center.table(Consts.darkGrayUI){ c ->
        val cell = buildCell(CellType.BLOCK, blockStack)
        c.add(cell).size(80f).pad(8f)

        if (graphNode.parents().any()) {
          c.add("").pad(8f).update {
            if (graphNode.balanceAmount > 0) it.setText("${graphNode.balanceAmount}x")
            else it.setText("--x")
          }.pad(6f).fontScale(1.5f)
        }
        else c.table { num ->
          num.field(graphNode.targetAmount.toString(), TextField.TextFieldFilter.digitsOnly) { str ->
            if (str.isBlank()) return@field
            graphNode.targetAmount = str.toInt()
            view.balanceUpdated()
          }.width(60f)
          num.add("x")
        }.pad(6f)
      }.margin(8f).get()

      blockPos = lazy {
        val cent = Vec2(t.width/2f, t.height/2f)
        t.localToAscendantCoordinates(this, cent)
      }
    }.padTop(64f).padBottom(64f).fill()
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