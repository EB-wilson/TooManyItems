package tmi.ui.calculator

import arc.Core
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.style.BaseDrawable
import arc.scene.ui.Button
import arc.scene.ui.TextField
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Strings
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.recipe.AmountFormatter
import tmi.recipe.InputTable
import tmi.recipe.RecipeItemStack
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.ui.CellType
import tmi.ui.CellType.*
import tmi.ui.RecipeItemCell
import tmi.ui.RecipesDialog
import tmi.ui.TmiUI
import tmi.ui.addEventBlocker
import tmi.util.*
import kotlin.math.ceil

class RecipeTab(
  override val node: RecipeGraphLayout.RecNode,
  val view: CalculatorView,
  val isShadow: Boolean,
): Table(), RecipeGraphElement, CalculatorDialog.TipsProvider {
  val graphNode = node.targetNode

  private val allCells = Seq<RecipeItemCell>()

  private val materialCells = ObjectMap<RecipeItem<*>, RecipeItemCell>()
  private val productionCells = ObjectMap<RecipeItem<*>, RecipeItemCell>()

  private val materialPos = ObjectMap<RecipeItem<*>, ProviderWrap<Vec2>>()
  private val productionPos = ObjectMap<RecipeItem<*>, ProviderWrap<Vec2>>()
  private lateinit var blockPos : ProviderWrap<Vec2>
  private lateinit var centerUp : ProviderWrap<Vec2>
  private lateinit var centerDown : ProviderWrap<Vec2>

  override val nodeWidth: Float by::width
  override val nodeHeight: Float by::height
  override var nodeX: Float by this::x
  override var nodeY: Float by this::y

  init {
    build()
    validate()
    pack()

    addEventBlocker()

    // initial pos
    materialPos.forEach { it.value }
    productionPos.forEach { it.value }
    blockPos.value
    centerUp.value
    centerDown.value
  }

  override fun layout() {
    super.layout()

    materialPos.values().forEach { it.refresh() }
    productionPos.values().forEach { it.refresh() }
    blockPos.refresh()
    centerUp.refresh()
    centerDown.refresh()
  }

  override fun getTip(): String = Core.bundle["calculator.tips.shadowed"]
  override fun tipValid(): Boolean = isShadow

  fun setupEnvParameters(env: InputTable) {
    if (isShadow) return

    env.clear()
    graphNode.recipe.materialGroups
      .filter { it.first().itemType == RecipeItemType.POWER }
      .forEach { p -> p.forEach { env.setFull(it) } }

    if (node.contextDepth == 0) graphNode.balanceAmount = graphNode.targetAmount.toFloat()

    materialCells.values()
      .toSet()
      .forEach { cell ->
        var stack = cell.chosenItem?.let { cell.currentItem() }

        if (stack == null) {
          stack = cell.groupItems.first()
          if (stack.isOptional || stack.itemType == RecipeItemType.ATTRIBUTE) return@forEach
        }

        env.setFull(stack)
      }

    allCells.forEach { it.updateText() }
  }

  fun getRecipeCells() = allCells.toList()

  override fun centerOffset() = blockPos.value

  override fun inputOffset(item: RecipeItem<*>): Vec2 {
    val pos = materialPos.get(item)?:
      throw IllegalArgumentException("This node item does not match to line item.")

    return pos.value
  }
  override fun outputOffset(item: RecipeItem<*>): Vec2 {
    val pos = productionPos.get(item)?:
      throw IllegalArgumentException("This node item does not match to line item.")

    return pos.value
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

  private fun buildCell(type: CellType, vararg groupItems: RecipeItemStack<*>) =
    object: RecipeItemCell(type, *groupItems, clickListener = { stack, type, mode ->
      if (!isShadow) cellCallback(type, stack, groupItems, mode)
    }), CalculatorDialog.TipsProvider{
      override fun getTip(): String {
        val input = if (Vars.mobile) "mobile" else "desktop"
        val stackStrify = groupItems.joinToString("/") { it.item.localizedName }
        return when(this.type){
          MATERIAL -> Core.bundle.format("calculator.tips.material-$input", stackStrify)
          BLOCK -> Core.bundle.format("calculator.tips.block-$input", stackStrify)
          PRODUCTION -> Core.bundle.format("calculator.tips.production-$input", stackStrify)
          ATTRIBUTE ->
            if (groupItems.size > 1 || groupItems.first().isOptional)
              Core.bundle.format("calculator.tips.attribute-$input", stackStrify)
            else Core.bundle.format("calculator.tips.fix.attribute-$input", stackStrify)
          OPTIONAL -> Core.bundle.format("calculator.tips.optional-$input", stackStrify)
        }
      }
    }.also { postHandleCell(it, groupItems, type) }

  private fun postHandleCell(
    cell: RecipeItemCell,
    groupItems: Array<out RecipeItemStack<*>>,
    type: CellType,
  ) {
    cell.addEventBlocker()
    allCells.add(cell)

    if (isShadow) {
      cell.isDisabled = true
    }

    val posProv = wrap {
      val cent = Vec2(cell.width/2f, cell.height/2f)
      cell.localToAscendantCoordinates(this, cent)
    }

    if (type == MATERIAL || type == PRODUCTION) cell.setFormatter(AmountFormatter.timedAmountFormatter())

    when (type) {
      MATERIAL -> {
        when (groupItems.first().itemType) {
          RecipeItemType.ISOLATED -> cell.setMultiplier { graphNode.balanceAmount }
          RecipeItemType.BOOSTER -> cell.setMultiplier { graphNode.multiplier*ceil(graphNode.balanceAmount) }
          else -> cell.setMultiplier { graphNode.efficiency*graphNode.balanceAmount }
        }

        if (!groupItems.first().isOptional) groupItems.forEach { i -> materialCells.put(i.item, cell) }
        if (groupItems.first().itemType != RecipeItemType.ATTRIBUTE)
          groupItems.forEach { i -> materialPos.put(i.item, posProv) }

        groupItems.find { s -> graphNode.hasInput(s.item) }?.also { s -> cell.setChosenItem(s.item) }
        cell.style = Button.ButtonStyle(cell.style).also { s ->
          s.up = object : BaseDrawable(s.up) {
            override fun draw(x: Float, y: Float, width: Float, height: Float) {
              if (cell.chosenItem != null) Draw.color(Color.darkGray)
              else if (view.imageGenerating) Draw.color(Color.darkGray)
              else Draw.color(Color.darkGray, Pal.accent, Mathf.absin(10f, 1f))
              Tex.buttonDown.draw(x, y, width, height)
            }
          }
        }
      }
      PRODUCTION -> {
        cell.setMultiplier {
          graphNode.balanceAmount*if (groupItems.first().itemType == RecipeItemType.ISOLATED) 1f else graphNode.efficiency
        }

        groupItems.forEach { i -> productionCells.put(i.item, cell) }
        groupItems.forEach { i -> productionPos.put(i.item, posProv) }
      }
      BLOCK -> {
        cell.style = Styles.cleart
        cell.margin(6f)
      }
      ATTRIBUTE -> {
        cell.setMultiplier { graphNode.balanceAmount }
        groupItems.forEach { i -> materialCells.put(i.item, cell) }

        if (groupItems.size == 1) cell.setChosenItem(groupItems.first().item)
        else {
          groupItems.find { s -> graphNode.attributes.contains(s.item) }
            ?.also { s -> cell.setChosenItem(s.item) }
          ?:run{
            if (!groupItems.first().isOptional) {
              val default = groupItems.maxBy { it.efficiency }
              cell.setChosenItem(default.item)
              graphNode.attributes.add(default.item)

              view.balanceUpdated()
            }
          }
        }
      }
      OPTIONAL -> {
        when(groupItems.first().itemType) {
          RecipeItemType.BOOSTER -> cell.setMultiplier { graphNode.multiplier*ceil(graphNode.balanceAmount) }
          RecipeItemType.ISOLATED -> cell.setMultiplier { graphNode.balanceAmount }
          else -> cell.setMultiplier { graphNode.efficiency*graphNode.balanceAmount }
        }

        groupItems.forEach { i -> materialCells.put(i.item, cell) }

        groupItems.find { s -> graphNode.optionals.contains(s.item) || graphNode.hasInput(s.item) }
          ?.also { s -> cell.setChosenItem(s.item) }
      }
    }
  }

  private fun RecipeItemCell.cellCallback(
    type: CellType,
    stack: RecipeItemStack<*>,
    groupItems: Array<out RecipeItemStack<*>>,
    mode: RecipesDialog.Mode,
  ) {
    when {
      type == MATERIAL
      && TooManyItems.recipesManager.anyProduction(stack.item)
      && stack.itemType != RecipeItemType.POWER -> {
        if (mode != RecipesDialog.Mode.RECIPE) {
          resetLockedItem()
          graphNode.disInput(stack.item)
          view.graphUpdated()
        }
        else {
          view.showRecipeSelector(this, stack.item, graphNode)
        }
      }
      type == OPTIONAL -> {
        // Should update the graph structure with optional items. (switch)
        if (!graphNode.optionals.contains(stack.item) && mode == RecipesDialog.Mode.RECIPE) {
          graphNode.optionals.add(stack.item)
          setChosenItem(stack.item)

          view.graphUpdated()
        }
        else {
          resetLockedItem()
          graphNode.optionals.remove(stack.item)

          graphNode.disInput(stack.item)

          view.graphUpdated()
        }
      }
      type == ATTRIBUTE -> {
        if (mode != RecipesDialog.Mode.RECIPE) {
          if (groupItems.size == 1) {
            TmiUI.recipesDialog.showWith {
              setCurrSelecting(stack.item)
            }
          }
          else {
            resetLockedItem()
            graphNode.attributes.remove(stack.item)
            view.balanceUpdated()
          }
        }
        else {
          chosenItem?.also { graphNode.attributes.remove(it) }

          setChosenItem(stack.item)
          graphNode.attributes.add(stack.item)
          view.balanceUpdated()
        }
      }
      type == BLOCK && mode != RecipesDialog.Mode.RECIPE -> {
        graphNode.remove()
        view.graphUpdated()
      }
      type == PRODUCTION && mode != RecipesDialog.Mode.RECIPE -> {
        TmiUI.recipesDialog.showWith {
          setCurrSelecting(stack.item, RecipesDialog.Mode.USAGE, true)
          setFilter { it.recipeType != RecipeType.building }
          callbackRecipe(Icon.add) {
            val node = RecipeGraphNode(it)
            view.graph.addNode(node)
            view.linkExisted(node)
            view.graphUpdated()
            hide()
          }
        }
      }
      else -> {
        TmiUI.recipesDialog.showWith {
          setCurrSelecting(stack.item)
        }
      }
    }
  }

  private fun build() {
    val recipe = graphNode.recipe

    val blockStack = RecipeItemStack(recipe.ownerBlock, 1f)

    val materials = recipe.materialGroups
    val productions = recipe.productions

    val mats = materials.filter { it.first().itemType != RecipeItemType.POWER && it.first().itemType != RecipeItemType.ATTRIBUTE }
    val optionals = mats.filter { it.first().isOptional }
    val nonOptionals = mats.filter { !it.first().isOptional }

    val realMats = if (isShadow) emptyList() else {
      nonOptionals + optionals.map {
        listOfNotNull(it.find { s ->
          graphNode.optionals.contains(s.item) || graphNode.hasInput(s.item)
        })
      }.filter { it.isNotEmpty() }
    }

    val attrs = materials.filter { it.first().itemType == RecipeItemType.ATTRIBUTE }
    val prod = productions.filter { it.itemType != RecipeItemType.POWER }

    fill { x, y, _, _ ->
      val from = blockPos.value
      val centUp = centerUp.value
      val centDown = centerDown.value

      Lines.stroke(Scl.scl(5f), Color.gray)
      productionPos.values().forEach {
        val off = it.value

        Lines.line(
          x + from.x, y + from.y,
          x + from.x, y + centUp.y,
        )
        Lines.line(
          x + from.x, y + centUp.y,
          x + off.x, y + centUp.y,
        )
        Lines.line(
          x + off.x, y + centUp.y,
          x + off.x, y + off.y,
        )
      }
      materialPos.values().forEach {
        val off = it.value

        Lines.line(
          x + from.x, y + from.y,
          x + from.x, y + centDown.y,
        )
        Lines.line(
          x + from.x, y + centDown.y,
          x + off.x, y + centDown.y,
        )
        Lines.line(
          x + off.x, y + centDown.y,
          x + off.x, y + off.y,
        )
      }
    }

    table{ outputs ->
      outputs.table { main ->
        main.left()
        if (prod.size <= 5) {
          prod.forEach { stack ->
            val cell = buildCell(PRODUCTION, stack)

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
              val cell = buildCell(PRODUCTION, stack)

              upTab.add(cell).size(80f).pad(8f)
            }
          }
          main.row()
          main.table { downTab ->
            downTab.left()
            down.forEach { stack ->
              val cell = buildCell(PRODUCTION, stack)

              downTab.add(cell).size(80f).pad(8f)
            }
          }
        }
      }
    }
    row()
    add(Element().also {
      centerUp = wrap { it.localToAscendantCoordinates(this, Vec2(it.width/2, it.height/2)) }
    }).height(64f).fill()
    row()
    table{ center ->
      val t = center.table(if (isShadow) Tex.buttonDisabled else Consts.darkGrayUI){ cent ->
        cent.table { c ->
          val cell = buildCell(BLOCK, blockStack)
          c.add(cell).size(80f).pad(8f)

          if (graphNode.contextDepth > 0) {
            c.table { amount ->
              amount.add("").update {
                if (graphNode.balanceAmount > 0) it.setText("${ceil(graphNode.balanceAmount).toInt()}x")
                else it.setText("--x")
              }.pad(6f).fontScale(1.5f).get().act(0f)
              amount.add("").update {
                if (graphNode.balanceAmount > 0) it.setText("[gray](${Strings.autoFixed(graphNode.balanceAmount, 2)})")
                else it.setText("")
              }.pad(6f).fontScale(0.9f).bottom().get().act(0f)
            }.fill()
          }
          else c.table { num ->
            num.field(graphNode.targetAmount.toString(), TextField.TextFieldStyle(Styles.defaultField)) { str ->
              if (str.isBlank()) return@field
              val a = str.toIntOrNull()?.let { if (it > 10000) 0 else it } ?: 0
              graphNode.targetAmount = a
              view.balanceUpdated()
            }.width(60f).update {
              if (it.text.isBlank()) return@update
              val a = it.text.toIntOrNull()
              it.color.set(if (a?.let { n -> n > 10000 } ?: true) Color.crimson else Color.white)
              it.style.fontColor = if (a?.let { n -> n > 10000 } ?: true) Color.crimson else Color.white
            }.get().filter = TextField.TextFieldFilter.digitsOnly
            num.add("x")
          }.pad(6f)
        }.fill()

        if (attrs.isNotEmpty() || optionals.isNotEmpty()) {
          cent.row()
          cent.image().color(Color.gray).height(4f).growX().padLeft(-8f).padRight(-8f)
          cent.row()
          cent.table { attr ->
            attrs.forEach { stack ->
              val cell = buildCell(ATTRIBUTE, *stack.toTypedArray())
              cell.style = Styles.clearNoneTogglei
              cell.update { cell.isChecked = cell.chosenItem != null }
              attr.add(cell).size(64f).pad(8f).margin(6f).get().setFontScl(0.8f)
            }
            optionals.forEach { stack ->
              val cell = buildCell(OPTIONAL, *stack.toTypedArray())
              cell.style = Styles.clearNoneTogglei
              cell.update { cell.isChecked = cell.chosenItem != null }
              attr.add(cell).size(64f).pad(8f).margin(6f).get().setFontScl(0.8f)
            }
          }.pad(-4f)
        }

        if (isShadow) {
          cent.fill { over ->
            over.top().left()
            over.image(Icon.copy).size(42f).pad(8f)
          }
        }
      }.margin(8f).get()

      blockPos = wrap {
        val cent = Vec2(t.width/2f, t.height/2f)
        t.localToAscendantCoordinates(this, cent)
      }
    }.fill()
    row()
    add(Element().also {
      centerDown = wrap { it.localToAscendantCoordinates(this, Vec2(it.width/2, it.height/2)) }
    }).height(64f).fill()
    row()
    table{ inputs ->
      inputs.table { main ->
        main.left()

        if (realMats.size <= 5) {
          realMats.forEach { stack ->
            val cell = buildCell(MATERIAL, *stack.toTypedArray())

            main.add(cell).size(80f).pad(8f)
          }
        }
        else {
          val up = mutableListOf<List<RecipeItemStack<*>>>()
          val down = mutableListOf<List<RecipeItemStack<*>>>()
          realMats.forEachIndexed { i, s ->
            if (i % 2 == 0) up.add(s)
            else down.add(s)
          }

          main.table { upTab ->
            upTab.left()
            up.forEach { stack ->
              val cell = buildCell(MATERIAL, *stack.toTypedArray())

              upTab.add(cell).size(80f).pad(8f)
            }
          }.growX()
          main.row()
          main.table { downTab ->
            downTab.left().add().width(40f).pad(4f)

            down.forEach { stack ->
              val cell = buildCell(MATERIAL, *stack.toTypedArray())

              downTab.add(cell).size(80f).pad(8f)
            }
          }.growX()
        }
      }
    }
  }
}