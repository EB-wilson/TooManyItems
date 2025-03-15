package tmi.ui.designer

import arc.ApplicationCore
import arc.ApplicationListener
import arc.Core
import arc.files.Fi
import arc.func.*
import arc.graphics.Color
import arc.input.KeyCode
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.event.*
import arc.scene.style.Drawable
import arc.scene.ui.Button
import arc.scene.ui.Tooltip
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Align
import arc.util.Log
import arc.util.Reflect
import arc.util.Scaling
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.invoke
import tmi.ui.TmiUI
import tmi.ui.addEventBlocker
import tmi.util.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.max

open class SchematicDesignerDialog : BaseDialog("") {
  companion object {
    val modules: Array<ApplicationListener> by lazy {
      Reflect.get(ApplicationCore::class.java, Core.app.listeners.find { it is ApplicationCore }, "modules")
    }

    val seq: Seq<ItemLinker> = Seq()
    const val FI_HEAD: Int = -0x315240ff

    fun useKeyboard(): Boolean {
      return !Vars.mobile || Core.settings.getBool("keyboard")
    }
  }

  var maximumHistories = 300

  val viewMenuTabs = Seq<ViewTab>()
  val topMenuTabSet = Seq<MenuTab>()
  val sideToolTabs = Seq<ToolTab>()

  var currPage: ViewPage? = null
    private set
  var removeArea: Table? = null
    private set

  private var clipboard: ByteArray = byteArrayOf() // serialize view

  private val pages = Seq<ViewPage>()
  private val recentPages = Seq<ViewPage>()
  private val keyBinds = CombineKeyTree<Runnable>()

  private var topTable: Table? = null
  private var viewTable: Table? = null
  private var sideTable: Table? = null
  private var fastStatistic: Table? = null

  private val menuTable: Table = object : Table() {
    init {
      visible = false
    }
  }
  private val menuHiddens = Seq<Runnable>()

  private fun loadRecentPages(){
    recentPages.clear()
    val raw = Core.settings.getString("tmi-schematic-recent-pages", "")
    val entries = raw.split(";")

    entries.forEach {
      addRecentPage(Fi(it))
    }
  }

  private fun addRecentPage(fi: Fi) {
    recentPages.removeAll { it.fi?.exists()?.let { b -> !b }?: true }
    if (!fi.exists()) return
    if (recentPages.contains { it.fi == fi }) return
    recentPages.add(ViewPage({ DesignerView(this) }, fi, fi.nameWithoutExtension()))

    Core.settings.put("tmi-schematic-recent-pages", recentPages.joinToString(";") { it.fi!!.path() })
  }

  fun build(){
    loadRecentPages()

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
        viewTable = under.table().grow().get().apply { clip = true }
      }.grow().get()
    }.grow().get()

    addChild(menuTable)

    var wrappedRenderer: WrapAppListener? = null
    val hiddenElem = ObjectMap<Element, Boolp>()

    shown {
      val n = modules.indexOf(Vars.renderer)

      if (n != -1) {
        WrapAppListener(Vars.renderer) { modules[n] = it }.also {
          wrappedRenderer = it
          modules[n] = it
        }

        hiddenElem.clear()
      }

      Core.scene.elements.forEach { e ->
        if (e != this && (e.visible || e.visibility != null)){
          hiddenElem.put(e, e.visibility)
          e.visible = false
          e.visibility = null
        }
        else return@shown
      }
    }
    hidden {
      wrappedRenderer?.reset()
      hiddenElem.forEach { e ->
        e.key.visible = true
        e.key.visibility = e.value
      }

      wrappedRenderer = null
      hiddenElem.clear()

      removeArea!!.height = 0f
      removeArea!!.color.a = 0f
      pages.forEach{
        val view = it.view
        view.removeMode = false
        view.selectMode = false
        view.selects.clear()
        view.editLock = false
      }
      hideMenu()
    }

    fill { t ->
      t.bottom().table(Consts.darkGrayUI) { area ->
        removeArea = area
        area.color.a = 0f
        area.add(Core.bundle["dialog.calculator.removeArea"])
      }.bottom().growX().height(0f)
    }

    addListener(object: InputListener(){
      override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
        hideMenu()
        return false
      }
    })

    addListener(object: EventListener {
      override fun handle(event: SceneEvent?): Boolean {
        if (event is StatisticEvent) {
          updateStatistic()
          return true
        }
        return false
      }
    })

    buildView()
    buildTopBar()
    buildSideBar()

    setupMenuBinds()
  }

  private fun buildPageTab(
    pane: Table,
    page: ViewPage,
    recentMark: Boolean
  ): Button {
    return pane.button({ b ->
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
            TmiUI.showChoiceIcons(
              Core.bundle["misc.unsaved"],
              Core.bundle["misc.ensureClose"],
              true,
              Core.bundle["misc.save"] to Icon.save to Runnable {
                if (page.fi != null) {
                  save(page.view, page.fi!!)
                  deletePage(page)
                }
                else {
                  Vars.platform.showFileChooser(false, "shd") { file ->
                    save(page.view, file)
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
    }, Button.ButtonStyle(Styles.cleart)) {
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

  private fun save(currPage: DesignerView, file: Fi): Boolean{
    try {
      file.writes().apply {
        currPage.write(this)
        close()
      }

      currPage.makeSaved()

      addRecentPage(file)

      return true
    } catch (e: Exception) {
      Vars.ui.showException(e)
      Log.err(e)

      return false
    }
  }

  fun buildSideBar() {
    sideTable!!.clear()
    sideTable!!.top().pane(Styles.noBarPane) { list ->
      list.top().defaults().size(40f).padBottom(8f)
      for (entry in sideToolTabs) {
        var btn: Button? = null
        btn = list.button(Icon.none, Styles.clearNoneTogglei, 32f) { entry.action.get(currPage?.view, btn) }
          .update { b ->
            b.isChecked = entry.checked?.get(currPage?.view)?:false
            b.style.imageUp = entry.icon.get(currPage?.view)
          }.get()
        btn.addListener(Tooltip { tip -> tip.table(Tex.paneLeft).get().add(entry.desc) })
        btn.setDisabled { currPage == null }
        btn.touchable { Touchable.enabled.takeIf{ currPage != null }?: Touchable.disabled }
        btn.fill { x, y, w, h ->
          if (!btn.isDisabled) return@fill
          Consts.grayUIAlpha.draw(x, y, w, h)
        }.touchable = Touchable.disabled
        list.row()
      }
    }.fill().padTop(8f)
    sideTable!!.add().growY()

    sideTable!!.row()
    sideTable!!.button(Icon.infoCircle, Styles.clearNonei, 32f) {}.padBottom(0f).size(40f).padBottom(8f)
  }

  fun buildTopBar() {
    val top = topTable!!
    top.clear()

    val tabs = linkedMapOf<String, MutableMap<String, Seq<MenuTab>>>()
    topMenuTabSet.forEach {
      tabs.computeIfAbsent(it.tabName){ linkedMapOf() }
        .computeIfAbsent(it.group){ Seq() }
        .add(it)
    }

    top.table{ it.image(Consts.tmi).scaling(Scaling.fit).size(32f) }.growY().marginLeft(8f).marginRight(8f)

    var folded = true
    var menuTable: Table? = null

    this@SchematicDesignerDialog.addListener(object : InputListener(){
      override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
        val elem = this@SchematicDesignerDialog.hit(x, y, false)
        if (elem != null && elem.isDescendantOf(menuTable)) return false

        folded = true
        return false
      }
    })

    top.stack(Table{ def ->
      def.left()
      def.visibility = Boolp{ folded }
      def.button(Icon.menuSmall, Styles.clearNonei, 48f) {
        folded = false
      }.growY().fillX().padLeft(12f).padRight(12f)

      buildFoldedMenu(def)
    }, Table{ menus ->
      menuTable = menus
      menus.left()
      menus.visibility = Boolp{ !folded }

      buildUnfoldedMenuTabs(tabs, menus)
    })

    top.add().growX()
    top.table { buildStatisticBar(it) }.padRight(180f).fillX().growY()
    top.button(Icon.cancel, Styles.clearNonei, 32f) { this.hide() }.marginLeft(5f).marginRight(5f).growY()
  }

  private fun buildStatisticBar(stat: Table) {
    stat.defaults().growY()
    //stat.add(Core.bundle["dialog.calculator.statistic"])

    stat.image().color(Color.darkGray).width(2f).growY().pad(0f).padLeft(6f)
    stat.button({ t ->
      fastStatistic = t.table().minWidth(220f).maxWidth(500f).fillX().get().left()
      t.image(Icon.downOpenSmall).size(36f).scaling(Scaling.fit).padLeft(4f)
    }, Styles.clearNonei){
    }.fillX()

    stat.image().color(Color.darkGray).width(2f).growY().pad(0f).padRight(6f)

    stat.button(Icon.listSmall, Styles.clearNonei, 36f){

    }.margin(4f)
    stat.button(Icon.settingsSmall, Styles.clearNonei, 36f){

    }.margin(4f)
  }

  private fun updateStatistic(){
    val curr = currPage?.view?:return

    fastStatistic?.apply {
      clearChildren()

      var n = 0
      curr.statistic.resultMissing().forEach{ s ->
        if (n++ > 6) return@forEach

        stack(
          Table{ t ->
            t.left().image(s.item.icon).left().scaling(Scaling.fit).pad(4f)
          },
          Table{ t ->
            t.bottom().left().add(s.getAmount(), Styles.outlineLabel).color(Color.red).fontScale(0.7f)
          }
        ).height(32f).fillX().padLeft(4f).padRight(4f)
      }

      curr.statistic.resultRedundant().forEach{ s ->
        if (n++ > 6) return@forEach

        stack(
          Table{ t ->
            t.left().image(s.item.icon).left().scaling(Scaling.fit).pad(4f)
          },
          Table{ t ->
            t.bottom().left().add(s.getAmount(), Styles.outlineLabel).color(Pal.heal).fontScale(0.7f)
          }
        ).height(32f).fillX().padLeft(4f).padRight(4f)
      }

      if (n > 6) {
        add("...").pad(4f)
      }
    }
  }

  private fun buildFoldedMenu(menu: Table, ) {
    menu.button({ t ->
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

            m.button({ b ->
              b.left().defaults().left()
              b.image(Icon.addSmall).scaling(Scaling.fit).size(14f)
              b.add(Core.bundle["misc.new"]).padLeft(6f).labelAlign(Align.left)
            }, Styles.cleart) {
              hideMenu()
              createNewPage()
            }.margin(8f)
            m.row()
            m.button({ b ->
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

    menu.button({ b ->
      b.image(Icon.saveSmall).size(42f).update { it.setColor(Color.lightGray.takeIf { b.isDisabled }?: Color.white) }
      b.add(Core.bundle["misc.save"]).update { it.setColor(Color.lightGray.takeIf { b.isDisabled }?: Color.white) }
    }, Styles.cleart) {
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
    }.disabled { currPage == null }.growY().padLeft(6f).marginLeft(8f).marginRight(12f)
  }

  private fun buildUnfoldedMenuTabs(
    tabs: LinkedHashMap<String, MutableMap<String, Seq<MenuTab>>>,
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
      }

      menu.add(button).marginLeft(20f).marginRight(20f).growY().get()
        .apply {
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
    }
  }

  fun setupMenuBinds() {
    keyBinds.clear()

    setBinds(topMenuTabSet)

    update {
      if (Core.scene.hasField()) return@update
      keyBinds.checkTap(Core.input)?.run()
    }
  }

  private fun setBinds(topMenuTabSet: Iterable<MenuTab>) {
    topMenuTabSet.forEach { tab ->
      if (tab.subTabs != null) {
        setBinds(tab.subTabs)
      }
      else if (tab.keyBind != null) {
        keyBinds.putKeyBinding(tab.keyBind!!) { tab.clicked?.apply { accept() } }
      }
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
            val valid = tab.valid.run { accept() }
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
          if (tab.valid(this@SchematicDesignerDialog)) {
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
                  tab.clicked?.invoke(this@SchematicDesignerDialog)
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

  private fun buildView(){
    viewTable!!.clear()
    if (currPage != null){
      val viewPage = currPage!!
      if (!viewPage.loaded) {
        viewPage.view.build()
        if (viewPage.fi != null) viewPage.view.read(viewPage.fi!!.reads())
        viewPage.loaded = true
      }

      viewTable!!.add(viewPage.view).grow()
    }
    else {
      viewTable!!.table{ t ->
        t.left().defaults().fill().left()
        t.add(Core.bundle["dialog.calculator.noPage"]).fontScale(1.2f)
        t.row()
        t.add(Core.bundle["dialog.calculator.openPage"]).padTop(8f)
      }.fill()
    }
  }

  fun getPages(): Iterable<ViewPage> = pages

  fun setCurrPage(page: ViewPage?){
    if (page != null && !pages.contains(page)) throw IllegalArgumentException("No such page existed in dialog, please create this page first")
    currPage = page
    buildView()
  }

  fun createNewPage(
    fi: Fi? = null,
    title: String = fi?.nameWithoutExtension() ?: "untitled",
    activate: Boolean = true,
  ) = ViewPage({ DesignerView(this) }, fi, title)
    .also {
      pages.add(it)

      if (fi != null) addRecentPage(fi)

      if (!activate) return@also
      setCurrPage(it)
    }

  fun openFile(){
    Vars.platform.showFileChooser(true, "shd") { file ->
      val existed = getPages().find { it.fi == file }
      if (existed == null){
        try {
          createNewPage(fi = file)
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

  fun deletePage(page: ViewPage) {
    val index = pages.indexOf(page)
    pages.remove(index)
    if (page == currPage){
      setCurrPage(if (pages.isEmpty) null else pages[max(index - 1, 0)])
    }
    page.reset()
  }

  fun setClipboard(card: Iterable<Card>){
    val tempView = DesignerView(this)
    val output = ByteArrayOutputStream()
    val writes = Writes(DataOutputStream(output))
    card.forEach {
      tempView.cards.add(it) // not use 'addCard' because can not exchange parent
    }
    tempView.writeCards(writes)
    clipboard = output.toByteArray()
  }

  fun getClipboard(): Iterable<Card>{
    val tempView = DesignerView(this)
    val reads = Reads(DataInputStream(ByteArrayInputStream(clipboard)))
    tempView.readCards(reads)
    tempView.standardization()
    return tempView.cards.toList().plus(tempView.foldCards)
  }

  fun clipboardEmpty() = clipboard.isEmpty()

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

    showOn.parent.localToAscendantCoordinates(this@SchematicDesignerDialog, v)
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
    menuTable.visible = false
    menuHiddens.forEach { it.run() }
    menuHiddens.clear()
  }

  fun onMenuHidden(listener: Runnable){
    menuHiddens.add(listener)
  }

  fun menuShown() = menuTable.visible

  data class ViewPage(
    val viewBuilder: Prov<DesignerView>,
    var fi: Fi?,
    var title: String,
  ){
    var view = viewBuilder()
    var loaded: Boolean = false
    internal var hovered: Boolean = false

    fun reset(){
      loaded = false
      view = viewBuilder()
    }
    fun shouldSave() = fi == null || view.isUpdated
  }
}

data class ToolTab(
  val desc: String,
  val icon: Func<DesignerView?, Drawable>,
  val checked: Boolf<DesignerView?>? = null,
  val action: Cons2<DesignerView?, Button>,
) {
  constructor(
    desc: String,
    icon: Drawable,
    checked: Boolf<DesignerView?>? = null,
    action: Cons2<DesignerView?, Button>,
  ) : this(desc, Func<DesignerView?, Drawable> { icon }, checked, action)
}

data class MenuTab internal constructor (
  val title: String,
  val tabName: String,
  val icon: Drawable,
  val group: String,
  var keyBind: CombinedKeys?,
  val valid: DesignerReceiver<Boolean>,
  val subTabs: List<MenuTab>?,
  val clicked: DesignerReceiver<Unit>?,
){
  constructor(
    title: String,
    tabName: String,
    icon: Drawable = Consts.transparent,
    group: String = "normal",
    keyBind: CombinedKeys? = null,
    valid: DesignerReceiver<Boolean> = DesignerReceiver{ true },
    clicked: DesignerReceiver<Unit>,
  ): this(title, tabName, icon, group, keyBind, valid, null, clicked)

  constructor(
    title: String,
    tabName: String,
    icon: Drawable = Consts.transparent,
    group: String = "normal",
    keyBind: CombinedKeys? = null,
    valid: DesignerReceiver<Boolean> = DesignerReceiver{ true },
    vararg subTabs: MenuTab,
  ): this(title, tabName, icon, group, keyBind, valid, subTabs.toList(), null)
}

fun interface DesignerReceiver<R>{
  fun SchematicDesignerDialog.accept(): R

  operator fun invoke(dialog: SchematicDesignerDialog) = dialog.accept()
}
