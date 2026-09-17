package tmi.ui

import arc.Core
import arc.func.Boolf
import arc.input.KeyCode
import arc.scene.Element
import arc.scene.event.SceneEvent
import arc.scene.style.Drawable
import arc.util.Align
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.ui.FileChooser
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.ui.calculator.CalculatorDialog
import tmi.ui.calculator.CalculatorDialog.Companion.exportDialog
import tmi.ui.calculator.CalculatorDialog.Companion.showCalculatorHelp
import tmi.ui.calculator.CalculatorDialog.MenuTab
import tmi.ui.calculator.CalculatorDialog.ToolTab
import tmi.ui.calculator.CalculatorTransactions
import tmi.ui.calculator.RecipeGraphNode
import tmi.util.CombinedKeys
import tmi.util.Consts
import tmi.util.KeyBinds

object TmiUI {
  @JvmStatic
  val recipesDialog by lazy { RecipesDialog() }
  @JvmStatic
  val recipeGraph by lazy { CalculatorDialog() }
  @JvmStatic
  val document by lazy { DocumentDialog() }

  fun init() {
    recipesDialog.build()

    recipeGraph.setupTools()
    recipeGraph.setupMenu()
    recipeGraph.build()
  }

  private fun CalculatorDialog.setupTools() {
    addTool(
      ToolTab(
        Core.bundle["dialog.calculator.addRecipe"],
        Icon.add,
        disabled = { it != null },
      ){ v, _ ->
        v!!
        recipesDialog.showWith {
          callbackRecipe(Icon.add) { rec ->
            v.commitTransaction(
              CalculatorTransactions.AddRecipeCard(v, RecipeGraphNode(rec))
            )
            hide()
          }
          showDoubleRecipe(true)
        }
      },
      ToolTab(
        { v -> Core.bundle[if (v?.browsMode?:false) "dialog.calculator.browseMode" else "dialog.calculator.editMode"] },
        { v -> if (v?.browsMode?:false) Icon.zoom else Icon.pencil },
        disabled = { it != null },
      ){ v, _ ->
        v!!.browsMode = !v.browsMode
      },
      ToolTab(
        Core.bundle["dialog.calculator.showGrid"],
        { v -> if (v?.showGrid?:true) Consts.showGrid else Consts.hideGrid },
        disabled = { it != null },
      ){ v, _ ->
        v!!.showGrid = !v.showGrid
      },
      ToolTab(
        Core.bundle["dialog.calculator.autoLink"],
        {
          it?.let { v ->
            when {
              v.autoLinkInput && v.autoLinkOutput -> Consts.autolinkAll
              v.autoLinkInput && !v.autoLinkOutput -> Consts.autolinkInputs
              !v.autoLinkInput && v.autoLinkOutput -> Consts.autolinkOutputs
              else -> Consts.autolinkOff
            }
          }?: Consts.autolinkAll
        },
        disabled = { it != null },
      ){ v, b ->
        v!!
        showMenu(b, Align.topRight){ tab ->
          tab.table(Consts.padDarkGrayUI) { m ->
            m.left().defaults().growX().fillY().minWidth(240f).left()

            m.button(Core.bundle["autolink.all"], Consts.autolinkAll, Styles.clearTogglet) {
              v.autoLinkInput = true
              v.autoLinkOutput = true
            }.margin(8f).update { it.isChecked = v.autoLinkInput && v.autoLinkOutput }

            m.row()
            m.button(Core.bundle["autolink.inputs"], Consts.autolinkInputs, Styles.clearTogglet) {
              v.autoLinkInput = true
              v.autoLinkOutput = false
            }.margin(8f).update { it.isChecked = v.autoLinkInput && !v.autoLinkOutput }

            m.row()
            m.button(Core.bundle["autolink.outputs"], Consts.autolinkOutputs, Styles.clearTogglet) {
              v.autoLinkInput = false
              v.autoLinkOutput = true
            }.margin(8f).update { it.isChecked = !v.autoLinkInput && v.autoLinkOutput }

            m.row()
            m.button(Core.bundle["autolink.off"], Consts.autolinkOff, Styles.clearTogglet) {
              v.autoLinkInput = false
              v.autoLinkOutput = false
            }.margin(8f).update { it.isChecked = !v.autoLinkInput && !v.autoLinkOutput }
          }
        }
      },
    )
  }

  private fun CalculatorDialog.setupMenu(){
    addMenu(
      // files
      MenuTab(
        Core.bundle["misc.new"], "file",
        group = "open"
      ){
        createNewPage()
      },
      MenuTab(
        Core.bundle["misc.open"], "file", Icon.fileSmall,
        group = "open"
      ){
        openFile()
      },
      MenuTab(
        Core.bundle["misc.export"], "file",
        group = "export",
        valid = { currPage != null && currPage!!.view.graph.any() },
        subTabs = arrayOf(
          MenuTab(Core.bundle["misc.exportImg"], "file", Icon.imageSmall){
            exportDialog.show(it!!.view)
          },
          MenuTab(Core.bundle["misc.exportText"], "file", Icon.fileTextSmall){
            //TODO
          },
          MenuTab(Core.bundle["misc.exportStat"], "file", Icon.bookSmall){
            //TODO
          },
        )
      ),
      MenuTab(
        Core.bundle["misc.save"], "file", Icon.saveSmall,
        group = "save",
        valid = { it != null },
        keyBind = KeyBinds.save,
      ){ currPage ->
        val page = currPage!!
        if (page.fi != null) {
          if (page.shouldSave()) save(page, page.fi!!)
        }
        else {
          FileChooser.FileChooserParams()
            .title(page.title)
            .extensions("shd")
            .submit { file ->
              if (save(page, file)) {
                page.fi = file
                page.title = file.nameWithoutExtension()
              }
            }
        }
      },
      MenuTab(
        Core.bundle["misc.saveAs"], "file",
        group = "save",
        valid = { currPage != null },
        keyBind = KeyBinds.saveAs,
      ){ currPage ->
        val page = currPage!!
        FileChooser.FileChooserParams()
          .title(page.title)
          .extensions("shd")
          .submit { file ->
            if (save(page, file)) {
              page.fi = file
              page.title = file.nameWithoutExtension()
            }
          }
      },
      MenuTab(
        Core.bundle["misc.saveAll"], "file", Icon.saveSmall,
        group = "save",
        valid = { currPage != null },
        keyBind = KeyBinds.saveAll,
      ){
        pages.forEach { it.fi?.also { f -> it.view.save(f) } }
      },

      // edit
      MenuTab(
        Core.bundle["dialog.calculator.undo"], "edit", Icon.undo,
        group = "history",
        keyBind = KeyBinds.undo,
        valid = { currPage?.view?.canUndo == true }
      ){ currPage ->
        currPage!!.view.undo()
      },
      MenuTab(
        Core.bundle["dialog.calculator.redo"], "edit", Icon.redo,
        group = "history",
        keyBind = KeyBinds.redo,
        valid = { currPage?.view?.canRedo == true }
      ){ currPage ->
        currPage!!.view.redo()
      },

      // view
      MenuTab(
        Core.bundle["dialog.calculator.refresh"], "view", Icon.refreshSmall,
        group = "normal",
        keyBind = CombinedKeys(KeyCode.f5),
        valid = { currPage != null }
      ){ currPage ->
        currPage!!.view.graphUpdated()
      },
      MenuTab(
        Core.bundle["misc.closeAllPage"], "view",
        group = "pages",
        valid = { pages.any() }
      ){
        pages.toList().also { closePages(it) }
      },
      MenuTab(
        Core.bundle["misc.closeOtherPage"], "view",
        group = "pages",
        valid = { pages.any { it != currPage } }
      ){
        pages.filter { it != currPage }.also { closePages(it) }
      },
      MenuTab(
        Core.bundle["misc.closeAllSaved"], "view",
        group = "pages",
        valid = { pages.any { !it.shouldSave() } }
      ){
        pages.filter { !it.shouldSave() }.forEach { deletePage(it) }
      },
      MenuTab(
        Core.bundle["misc.resetView"], "view", Icon.refreshSmall,
        group = "view",
        valid = { currPage != null }
      ){ currPage ->
        currPage!!.view.resetView()
      },

      // help
      MenuTab(
        Core.bundle["misc.calculatorHelp"], "help", Icon.infoSmall
      ){
        showCalculatorHelp()
      },
      MenuTab(
        Core.bundle["misc.about"], "help",
        valid = { false }
      ){
        //TODO
      },
    )
  }

  @JvmStatic
  fun showChoice(title: String, text: String, closeButton: Boolean = true, vararg options: Pair<String, Runnable>) {
    showChoiceIcons(title, text, closeButton, *options.map { Pair(it.first, null) to it.second  }.toTypedArray() )
  }

  @JvmStatic
  fun showChoiceIcons(title: String, text: String, closeButton: Boolean = true, vararg options: Pair<Pair<String, Drawable?>, Runnable>) {
    val dialog = BaseDialog(title)
    dialog.cont.add(text).width(if (Vars.mobile) 400f else 500f).wrap().pad(4f).get()
      .setAlignment(Align.center, Align.center)
    dialog.buttons.defaults().size(200f, 54f).pad(2f)
    dialog.setFillParent(false)

    options.forEach {
      if (it.first.second == null){
        dialog.buttons.button(it.first.first) {
          dialog.hide()
          it.second.run()
        }
      }
      else {
        dialog.buttons.button(it.first.first, it.first.second) {
          dialog.hide()
          it.second.run()
        }
      }
    }
    if (closeButton) dialog.buttons.button("@cancel", Icon.cancel) { dialog.hide() }

    dialog.keyDown(KeyCode.escape) { dialog.hide() }
    dialog.keyDown(KeyCode.back) { dialog.hide() }
    dialog.show()
  }
}

fun Element.addEventBlocker(
  capture: Boolean = false,
  isCancel: Boolean = false,
  filter: Boolf<SceneEvent> = Boolf{ true }
){
  (this::addCaptureListener.takeIf{ capture }?: this::addListener){ event ->
    if (event != null && filter.get(event)) {
      if (isCancel) event.cancel()
      else event.stop()
    }
    false
  }
}

