package tmi.ui

import arc.Core
import arc.files.Fi
import arc.func.Boolf
import arc.func.Cons
import arc.func.Cons2
import arc.input.KeyCode
import arc.scene.Element
import arc.scene.actions.Actions
import arc.scene.event.SceneEvent
import arc.scene.style.Drawable
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Button
import arc.scene.ui.Tooltip
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Align
import arc.util.Log
import arc.util.Tmp
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.invoke
import tmi.recipe.types.RecipeItem
import tmi.ui.designer.*
import tmi.util.CombinedKeys
import tmi.util.Consts
import tmi.util.vec4

object TmiUI {
  private val alignTable = intArrayOf(
    Align.topLeft, Align.top, Align.topRight,
    Align.left, Align.center, Align.right,
    Align.bottomLeft, Align.bottom, Align.bottomRight,
  )
  private val alignIcon = arrayOf(
    Core.atlas.drawable("tmi-ui-top-left"), Core.atlas.drawable("tmi-ui-top"), Core.atlas.drawable("tmi-ui-top-right"),
    Core.atlas.drawable("tmi-ui-left"), Core.atlas.drawable("tmi-ui-center"), Core.atlas.drawable("tmi-ui-right"),
    Core.atlas.drawable("tmi-ui-bottom-left"), Core.atlas.drawable("tmi-ui-bottom"), Core.atlas.drawable("tmi-ui-bottom-right"),
  )

  @JvmStatic
  val recipesDialog by lazy { RecipesDialog() }
  @JvmStatic
  val schematicDesigner by lazy { SchematicDesignerDialog() }

  fun init() {
    setDefaultMenuTabs()
    setDefaultViewSideTools()
    setDefaultViewTabs()
    recipesDialog.build()
    schematicDesigner.build()
  }

  private fun setDefaultMenuTabs(){
    val save = fun(currPage: DesignerView, file: Fi): Boolean{
      try {
        file.writes().apply {
          currPage.write(this)
          close()
        }

        currPage.makeSaved()

        return true
      } catch (e: Exception) {
        Vars.ui.showException(e)
        Log.err(e)

        return false
      }
    }

    schematicDesigner.topMenuTabSet.addAll(
      // files
      MenuTab(
        Core.bundle["misc.new"], "file",
        group = "fileIO"
      ){
        hideMenu()
        createNewPage()
      },
      MenuTab(
        Core.bundle["misc.open"], "file", Icon.fileSmall,
        group = "fileIO"
      ){
        hideMenu()
        openFile()
      },
      MenuTab(
        Core.bundle["misc.save"], "file", Icon.saveSmall,
        group = "fileIO",
        valid = { currPage != null },
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.s),
      ){
        hideMenu()
        val currPage = currPage!!
        if (currPage.fi != null) {
          if (currPage.shouldSave()) save(currPage.view, currPage.fi!!)
        }
        else {
          Vars.platform.showFileChooser(false, currPage.title, "shd") { file ->
            if (save(currPage.view, file)) {
              currPage.fi = file
              currPage.title = file.nameWithoutExtension()
            }
          }
        }
      },
      MenuTab(
        Core.bundle["misc.saveAs"], "file",
        group = "fileIO",
        valid = { currPage != null },
        keyBind = CombinedKeys(KeyCode.altLeft, KeyCode.s),
      ){
        hideMenu()
        val page = currPage!!
        Vars.platform.showFileChooser(false, page.title, "shd") { file ->
          if (save(page.view, file)) {
            page.fi = file
            page.title = file.nameWithoutExtension()
          }
        }
      },
      MenuTab(
        Core.bundle["misc.saveAll"], "file", Icon.saveSmall,
        group = "fileIO",
        valid = { currPage != null },
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.shiftLeft, KeyCode.s),
      ){
        hideMenu()
        getPages().forEach { if (it.fi != null) save(it.view, it.fi!!) }
      },
      MenuTab(
        Core.bundle["misc.export"], "file",
        group = "export",
        valid = { currPage != null && (currPage!!.view.cards.any() || currPage!!.view.foldCards.any()) },
        subTabs = arrayOf(
          MenuTab(Core.bundle["misc.exportImg"], "file", Icon.imageSmall){
            ExportDialog(this.currPage!!.view).show()
          },
          MenuTab(Core.bundle["misc.exportText"], "file", Icon.fileTextSmall){},
          MenuTab(Core.bundle["misc.exportStat"], "file", Icon.bookSmall){},
        )
      ),
      MenuTab(
        Core.bundle["misc.settings"], "file", Icon.settingsSmall,
        group = "settings",
        valid = { false },
      ){
        hideMenu()
        //TODO
      },

      // edits
      MenuTab(
        Core.bundle["misc.undo"], "edit", Icon.undoSmall,
        valid = { currPage != null && currPage!!.view.canUndo() },
        group = "history",
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.z),
      ){
        hideMenu()
        currPage!!.view.undoHistory()
      },
      MenuTab(
        Core.bundle["misc.redo"], "edit", Icon.redoSmall,
        valid = { currPage != null && currPage!!.view.canRedo() },
        group = "history",
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.shiftLeft, KeyCode.z),
      ){
        hideMenu()
        currPage!!.view.redoHistory()
      },
      MenuTab(
        Core.bundle["misc.copy"], "edit", Icon.copySmall,
        valid = { currPage != null && currPage!!.view.selects.any() },
        group = "clipboard",
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.c),
      ){
        hideMenu()
        setClipboard(currPage!!.view.selects.toList())
      },
      MenuTab(
        Core.bundle["misc.clip"], "edit", Consts.clip,
        valid = { currPage != null && currPage!!.view.selects.any() },
        group = "clipboard",
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.x),
      ){
        hideMenu()
        val l = currPage!!.view.selects.toList()
        setClipboard(l)
        currPage!!.view.pushHandle(RemoveCardHandle(currPage!!.view, l))
      },
      MenuTab(
        Core.bundle["misc.paste"], "edit", Icon.copySmall,
        valid = { currPage != null && !clipboardEmpty() },
        group = "clipboard",
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.v),
      ){
        hideMenu()
        vec4.set(clipboardCenter.add(45f, -45f))
        val v = currPage!!.view
        val l = getClipboard().toList()
        v.stageToLocalCoordinates(vec4)
        l.forEach { it.moveBy(vec4.x, vec4.y) }
        v.pushHandle(AddCardsHandle(v, l))
        v.clearEmphasize()
      },
      MenuTab(
        Core.bundle["misc.addCard"], "edit",
        group = "handle",
        valid = { currPage != null },
        subTabs = arrayOf(
          MenuTab(
            Core.bundle["misc.addRecipeCard"], "edit",
            valid = { currPage != null },
          ){
            hideMenu()

            val view = currPage!!.view
            recipesDialog.toggle = Cons { r ->
              recipesDialog.hide()
              view.addRecipe(r).gridAlign(view.cardAlign)
            }
            recipesDialog.show()
          },
          MenuTab(
            Core.bundle["misc.addInputCard"], "edit", Icon.downloadSmall,
            valid = { currPage != null },
          ){
            hideMenu()

            val view = currPage!!.view
            view.addCard(IOCard(view, true))
          },
          MenuTab(
            Core.bundle["misc.addOutputCard"], "edit", Icon.uploadSmall,
            valid = { currPage != null },
          ){
            hideMenu()

            val view = currPage!!.view
            view.addCard(IOCard(view, false))
          },
        )
      ),
      MenuTab(
        Core.bundle["misc.remove"], "edit", Icon.trashSmall,
        group = "handle",
        valid = { currPage != null && currPage!!.view.cards.any() },
        keyBind = CombinedKeys(KeyCode.del)
      ){
        hideMenu()
        val view = currPage!!.view
        view.pushHandle(RemoveCardHandle(view, view.selects.toList()))
      },
      MenuTab(
        Core.bundle["misc.allSelect"], "edit",
        group = "handle",
        valid = { currPage != null },
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.a)
      ){
        hideMenu()
        currPage!!.view.selects.addAll(currPage!!.view.cards)
      },
      MenuTab(
        Core.bundle["dialog.calculator.switchToComp"], "edit",
        group = "handle",
        valid = { currPage != null && !currPage!!.view.selects.any { !it.isSimplified } },
        keyBind = CombinedKeys(KeyCode.altLeft, KeyCode.a)
      ){
        hideMenu()
        currPage!!.view.apply {
          pushHandle(SwitchSimplifiedHandle(this, selects.toList(), false))
        }
      },
      MenuTab(
        Core.bundle["dialog.calculator.switchToSimple"], "edit",
        group = "handle",
        valid = { currPage != null && !currPage!!.view.selects.any { it.isSimplified } },
        keyBind = CombinedKeys(KeyCode.altLeft, KeyCode.s)
      ){
        hideMenu()
        currPage!!.view.apply {
          pushHandle(SwitchSimplifiedHandle(this, selects.toList(), true))
        }
      },
      MenuTab(
        Core.bundle["misc.alignSize"], "edit", Icon.resizeSmall,
        group = "handle",
        valid = { currPage != null && currPage!!.view.selects.any { !it.isSizeAlign } },
      ){
        hideMenu()
        currPage!!.view.apply { pushHandle(CardSizeAlignHandle(
          this,
          true,
          selects.toList().filter { !it.isSizeAlign }
        )) }
      },
      MenuTab(
        Core.bundle["misc.unAlignSize"], "edit", Icon.diagonalSmall,
        group = "handle",
        valid = { currPage != null && currPage!!.view.selects.any { it.isSizeAlign } },
      ){
        hideMenu()
        currPage!!.view.apply { pushHandle(CardSizeAlignHandle(
          this,
          false,
          selects.toList().filter { it.isSizeAlign }
        )) }
      },
      MenuTab(
        Core.bundle["misc.standardize"], "edit", Icon.refreshSmall,
        group = "handle",
        valid = { currPage != null }
      ){
        hideMenu()
        currPage!!.view.standardization()
      },

      // analyze
      MenuTab(
        Core.bundle["misc.stat"], "analyze",
        valid = { currPage != null }
      ){
        hideMenu()
      },
      MenuTab(
        Core.bundle["misc.simulate"], "analyze",
        valid = { currPage != null }
      ){
        hideMenu()
      },
      MenuTab(
        Core.bundle["misc.buildSteer"], "analyze", Icon.bookSmall,
        group = "game",
        valid = { currPage != null }
      ){
        hideMenu()
      },

      // view
      MenuTab(
        Core.bundle["misc.closeAllPage"], "view",
        group = "pages",
        valid = { getPages().any() }
      ){
        hideMenu()
        getPages().toList().also { closePages(this, it, save) }
      },
      MenuTab(
        Core.bundle["misc.closeOtherPage"], "view",
        group = "pages",
        valid = { getPages().any { it != currPage } }
      ){
        hideMenu()
        getPages().filter { it != currPage }.also { closePages(this, it, save) }
      },
      MenuTab(
        Core.bundle["misc.closeAllSaved"], "view",
        group = "pages",
        valid = { getPages().any { !it.shouldSave() } }
      ){
        hideMenu()
        getPages().filter { !it.shouldSave() }.forEach { deletePage(it) }
      },
      MenuTab(
        Core.bundle["misc.resetView"], "view", Icon.refreshSmall,
        group = "view",
        valid = { currPage != null }
      ){
        hideMenu()
        val view = currPage!!.view
        view.resetView()
      },

      // help
      MenuTab(
        Core.bundle["misc.designerHelp"], "help", Icon.bookSmall
      ){
        hideMenu()
      },
      MenuTab(
        Core.bundle["misc.about"], "help", Icon.infoSmall
      ){
        hideMenu()
      },
    )
  }

  private fun closePages(
    designer: SchematicDesignerDialog,
    viewPages: List<SchematicDesignerDialog.ViewPage>,
    save: (DesignerView, Fi) -> Boolean,
  ) {
    if (viewPages.any { it.shouldSave() }) {
      showChoiceIcons(
        Core.bundle["misc.someUnsaved"],
        Core.bundle["misc.ensureClose"],
        true,
        Core.bundle["misc.saveAll"] to Icon.save to Runnable {
          viewPages.forEach {
            if (it.shouldSave()) {
              if (it.fi != null) {
                save(it.view, it.fi!!)
                designer.deletePage(it)
              }
              else {
                Vars.platform.showFileChooser(false, it.title, "shd") { file ->
                  save(it.view, file)
                  designer.deletePage(it)
                }
              }
            }
          }
        },
        Core.bundle["misc.close"] to Icon.cancel to Runnable {
          viewPages.forEach { designer.deletePage(it) }
        },
      )
    }
    else viewPages.forEach { designer.deletePage(it) }
  }

  private fun setDefaultViewSideTools() {
    schematicDesigner.sideToolTabs.addAll(
      ToolTab(Core.bundle["dialog.calculator.add"], Icon.add) { v, _ ->
        recipesDialog.toggle = Cons { r ->
          recipesDialog.hide()
          v?.addRecipe(r)
        }
        recipesDialog.show()
      },
      ToolTab(Core.bundle["misc.undo"], Icon.undo) { v, _ -> v?.undoHistory() },
      ToolTab(Core.bundle["misc.redo"], Icon.redo) { v, _ -> v?.redoHistory() },
      ToolTab(Core.bundle["dialog.calculator.standard"], Icon.refresh) { v, _ -> v?.standardization() },
      ToolTab(
        Core.bundle["dialog.calculator.align"],
        { it?.currAlignIcon?: Icon.none }
      ) { v, b ->
        (v?.parentDialog ?: return@ToolTab).apply {
          if (menuShown()) {
            hideMenu()
          }
          else {
            showMenu(b, Align.right, Align.left, true) { t ->
              t.table(Tex.paneLeft) { ta ->
                for (i in alignTable.indices) {
                  val align = alignTable[i]
                  ta.button(alignIcon[i], Styles.clearNoneTogglei, 32f) {
                    val view = currPage!!.view
                    if (view.cardAlign == align) {
                      view.cardAlign = -1
                      view.currAlignIcon = Icon.none
                    }
                    else {
                      view.cardAlign = align
                      view.currAlignIcon = alignIcon[i]
                    }
                  }.size(40f).checked { currPage!!.view.cardAlign == align }

                  if ((i + 1)%3 == 0) ta.row()
                }
              }.fill()
            }
          }
        }
      },
      ToolTab(
        Core.bundle["dialog.calculator.selecting"],
        Icon.resize,
        { it?.selectMode?:false }
      ){ v, _ ->
        val view = v?:return@ToolTab
        view.selectMode = !view.selectMode
        if (!view.selectMode) view.selects.clear()
      },
      //SideBtn(Core.bundle["dialog.calculator.exportIMG"], Icon.export) { export.show() },
      ToolTab(
        Core.bundle["dialog.calculator.delete"],
        Icon.trash,
        { it?.removeMode?: false }
      ) { v, _ ->
        val view = v?:return@ToolTab
        val dialog = view.parentDialog
        view.removeMode = !view.removeMode
        dialog.removeArea!!.clearActions()
        if (view.removeMode) {
          dialog.removeArea!!.actions(
            Actions.parallel(
              Actions.sizeTo(dialog.removeArea!!.width, Core.scene.height*0.15f, 0.12f),
              Actions.alpha(0.6f, 0.12f)
            )
          )
        }
        else dialog.removeArea!!.actions(
          Actions.parallel(
            Actions.sizeTo(dialog.removeArea!!.width, 0f, 0.12f),
            Actions.alpha(0f, 0.12f)
          )
        )
      },

      ToolTab(
        Core.bundle["dialog.calculator.lock"],
        { v -> Icon.lock.takeIf { v?.editLock == true }?: Icon.lockOpen },
        { it?.editLock?: false },
      ){ v, _ -> v?.editLock = !(v?.editLock?:false) }
    )
  }

  private fun setDefaultViewTabs() {
    val vec = Tmp.v1
    schematicDesigner.viewMenuTabs.addAll(
      ViewTab(
        title = Core.bundle["misc.remove"],
        icon = Icon.trashSmall,
        clicked = { _, _, v, _ ->
          hideMenu()
          v.pushHandle(RemoveCardHandle(v, v.selects.toList()))
          v.selects.clear()
        },
        group = "edit",
        filter = { _, _, v, s -> s is Card || !v.selects.isEmpty },
      ),
      ViewTab(
        title = Core.bundle["misc.fold"],
        icon = Icon.layersSmall,
        clicked = { _, _, v, _ ->
          hideMenu()
          v.pushHandle(FoldCardHandle(v, v.selects.toList(), true))
        },
        group = "edit",
        filter = { _, _, _, s -> s is Card && !s.isFold },
      ),
      ViewTab(
        title = Core.bundle["misc.copy"],
        icon = Icon.copySmall,
        clicked = { _, _, v, _ ->
          hideMenu()
          setClipboard(v.selects)
          v.selects.clear()
        },
        group = "edit",
        filter = { _, _, v, s -> s is Card || !v.selects.isEmpty }
      ),
      ViewTab(
        title = Core.bundle["misc.paste"],
        icon = Icon.pasteSmall,
        clicked = { x, y, v, _ ->
          hideMenu()
          val l = getClipboard().toList()
          vec.set(x, y)
          v.container.stageToLocalCoordinates(vec)
          l.forEach { it.moveBy(vec.x, vec.y) }
          v.pushHandle(AddCardsHandle(v, l))
          v.clearEmphasize()
        },
        group = "edit",
        valid = { _, _, _, _ -> !clipboardEmpty() }
      ),
      ViewTab(
        title = Core.bundle["dialog.calculator.switchToComp"],
        icon = Icon.diagonalSmall,
        clicked = { _, _, v, _ ->
          hideMenu()
          v.pushHandle(SwitchSimplifiedHandle(v, v.selects.toList(), false))
        },
        group = "cards",
        filter = { _, _, v, _ ->
          v.selects.any() && v.selects.all { it.isSimplified }
        }
      ),
      ViewTab(
        title = Core.bundle["dialog.calculator.switchToSimple"],
        icon = Icon.diagonalSmall,
        clicked = { _, _, v, _ ->
          hideMenu()
          v.pushHandle(SwitchSimplifiedHandle(v, v.selects.toList(), true))
        },
        group = "cards",
        filter = { _, _, v, _ ->
          v.selects.any() && v.selects.all { !it.isSimplified }
        }
      ),
      ViewTab(
        title = Core.bundle["dialog.calculator.unAlignSize"],
        icon = Icon.diagonalSmall,
        clicked = { _, _, v, _ ->
          hideMenu()
          v.pushHandle(CardSizeAlignHandle(v, false, v.selects.toList().filter { it.isSizeAlign }))
        },
        group = "cards",
        filter = { _, _, v, _ -> v.selects.any { it.isSizeAlign } }
      ),
      ViewTab(
        title = Core.bundle["dialog.calculator.sizeAlign"],
        icon = Icon.resizeSmall,
        clicked = { _, _, v, _ ->
          hideMenu()
          v.pushHandle(CardSizeAlignHandle(v, true, v.selects.toList().filter { !it.isSizeAlign }))
        },
        group = "cards",
        filter = { _, _, v, _ -> v.selects.any() && v.selects.none { it.isSizeAlign } }
      ),
      ViewTab(
        title = Core.bundle["dialog.calculator.removeLinker"],
        icon = Icon.trashSmall,
        clicked = { _, _, v, linker ->
          hideMenu()
          if (linker !is ItemLinker) return@ViewTab

          v.pushHandle(RemoveLinkerHandle(v, linker))
        },
        group = "linkers",
        filter = { _, _, _, l -> l is ItemLinker }
      ),
      ViewTab(
        title = Core.bundle["dialog.calculator.addRecipe"],
        icon = Icon.bookSmall,
        clicked = { x, y, v, _ ->
          hideMenu()
          recipesDialog.toggle = Cons { r ->
            recipesDialog.hide()
            v.localToDescendantCoordinates(v.container, vec.set(x, y))
            v.alignCard(v.addRecipe(r), vec.x, vec.y, v.cardAlign)
          }
          recipesDialog.show()
        }
      ),
      ViewTab(
        title = Core.bundle["dialog.calculator.addInput"],
        icon = Icon.downloadSmall,
        clicked = { x, y, v, _ ->
          hideMenu()
          val c = IOCard(v, true)
          v.addIO(c)
          vec.set(x, y)
          v.container.stageToLocalCoordinates(vec)
          v.alignCard(c, vec.x, vec.y, v.cardAlign)
        }
      ),
      ViewTab(
        title = Core.bundle["dialog.calculator.addOutput"],
        icon = Icon.uploadSmall,
        clicked = { x, y, v, _ ->
          hideMenu()
          val c = IOCard(v, false)
          v.addIO(c)
          vec.set(x, y)
          v.container.stageToLocalCoordinates(vec)
          v.alignCard(c, vec.x, vec.y, v.cardAlign)
        }
      ),
    )
  }

  fun buildItems(
    items: Table,
    list: Seq<RecipeItem<*>>,
    buttonAction: Cons2<RecipeItem<*>, Button>? = null,
    callBack: Cons<RecipeItem<*>>
  ) {
    var i = 0
    var reverse = false
    var search = ""
    var rebuild = {}

    items.table { top ->
      top.image(Icon.zoom).size(32f)
      top.field("") { str ->
        search = str
        rebuild()
      }.growX()

      top.button(Icon.none, Styles.clearNonei, 36f) {
        i = (i + 1)%recipesDialog.sortings.size
        rebuild()
      }.margin(2f).update { b -> b.style.imageUp = recipesDialog.sortings[i].icon }.get()
        .addListener(Tooltip{ t ->
          t.table(Tex.paneLeft){
            it.add("").update { l -> l.setText(recipesDialog.sortings[i].localized) }
          }
        }.also { it.allowMobile = true })
      top.button(Icon.none, Styles.clearNonei, 36f) {
        reverse = !reverse
        rebuild()
      }.margin(2f).update { b -> b.style.imageUp = if (reverse) Icon.up else Icon.down }
    }.growX()
    items.row()

    items.table{ pane ->
      pane.left().top().pane(Styles.smallPane) { cont ->
        rebuild = {
          cont.clearChildren()
          var ind = 0

          val sorting = recipesDialog.sortings[i].sort
          val ls: List<RecipeItem<*>> = list.toList()
            .filter { e -> (e.name.contains(search) || e.localizedName.contains(search)) }
            .sortedWith(
              if (reverse) java.util.Comparator { a, b -> sorting.compare(b, a) }
              else sorting
            )

          ls.forEach { item ->
            if (item.locked || item.hidden) return@forEach

            val b = cont.button(TextureRegionDrawable(item.icon), Styles.clearNonei, 32f) {
              callBack[item]
            }.margin(4f).tooltip(item.localizedName).get()
            buttonAction?.invoke(item, b)

            if (ind++%8 == 7) {
              cont.row()
            }
          }
        }
        rebuild()
      }.padTop(6f).padBottom(4f).fill()
    }.height(400f).growX()
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

    if (closeButton) dialog.buttons.button("@cancel", Icon.cancel) { dialog.hide() }
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

