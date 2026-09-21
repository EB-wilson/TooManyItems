package tmi.ui.calculator

import arc.Core
import arc.func.Boolp
import arc.graphics.Color
import arc.scene.style.Drawable
import arc.scene.ui.Dialog
import arc.scene.ui.layout.Collapser
import arc.scene.ui.layout.Table
import arc.util.Align
import arc.util.Scaling
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.recipe.AmountFormatter
import tmi.recipe.RecipeItemStack
import tmi.ui.SpecialFormatters
import tmi.util.Consts

class StatisticDialog: BaseDialog(Core.bundle["dialog.statistic.title"]) {
  private var inputs = Table()
  private var optionalInputs = Table()
  private var outputs = Table()
  private var redundant = Table()
  private var buildCosts = Table()

  private var showInput = false
  private var showOptional = false
  private var showOutput = false
  private var showRedundant = false
  private var showCosts = false

  init {
    addCloseButton()
    buildInit()
  }

  fun buildInit(){
    cont.clear()

    cont.table(Consts.grayUIAlpha){ main ->
      main.top().pane(Styles.smallPane) { pane ->
        pane.left().top().defaults().left()
        pane.sepLine(Icon.downloadSmall, Core.bundle["dialog.statistic.input"]) { showInput }
        pane.row()
        pane.table { inputs = it }.growX().fillY()
        pane.row()
        pane.sepLine(Icon.downloadSmall, Core.bundle["dialog.statistic.optionalInput"]) { showOptional }
        pane.row()
        pane.table { optionalInputs = it }.growX().fillY()
        pane.row()
        pane.sepLine(Icon.uploadSmall, Core.bundle["dialog.statistic.output"]) { showOutput }
        pane.row()
        pane.table { outputs = it }.growX().fillY()
        pane.row()
        pane.sepLine(Icon.logicSmall, Core.bundle["dialog.statistic.redundant"]) { showRedundant }
        pane.row()
        pane.table { redundant = it }.growX().fillY()
        pane.row()
        pane.sepLine(Icon.hammerSmall, Core.bundle["dialog.statistic.buildingCost"]) { showCosts }
        pane.row()
        pane.table { buildCosts = it }.growX().fillY()
      }.left().top().growX().fillY()
    }.top().fillX().growY().padLeft(25f).padRight(25f).maxWidth(1200f).minWidth(600f).margin(12f)
  }

  fun buildWith(statistic: RecipeStatistic){
    val input = statistic.resultInputs()
    val optionalInput = statistic.resultOptionalInputs()
    val output = statistic.resultOutputs()
    val redu = statistic.resultRedundant()
    val buildCost = statistic.resultBuildMaterials()

    inputs.clear()
    showInput = false
    optionalInputs.clear()
    showOptional = false
    outputs.clear()
    showOutput = false
    redundant.clear()
    showRedundant = false
    buildCosts.clear()
    showCosts = false

    if (input.any()) {
      inputs.buildInputs(input)
      showInput = true
    }
    if (optionalInput.any()) {
      optionalInputs.buildInputs(optionalInput)
      showOptional = true
    }
    if (output.any()) {
      outputs.buildOutput(output)
      showOutput = true
    }
    if (redu.any()) {
      redundant.buildOutput(redu)
      showRedundant = true
    }
    if (buildCost.any()) {
      buildCosts.buildBuildCost(buildCost)
      showCosts = true
    }
  }

  fun show(statistic: RecipeStatistic): Dialog {
    buildWith(statistic)
    return show()
  }

  private fun Table.buildInputs(input: List<List<RecipeItemStack<*>>>) {
    val united = AmountFormatter.unitTimedFormatter()
    input.forEach { items ->
      table(Consts.darkGrayUIAlpha) { rec ->
        rec.left()
        rec.pane { group ->
          var first = true
          items.forEach { stack ->
            val item = stack.item
            val name = item.localizedName
            val formatter = SpecialFormatters.getFormatter(item, united)
            val amount = formatter.format(stack.amount)

            if (!first) group.add("/", Styles.outlineLabel).pad(4f)
            group.itemIcon(stack).size(36f).pad(4f)
            group.add(name, Styles.outlineLabel).padLeft(6f)
            group.add(amount, Styles.outlineLabel).color(Color.lightGray).padLeft(6f)
            first = false
          }
        }.left().fill()
      }.left().fillY().growX().pad(8f).margin(8f)
      row()
    }
  }

  private fun Table.buildOutput(output: List<RecipeItemStack<*>>) {
    val united = AmountFormatter.unitTimedFormatter()
    output.forEach { stack ->
      table(Consts.darkGrayUIAlpha) { rec ->
        rec.left()

        val item = stack.item
        val name = item.localizedName
        val icon = item.icon
        val formatter = SpecialFormatters.getFormatter(item, united)
        val amount = formatter.format(stack.amount)

        rec.image(icon).size(36f).pad(4f).scaling(Scaling.fit)
        rec.add(name, Styles.outlineLabel).padLeft(6f)

        rec.add(amount, Styles.outlineLabel).color(Color.lightGray).padLeft(6f)
      }.left().fillY().growX().pad(8f).margin(8f)
      row()
    }
  }

  private fun Table.buildBuildCost(buildCost: List<RecipeItemStack<*>>) {

  }

  private fun Table.itemIcon(stack: RecipeItemStack<*>) = stack(
    Table {
      it.image(stack.item.icon).grow().scaling(Scaling.fit)
    },
    Table {
      it.left().bottom()
      it.add(formatAmount(stack), Styles.outlineLabel)
        .apply {
          fontScale(0.8f)
        }
      it.pack()
    },
    Table {
      if (!stack.item.locked) return@Table
      it.right().top().defaults().right().bottom().pad(4f)
      it.image(Icon.lock).scaling(Scaling.fit).size(10f).color(Color.lightGray)
    }
  ).grow().pad(5f)

  private fun Table.sepLine(
    icon: Drawable,
    text: String,
    color: Color = Pal.accent,
    show: Boolp,
  ){
    collapser({ t ->
      t.left().defaults().left()
      t.table {
        it.image(icon).size(32f).pad(6f)
        it.add(text).labelAlign(Align.left)
      }
      t.row()
      t.image().color(color).growX().height(4f).pad(0f).padBottom(6f)
    }, false, show).left().growX()
  }

  private fun formatAmount(stack: RecipeItemStack<*>): String =
    SpecialFormatters.getFormatter(stack.item, stack.amountFormat)
      .format(stack.amount)
}