package tmi.ui.calculator

import arc.Core
import arc.files.Fi
import arc.func.*
import arc.graphics.Color
import arc.input.KeyCode
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.Group
import arc.scene.event.ClickListener
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.style.Drawable
import arc.scene.ui.Button
import arc.scene.ui.Label
import arc.scene.ui.Tooltip
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Align
import arc.util.Log
import arc.util.Scaling
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.ui.TmiUI
import tmi.ui.TmiUI.showChoiceIcons
import tmi.ui.addEventBlocker
import tmi.util.*
import universecore.ui.elements.markdown.Markdown
import universecore.ui.elements.markdown.MarkdownStyles
import kotlin.math.max

class CalculatorDialog: BaseDialog("") {
  companion object{
    val exportDialog = ExportDialog()
  }

  private var menuFolded = true
  private var showTips = false
  private var lastTip: TipsProvider? = null

  private val sideToolTabs = Seq<ToolTab>()
  private val topMenuTabSet = Seq<MenuTab>()
  private val menuHiddens = Seq<Runnable>()

  private val keyBinds = CombineKeyTree<Runnable>()

  private val pages = Seq<ViewPage>()
  private val recentPages = Seq<ViewPage>()
  private var currPage: ViewPage? = null

  private lateinit var topTable: Table
  private lateinit var tipsTable: Table
  private lateinit var viewTable: Table
  private lateinit var sideTable: Table

  private val menuTable = Table().apply{ visible = false }

  init {
    setupTools()
    setupMenu()

    loadRecentPages()
    setupMenuBinds()

    addListener(object: InputListener(){
      override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
        hideMenu()
        return false
      }
    })
  }

  private fun Element.findTips(x: Float, y: Float): TipsProvider? {
    if (this is Group) {
      val point = vec1
      val childrenArray = children.items
      for (i in children.size - 1 downTo 0) {
        val child = childrenArray[i]
        if (!child.visible || (child.cullable && cullingArea != null && !cullingArea.overlaps(
            child.x + child.translation.x,
            child.y + child.translation.y,
            child.width,
            child.height
          ))
        ) continue
        child.parentToLocalCoordinates(point.set(x, y))
        val hit = child.findTips(point.x, point.y)
        if (hit != null) return hit
        else if (child is TipsProvider && child.tipValid()
                 && point.x >= child.translation.x && point.x < child.width + child.translation.x
                 && point.y >= child.translation.y && point.y < child.height + child.translation.y) return child
      }
    }

    return if (this is TipsProvider && tipValid()
               && x >= translation.x && x < width + translation.x
               && y >= translation.y && y < height + translation.y) this else null
  }

  override fun act(delta: Float) {
    super.act(delta)

    val mouse = vec1.set(Core.input.mouse())
    stageToLocalCoordinates(mouse)

    val prov = findTips(mouse.x, mouse.y)
    if (prov != lastTip) {
      lastTip = prov
      prov?.let { provider ->
        setTip({ provider.getTip() }, provider.getTipStyle(), provider.getTipColor())
      } ?: run {
        hideTips()
      }
    }

    if (Core.scene.hasField()) return
    keyBinds.checkTap(Core.input)?.run()
  }

  private fun loadRecentPages(){
    recentPages.clear()
    val raw = Core.settings.getString("tmi-calculator-recent-pages", "")
    val entries = raw.split(";")

    entries.forEach {
      addRecentPage(Fi(it))
    }

    recentPages.removeAll { it.fi?.exists()?.let { b -> !b }?: true }
  }

  private fun addRecentPage(fi: Fi) {
    recentPages.removeAll { it.fi?.exists()?.let { b -> !b }?: true }

    if (!fi.exists()) return
    if (recentPages.contains { it.fi == fi }) return
    recentPages.add(ViewPage(fi, fi.nameWithoutExtension()){ CalculatorView() })

    Core.settings.put(
      "tmi-calculator-recent-pages",
      recentPages.joinToString(";") { it.fi!!.path() }
    )
  }

  private fun setupTools() {
    addTool(
      ToolTab(
        Core.bundle["dialog.calculator.addRecipe"],
        Icon.add,
        disabled = { it != null },
      ){ v, _ ->
        v!!
        TmiUI.recipesDialog.showWith {
          callbackRecipe(Icon.add) { rec ->
            val node = RecipeGraphNode(rec)
            v.graph.addNode(node)
            v.linkExisted(node)
            v.graphUpdated()
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

  private fun setupMenu(){
    addMenu(
      // files
      MenuTab(
        Core.bundle["misc.new"], "file",
        group = "fileIO"
      ){
        createNewPage()
      },
      MenuTab(
        Core.bundle["misc.open"], "file", Icon.fileSmall,
        group = "fileIO"
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
        group = "fileIO",
        valid = { it != null },
        keyBind = CombinedKeys(KeyCode.controlLeft, KeyCode.s),
      ){ currPage ->
        val page = currPage!!
        if (page.fi != null) {
          if (page.shouldSave()) save(page, page.fi!!)
        }
        else {
          Vars.platform.showFileChooser(false, page.title, "shd") { file ->
            if (save(page, file)) {
              page.fi = file
              page.title = file.nameWithoutExtension()
            }
          }
        }
      },
      MenuTab(
        Core.bundle["misc.saveAs"], "file",
        group = "fileIO",
        valid = { currPage != null },
        keyBind = CombinedKeys(KeyCode.altLeft, KeyCode.s),
      ){ currPage ->
        val page = currPage!!
        Vars.platform.showFileChooser(false, page.title, "shd") { file ->
          if (save(page, file)) {
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
        pages.forEach { it.fi?.also { f -> it.view.save(f) } }
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
        showHelp()
      },
      MenuTab(
        Core.bundle["misc.about"], "help",
        valid = { false }
      ){
        //TODO
      },
    )
  }

  private fun Markdown.MarkdownStyle.copy() = Markdown.MarkdownStyle().also { c ->
    c.font = font
    c.emFont = emFont
    c.subFont = subFont
    c.codeFont = codeFont
    c.strongFont = strongFont

    c.textColor = textColor
    c.emColor = emColor
    c.subTextColor = subTextColor
    c.lineColor = lineColor
    c.linkColor = lineColor

    c.linesPadding = linesPadding
    c.maxCodeBoxHeight = maxCodeBoxHeight
    c.tablePadHor = tablePadHor
    c.tablePadVert = tablePadVert
    c.paragraphPadding = paragraphPadding

    c.board = board
    c.codeBack = codeBack
    c.codeBlockBack = codeBlockBack
    c.tableBack1 = tableBack1
    c.tableBack2 = tableBack2
    c.curtain = curtain

    c.codeBlockStyle = codeBlockStyle
    c.listMarks = listMarks
  }

  private fun showHelp(){
    val docText = TmiAssets.getDocument("calculator-help.md")
    TmiUI.document.showDocument(
      Core.bundle["misc.calculatorHelp"],
      MarkdownStyles.defaultMD,
      docText
    )
  }

  fun setupMenuBinds() {
    keyBinds.clear()

    setBinds(topMenuTabSet)
  }

  private fun setBinds(topMenuTabSet: Iterable<MenuTab>) {
    topMenuTabSet.forEach { tab ->
      if (tab.subTabs != null) {
        setBinds(tab.subTabs)
      }
      else if (tab.keyBind != null) {
        keyBinds.putKeyBinding(tab.keyBind!!) { tab.clicked?.get(currPage) }
      }
    }
  }

  private fun closePages(
    viewPages: List<ViewPage>,
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
                it.view.save(it.fi!!)
                deletePage(it)
              }
              else {
                Vars.platform.showFileChooser(false, it.title, "shd") { file ->
                  it.view.save(file)
                  deletePage(it)
                }
              }
            }
          }
        },
        Core.bundle["misc.close"] to Icon.cancel to Runnable {
          viewPages.forEach { deletePage(it) }
        },
      )
    }
    else viewPages.forEach { deletePage(it) }
  }

  fun addTool(vararg toolTab: ToolTab) = also{
    sideToolTabs.add(toolTab)
  }

  fun addMenu(vararg menuTab: MenuTab) = also{
    topMenuTabSet.add(menuTab)
  }

  fun build(){
    titleTable.clear()

    margin(0f)
    getCell(cont).pad(0f)
    cont.background(Consts.grayUI)
    cont.defaults().pad(0f)
    cont.table{
      topTable = it.table(Consts.darkGrayUI).growX().fillY().get()
      it.row()
      it.table{ under ->
        sideTable = under.table(Consts.midGrayUI).fillX().growY().get()
        under.image().color(Pal.darkestGray).width(2f).growY().pad(0f)
        under.table{ v ->
          viewTable = v.table().grow().get().apply { clip = true }
          v.fill{ t ->
            t.bottom().table { tip ->
              tip.image().color(Pal.darkestGray).height(2f).growX().pad(0f)
              tip.row()
              tipsTable = tip.table(Consts.grayUI).left().growX().fillY().margin(6f).get()
            }.fillY().growX().visible { showTips }
          }
        }.grow()
      }.grow()
    }.grow()

    addChild(menuTable)

    buildTop()
    buildSide()
    buildView()
  }

  //Top bar
  private fun buildTop() {
    topTable.table{
      it.image(Consts.tmi).scaling(Scaling.fit).size(32f)
    }.growY().marginLeft(8f).marginRight(8f)

    val tabs = topMenuTabSet.groupBy { it.tabName }
      .map{ groups -> groups.key to groups.value.groupBy { it.group } }
      .toMap()

    var menuTable: Table? = null

    addListener(object : InputListener(){
      override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
        val elem = hit(x, y, false)
        if (elem != null && elem.isDescendantOf(menuTable)) return false

        menuFolded = true
        return false
      }
    })

    topTable.stack(Table{ def ->
      def.left()
      def.visibility = Boolp{ menuFolded }
      def.button(Icon.menuSmall, Styles.clearNonei, 48f) {
        menuFolded = false
      }.growY().fillX().padLeft(12f).padRight(12f)

      buildFoldedMenu(def)
    }, Table{ menus ->
      menuTable = menus
      menus.left()
      menus.visibility = Boolp{ !menuFolded }

      buildUnfoldedMenuTabs(tabs, menus)
    })

    topTable.add().growX()

    topTable.button(Icon.cancel, Styles.clearNonei, 32f) { this.hide() }
      .marginLeft(5f).marginRight(5f).growY()
  }

  private fun buildUnfoldedMenuTabs(
    tabs: Map<String, Map<String, List<MenuTab>>>,
    menu: Table,
  ) {
    var currHover: Button? = null
    tabs.forEach { (tabName, groups) ->
      val button = object : Button(Styles.cleart) {
        init {
          add(Core.bundle["calculator.tabs.$tabName", tabName])
        }

        override fun isOver(): Boolean {
          return currHover == this || super.isOver()
        }
      }.apply {
        val show = show@{
          if (currHover == this) return@show
          currHover = this
          onMenuHidden { currHover = null }
          showMenu(this, Align.bottomLeft, Align.topLeft, true) { menu ->
            menu.table(Consts.padDarkGrayUI) { m ->
              m.defaults().growX().fillY().minWidth(300f)
              buildMenuTab(m, groups.values)
            }.fill()
          }
        }

        addListener(object : ClickListener() {
          override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?) {
            super.enter(event, x, y, pointer, fromActor)
            if (fromActor != null && !fromActor.isDescendantOf(this@apply)) show()
          }

          override fun clicked(event: InputEvent?, x: Float, y: Float) {
            if (currHover == this@apply) {
              hideMenu()
              currHover = null
            }
            else show()
          }
        })

        addEventBlocker()
      }

      menu.add(button).marginLeft(20f).marginRight(20f).growY()
    }
  }

  private fun buildMenuTab(
    table: Table,
    groups: Iterable<Iterable<MenuTab>>,
  ) {
    var first = true
    var currHover: Button? = null
    var hoverMenu: Table? = null
    groups.forEach { group ->
      if (!first) table.image().height(2f).pad(4f).padLeft(0f).padRight(0f).growX().color(Color.lightGray).row()
      first = false

      group.forEach { tab ->
        val button = object :Button(Styles.cleart){
          init {
            val valid = tab.valid.get(currPage)
            left().defaults().left()
            image(tab.icon).scaling(Scaling.fit).size(14f)
              .color(if (valid) Color.white else Color.gray)
            add(tab.title).padLeft(6f).labelAlign(Align.left)
              .color(if (valid) Color.white else Color.gray)
            if (tab.subTabs == null){
              if (tab.keyBind != null) {
                add(tab.keyBind.toString()).padLeft(40f)
                  .growX().right().labelAlign(Align.right)
                  .color(if (valid) Color.lightGray else Color.darkGray)
              }
            }
            else {
              table{
                it.right().image(Icon.rightOpenSmall).scaling(Scaling.fit).size(12f)
                  .color(if (valid) Color.white else Color.gray)
              }.growX()
            }
          }

          override fun isOver(): Boolean {
            return currHover == this || super.isOver()
          }
        }

        table.add(button).fill().margin(8f).get().apply {
          if (tab.valid.get(currPage)) {
            if (tab.subTabs != null){
              val show = show@{
                if (currHover == this) return@show
                currHover = this
                hoverMenu = makeSubMenu(this, Align.topRight, Align.topLeft){ t ->
                  val tabs = linkedMapOf<String, Seq<MenuTab>>()
                  tab.subTabs.forEach {
                    tabs.computeIfAbsent(it.group){ Seq() }.add(it)
                  }

                  t.defaults().growX().fillY().minWidth(300f)
                  buildMenuTab(t, tabs.values)
                }
              }

              addListener(object : ClickListener(){
                override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?) {
                  super.enter(event, x, y, pointer, fromActor)
                  if (fromActor != null && !fromActor.isDescendantOf(this@apply)) show()
                }

                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                  super.clicked(event, x, y)
                  if (currHover == this@apply) {
                    hoverMenu?.remove()
                    currHover = null
                  }
                  else show()
                }
              })
            }
            else {
              hovered{
                currHover = null
                hoverMenu?.remove()
                hoverMenu = null
              }

              addCaptureListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                  event?.cancel()
                  hideMenu()
                  tab.clicked?.get(currPage)
                }
              })
            }
          }
          else isDisabled = true
        }
        table.row()
      }
    }
  }

  private fun buildFoldedMenu(pagesTable: Table) {
    pagesTable.button(
      { t ->
        t.left().defaults().left()
        t.image(Icon.mapSmall).size(36f).scaling(Scaling.fit).padLeft(4f)
        t.add("").padLeft(12f).minWidth(60f).update { it.setText(currPage?.title?: Core.bundle["dialog.calculator.noFileOpened"]) }
        t.add("*").padLeft(2f).padRight(4f).visible { currPage?.shouldSave()?: false }
        t.image(Icon.downOpenSmall).size(32f).scaling(Scaling.fit).padLeft(4f)
      }, Styles.cleart) {}.fillX().growY().get().also {
      it.clicked {
        showMenu(it, Align.bottomLeft) { page ->
          page.table(Consts.padDarkGrayUI) { m ->
            m.left().defaults().growX().fillY().minWidth(300f).left()

            m.button(
              { b ->
                b.left().defaults().left()
                b.image(Icon.addSmall).scaling(Scaling.fit).size(14f)
                b.add(Core.bundle["misc.new"]).padLeft(6f).labelAlign(Align.left)
            }, Styles.cleart) {
              hideMenu()
              createNewPage()
            }.margin(8f)
            m.row()
            m.button(
              { b ->
                b.left().defaults().left()
                b.image(Icon.fileSmall).scaling(Scaling.fit).size(14f)
                b.add(Core.bundle["misc.open"]).padLeft(6f).labelAlign(Align.left)
            }, Styles.cleart) {
              hideMenu()
              openFile()
            }.margin(8f)

            if (pages.any()) {
              m.row()
              m.image().color(Pal.darkerGray).height(4f).growX().padTop(4f).padBottom(4f)
              m.row()
              m.add(Core.bundle["dialog.calculator.openedFile"]).pad(4f).color(Color.darkGray)
              m.row()

              pages.forEach { p ->
                buildPageTab(m, p, false)
                m.row()
              }
            }

            if (recentPages.any{ a -> !pages.contains { b -> a.fi == b.fi } }) {
              m.row()
              m.image().color(Pal.darkerGray).height(4f).growX().padTop(4f).padBottom(4f)
              m.row()
              m.add(Core.bundle["dialog.calculator.recentFile"]).pad(4f).color(Color.darkGray)
              m.row()

              recentPages.forEach { p ->
                if (pages.contains { e -> e.fi == p.fi }) return@forEach

                buildPageTab(m, p, true)
                m.row()
              }
            }
          }.fill()
        }
      }
    }

    pagesTable.button(
      { b ->
        b.image(Icon.saveSmall).size(42f).update { it.setColor(Color.lightGray.takeIf { b.isDisabled }?: Color.white) }
        b.add(Core.bundle["misc.save"]).update { it.setColor(Color.lightGray.takeIf { b.isDisabled }?: Color.white) }
      },
      Styles.cleart
    ) {
      val currPage = currPage!!

      if (currPage.fi != null) {
        if (currPage.shouldSave()) save(currPage, currPage.fi!!)
      }
      else {
        Vars.platform.showFileChooser(false, currPage.title, "shd") { file ->
          if (save(currPage, file)) {
            currPage.fi = file
            currPage.title = file.nameWithoutExtension()
          }
        }
      }
    }.disabled { currPage == null }.growY().padLeft(6f).marginLeft(8f).marginRight(12f)
  }

  private fun buildPageTab(
    pane: Table,
    page: ViewPage,
    recentMark: Boolean,
  ): Button {
    return pane.button(
      { b ->
        b.margin(6f)
        b.table{ t ->
          val updateColor = Cons<Element>{
            it.setColor(Color.white.takeIf { page.hovered || page == currPage }?: Color.lightGray)
          }
          t.image(Icon.map).size(36f).scaling(Scaling.fit).padLeft(4f).update{ l -> updateColor(l) }
          t.table { fi ->
            fi.left().defaults().left()
            fi.table {
              it.add(page.title).padLeft(12f).pad(4f).update{ l -> updateColor(l) }
              if (!recentMark) it.add("*").padLeft(2f).padRight(4f).visible { page.shouldSave() }
            }
            fi.row()
            fi.add(page.fi?.path() ?: "no directed file", 0.9f).padLeft(12f).pad(4f)
              .update { it.setColor(Color.lightGray.takeIf { page.hovered || page == currPage }?: Color.gray) }
          }
        }.growY().fillX()

        b.add().grow()

        if (!recentMark) {
          b.button(Icon.cancelSmall, Styles.clearNonei, 20f) {
            if (page.shouldSave()) {
              showChoiceIcons(
                Core.bundle["misc.unsaved"],
                Core.bundle["misc.ensureClose"],
                true,
                Core.bundle["misc.save"] to Icon.save to Runnable {
                  if (page.fi != null) {
                    save(page, page.fi!!)
                    deletePage(page)
                  }
                  else {
                    Vars.platform.showFileChooser(false, "shd") { file ->
                      save(page, file)
                      deletePage(page)
                    }
                  }
                  hideMenu()
                },
                Core.bundle["misc.close"] to Icon.cancel to Runnable {
                  deletePage(page)
                  hideMenu()
                },
              )
            }
            else {
              deletePage(page)
              hideMenu()
            }
          }.margin(5f).visible { page.hovered || page == currPage }
        }
      },
      Button.ButtonStyle(Styles.cleart)
    ) {
      hideMenu()

      if (recentMark) pages.add(page)
      currPage = page
      buildView()
    }.grow().margin(4f).marginLeft(10f).marginRight(10f)
      .scrollX(false).scrollY(true)
      .update { it.isChecked = currPage == page }
      .get().apply {
        style.over = style.up
        hovered { page.hovered = true }
        exited { page.hovered = false }
      }
  }

  //Sides
  private fun buildSide() {
    sideTable.clear()
    sideTable.top().pane(Styles.noBarPane) { list ->
      list.top().defaults().size(40f).padBottom(8f)
      for (entry in sideToolTabs) {
        var btn: Button? = null
        btn = list.button(Icon.none, Styles.clearNoneTogglei, 32f) {
          entry.action.get(currPage?.view, btn)
        }.padTop(8f).update { b ->
          b.isChecked = entry.checked?.get(currPage?.view)?:false
          b.style.imageUp = entry.icon.get(currPage?.view)
        }.get()
        btn.addListener(Tooltip { tip ->
          tip.table(Tex.paneLeft).get()
            .add(entry.desc.get(currPage?.view))
            .update { l -> l.setText(entry.desc.get(currPage?.view)) }
        })
        btn.setDisabled { currPage == null }
        btn.touchable { Touchable.enabled.takeIf{ currPage != null }?: Touchable.disabled }
        btn.fill { x, y, w, h ->
          if (!btn.isDisabled) return@fill
          Consts.grayUIAlpha.draw(x, y, w, h)
        }.touchable = Touchable.disabled
        list.row()
      }
    }.fill().padTop(8f)
    sideTable.add().growY()

    sideTable.row()
    sideTable.button(Icon.infoCircle, Styles.clearNonei, 32f) {
      showHelp()
    }.padBottom(0f).size(40f).padBottom(8f)
  }

  //Views
  private fun buildView(){
    viewTable.clear()
    if (currPage != null){
      val viewPage = currPage!!
      if (!viewPage.loaded) {
        viewPage.view.build()
        if (viewPage.fi != null) viewPage.view.load(viewPage.fi!!)
        viewPage.loaded = true
      }

      viewTable.add(viewPage.view).grow()
    }
    else {
      viewTable.table{ t ->
        t.left().defaults().fill().left()
        t.add(Core.bundle["dialog.calculator.noPage"]).fontScale(1.2f)
        t.row()
        t.add(Core.bundle["dialog.calculator.openPage"]).padTop(8f)
      }.fill()
    }
  }

  //== Tools ==
  //Tips
  fun setTip(
    tip: Prov<String>,
    style: Label.LabelStyle = Styles.defaultLabel,
    color: Color = Color.lightGray,
  ){
    tipsTable.clearChildren()
    tipsTable.left().add(tip.get(), style).color(color).left().update { it.setText(tip.get()) }
    showTips = true
  }

  fun hideTips(){
    showTips = false
  }

  //Menu
  fun showMenu(
    showOn: Element,
    alignment: Int = Align.topLeft,
    tableAlign: Int = Align.topLeft,
    pack: Boolean = true,
    tabBuilder: Cons<Table>,
  ) {
    menuTable.clear()
    tabBuilder(menuTable)
    menuTable.draw()
    menuTable.act(1f)
    if (pack) menuTable.pack()

    menuTable.visible = true

    val v = Vec2()
    if (pack) menuTable.pack()
    v.set(showOn.x, showOn.y)

    if ((alignment and Align.right) != 0) v.x += showOn.width
    else if ((alignment and Align.left) == 0) v.x += showOn.width/2

    if ((alignment and Align.top) != 0) v.y += showOn.height
    else if ((alignment and Align.bottom) == 0) v.y += showOn.height/2

    var align = tableAlign
    showOn.parent.localToStageCoordinates(vec1.set(v))

    if ((align and Align.right) != 0 && vec1.x - menuTable.width < 0) align = align and Align.right.inv() or Align.left
    if ((align and Align.left) != 0 && vec1.x + menuTable.width > Core.scene.width) align = align and Align.left.inv() or Align.right

    if ((align and Align.top) != 0 && vec1.y - menuTable.height < 0) align = align and Align.top.inv() or Align.bottom
    if ((align and Align.bottom) != 0 && vec1.y + menuTable.height > Core.scene.height) align = align and Align.bottom.inv() or Align.top

    showOn.parent.localToAscendantCoordinates(this, v)
    menuTable.setPosition(v.x, v.y, align)

    menuTable.addEventBlocker()
  }

  fun makeSubMenu(
    showOn: Element,
    alignment: Int = Align.topLeft,
    tableAlign: Int = Align.topLeft,
    build: Cons<Table>,
  ): Table {
    val parent = showOn.parent
    val menuTable = Table(Consts.padDarkGrayUI)
    parent.addChild(menuTable)
    build(menuTable)
    menuTable.pack()

    vec1.set(showOn.x, showOn.y)

    if ((alignment and Align.right) != 0) vec1.x += showOn.width
    else if ((alignment and Align.left) == 0) vec1.x += showOn.width/2

    if ((alignment and Align.top) != 0) vec1.y += showOn.height
    else if ((alignment and Align.bottom) == 0) vec1.y += showOn.height/2

    menuTable.localToStageCoordinates(vec2.set(vec1))
    var align = tableAlign
    if ((align and Align.right) != 0 && vec2.x - menuTable.width < 0) align = align and Align.right.inv() or Align.left
    if ((align and Align.left) != 0 && vec2.x + menuTable.width > Core.scene.width) align = align and Align.left.inv() or Align.right

    if ((align and Align.top) != 0 && vec2.y - menuTable.height < 0) align = align and Align.top.inv() or Align.bottom
    if ((align and Align.bottom) != 0 && vec2.y + menuTable.height > Core.scene.height) align = align and Align.bottom.inv() or Align.top

    menuTable.setPosition(vec1.x, vec1.y, align)
    menuTable.addEventBlocker()

    return menuTable
  }

  fun hideMenu() {
    menuFolded = true
    menuTable.visible = false
    menuHiddens.forEach { it.run() }
    menuHiddens.clear()
  }

  fun onMenuHidden(listener: Runnable){
    menuHiddens.add(listener)
  }

  //Handles
  private fun setCurrPage(page: ViewPage?){
    if (page != null && !pages.contains(page)) throw IllegalArgumentException("No such page existed in dialog, please create this page first")
    currPage = page
    buildView()
  }

  private fun createNewPage(
    fi: Fi? = null,
    title: String = fi?.nameWithoutExtension() ?: "untitled",
    activate: Boolean = true,
  ) = ViewPage(fi, title){ CalculatorView() }
    .also { page ->
      pages.add(page)

      if (fi != null) addRecentPage(fi)

      if (!activate) return@also
      setCurrPage(page)
    }

  private fun save(page: ViewPage, fi: Fi): Boolean {
    if (page.view.save(fi)) {
      addRecentPage(fi)
      return true
    }

    return false
  }

  private fun openFile(){
    Vars.platform.showFileChooser(true, "shd") { file ->
      val existed = pages.find { it.fi == file }
      if (existed == null){
        try {
          createNewPage(file)
        } catch (e: Exception) {
          Vars.ui.showException(e)
          Log.err(e)
        }
      }
      else {
        setCurrPage(existed)
        Vars.ui.showInfo(Core.bundle["dialog.calculator.fileOpened"])
      }
    }
  }

  private fun deletePage(page: ViewPage) {
    val index = pages.indexOf(page)
    pages.remove(index)
    if (page == currPage){
      setCurrPage(if (pages.isEmpty) null else pages[max(index - 1, 0)])
    }
    page.reset()
  }

  interface TipsProvider{
    fun getTip(): String
    fun getTipStyle(): Label.LabelStyle = Styles.defaultLabel
    fun getTipColor(): Color = Color.lightGray
    fun tipValid() = true
  }

  class ViewPage(
    var fi: Fi?,
    var title: String,
    private val viewProv: Prov<CalculatorView>,
  ){
    var loaded: Boolean = false
    var hovered = false

    var view: CalculatorView = viewProv.get()

    fun reset(){
      loaded = false
      view = viewProv.get()
    }

    fun shouldSave() = fi == null || view.isUpdated
  }

  data class ToolTab(
    val desc: Func<CalculatorView?, String>,
    val icon: Func<CalculatorView?, Drawable>,
    val checked: Boolf<CalculatorView?>? = null,
    val disabled: Boolf<CalculatorView?>? = null,
    val action: Cons2<CalculatorView?, Button>,
  ) {
    constructor(
      desc: String,
      icon: Func<CalculatorView?, Drawable>,
      checked: Boolf<CalculatorView?>? = null,
      disabled: Boolf<CalculatorView?>? = null,
      action: Cons2<CalculatorView?, Button>,
    ) : this({ desc }, icon, checked, disabled, action)
    constructor(
      desc: String,
      icon: Drawable,
      checked: Boolf<CalculatorView?>? = null,
      disabled: Boolf<CalculatorView?>? = null,
      action: Cons2<CalculatorView?, Button>,
    ) : this({ desc }, Func<CalculatorView?, Drawable> { icon }, checked, disabled, action)
  }

  data class MenuTab(
    val title: String,
    val tabName: String,
    val icon: Drawable,
    val group: String,
    var keyBind: CombinedKeys?,
    val valid: Boolf<ViewPage?>,
    val subTabs: List<MenuTab>?,
    val clicked: Cons<ViewPage?>?,
  ){
    constructor(
      title: String,
      tabName: String,
      icon: Drawable = Consts.transparent,
      group: String = "normal",
      keyBind: CombinedKeys? = null,
      valid: Boolf<ViewPage?> = Boolf{ true },
      clicked: Cons<ViewPage?>,
    ): this(title, tabName, icon, group, keyBind, valid, null, clicked)

    constructor(
      title: String,
      tabName: String,
      icon: Drawable = Consts.transparent,
      group: String = "normal",
      keyBind: CombinedKeys? = null,
      valid: Boolf<ViewPage?> = Boolf{ true },
      vararg subTabs: MenuTab,
    ): this(title, tabName, icon, group, keyBind, valid, subTabs.toList(), null)
  }
}