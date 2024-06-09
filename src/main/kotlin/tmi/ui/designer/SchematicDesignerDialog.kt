package tmi.ui.designer

import arc.Core
import arc.files.Fi
import arc.func.Boolf
import arc.func.Cons
import arc.func.Cons2
import arc.func.Func
import arc.graphics.Color
import arc.graphics.g2d.Fill
import arc.input.KeyCode
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.event.ClickListener
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.style.Drawable
import arc.scene.ui.Button
import arc.scene.ui.Tooltip
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Align
import arc.util.Log
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
import tmi.util.Consts.leftLine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.max
import kotlin.math.min


open class SchematicDesignerDialog : BaseDialog("") {
  companion object {
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
  var rebuildPages = {}
    private set

  private var clipboard: ByteArray = byteArrayOf() // serialize view

  private val pages = Seq<ViewPage>()
  private val keyBinds = CombineKeyTree<Runnable>()

  private var bindsListener: CombineKeyListener<Runnable>? = null

  private var topTable: Table? = null
  private var viewTable: Table? = null
  private var sideTable: Table? = null
  private var pageTable: Table? = null

  private val menuTable: Table = object : Table() {
    init {
      visible = false
    }
  }
  private val menuHiddens = Seq<Runnable>()
  private val export by lazy { ExportDialog(this) }
  //private val balance by lazy { BalanceDialog(this) }

  fun build(){
    titleTable.clear()

    margin(0f)
    getCell(cont).pad(0f)
    cont.background(Consts.grayUI)
    cont.defaults().pad(0f)
    cont.table{
      topTable = it.table(Consts.darkGrayUI).growX().height(42f).get()
      it.row()
      it.table{ under ->
        sideTable = under.table(Consts.darkGrayUIAlpha).fillX().growY().get()
        under.table{ right ->
          pageTable = right.table(Consts.darkGrayUIAlpha).growX().fillY().minHeight(42f).get().left()
          right.row()
          viewTable = right.table().grow().get().apply { clip = true }
        }.grow()
      }.grow().get()
    }.grow().get()

    addChild(menuTable)

    hidden {
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

    buildView()
    buildTopBar()
    buildSideBar()
    buildSideBar()
    buildPageBar()

    setupMenuBinds()
  }

  fun buildPageBar() {
    pageTable!!.clear()
    pageTable!!.table().grow().get().left().apply {
      val e = Element()
      pane { pane ->
        rebuildPages = {
          pane.clearChildren()

          pane.add(e).width(14f)
          pages.forEach { page ->
            buildPageTab(pane, page).style = Button.ButtonStyle(Styles.clearNoneTogglei).apply {
              down = Tex.underline
              checked = Tex.underline
            }
          }

          pane.button(Icon.addSmall, Styles.clearNonei, 24f){
            createNewPage()
            rebuildPages()
          }.size(42f)
        }
        rebuildPages()
      }.scrollY(false).scrollX(true).fillX().growY().get().apply {
        addListener(object : InputListener() {
          override fun scrolled(event: InputEvent, x: Float, y: Float, sx: Float, sy: Float): Boolean {
            scrollX += min(scrollWidth, max((scrollWidth*0.9f), (maxX*0.1f))/4f)*sy
            return true
          }
        })
      }

      fill { over ->
        over.touchable = Touchable.disabled

        val c1 = Pal.darkestGray.toFloatBits()
        val c2 = Pal.darkestGray.cpy().a(0f).toFloatBits()
        over.add(object: Element(){
          override fun draw() {
            Fill.quad(
              x, y, c1,
              x, y + height, c1,
              x + width, y + height, c2,
              x + width, y, c2
            )
          }
        }).growY().width(14f)
        over.add().grow()
        over.add(object: Element(){
          override fun draw() {
            Fill.quad(
              x, y, c2,
              x, y + height, c2,
              x + width, y + height, c1,
              x + width, y, c1
            )
          }
        }).growY().width(14f)
      }
    }
    var button: Button? = null
    var shown = false
    pageTable!!.button(Icon.downOpen, Styles.clearNonei) {
      if (shown) {
        shown = false
        hideMenu()
      }
      else {
        if (pages.isEmpty) return@button
        shown = true
        onMenuHidden{ shown = false }
        showMenu(button!!, Align.bottomRight, Align.topRight) { menu ->
          menu.table(Consts.padDarkGrayUI) { m ->
            m.defaults().growX().fillY().minWidth(300f)
            pages.forEach {
              buildPageTab(m, it).style = Button.ButtonStyle(Styles.clearNoneTogglei).apply {
                down = leftLine
                checked = leftLine
              }
              m.row()
            }
          }.fill()
        }
      }
    }.get().also { button = it }
  }

  private fun buildPageTab(
    pane: Table,
    page: ViewPage,
  ): Button {
    return pane.button(
      { b ->
        arrayOf(
          b.table().growY().fillX().get().image(Icon.map).size(20f).scaling(Scaling.fit).padLeft(4f),
          b.add(page.title).padLeft(12f),
          b.add("*").padLeft(2f).padRight(4f).visible { page.shouldSave() },
        ).forEach {
          it.update { i -> i.setColor(Color.white.takeIf { page.hovered || page == currPage } ?: Color.lightGray) }
        }

        b.add().grow()

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
    ) {
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
    topTable!!.clear()

    val tabs = linkedMapOf<String, MutableMap<String, Seq<MenuTab>>>()
    topMenuTabSet.forEach {
      tabs.computeIfAbsent(it.tabName){ linkedMapOf() }
        .computeIfAbsent(it.group){ Seq() }
        .add(it)
    }

    topTable!!.table{ it.image(Consts.tmi).scaling(Scaling.fit).size(32f) }.growY().marginLeft(8f).marginRight(8f)

    var currHover: Button? = null
    tabs.forEach { (tabName, groups) ->
      val button = object :Button(Styles.cleart){
        init {
          add(Core.bundle["calculator.tabs.$tabName", tabName])
        }

        override fun isOver(): Boolean {
          return currHover == this || super.isOver()
        }
      }

      topTable!!.add(button).marginLeft(20f).marginRight(20f).growY()
        .get().apply {
          val show = show@{
            if (currHover == this) return@show
            currHover = this
            onMenuHidden{ currHover = null }
            showMenu(this, Align.bottomLeft, Align.topLeft, true){ menu ->
              menu.table(Consts.padDarkGrayUI){ m ->
                m.defaults().growX().fillY().minWidth(300f)
                buildMenuTab(m, groups.values)
              }.fill()
            }
          }

          addListener(object : ClickListener(){
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

    topTable!!.add().growX()
    topTable!!.button(Icon.cancel, Styles.clearNonei, 32f) { this.hide() }.margin(5f)
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

  fun buildView(){
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
  ) = ViewPage(DesignerView(this), fi, title)
    .also {
      pages.add(it)
      rebuildPages()

      if (!activate) return@also
      setCurrPage(it)
    }

  fun deletePage(page: ViewPage) {
    val index = pages.indexOf(page)
    pages.remove(index)
    if (page == currPage){
      setCurrPage(if (pages.isEmpty) null else pages[max(index - 1, 0)])
    }
    page.view.clear()
    rebuildPages()
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
    return tempView.cards
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
    val subTable = Table(Consts.padDarkGrayUI)
    parent.addChild(subTable)

    build(subTable)

    vec1.set(showOn.x, showOn.y)

    if ((alignment and Align.right) != 0) vec1.x += showOn.width
    else if ((alignment and Align.left) == 0) vec1.x += showOn.width/2

    if ((alignment and Align.top) != 0) vec1.y += showOn.height
    else if ((alignment and Align.bottom) == 0) vec1.y += showOn.height/2

    subTable.localToStageCoordinates(vec2.set(vec1))
    var align = tableAlign
    if ((align and Align.right) != 0 && vec2.x - menuTable.width < 0) align = align and Align.right.inv() or Align.left
    if ((align and Align.left) != 0 && vec2.x + menuTable.width > Core.scene.width) align = align and Align.left.inv() or Align.right

    if ((align and Align.top) != 0 && vec2.y - menuTable.height < 0) align = align and Align.top.inv() or Align.bottom
    if ((align and Align.bottom) != 0 && vec2.y + menuTable.height > Core.scene.height) align = align and Align.bottom.inv() or Align.top

    subTable.pack()
    subTable.setPosition(vec1.x, vec1.y, align)

    return subTable
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
    val view: DesignerView,
    var fi: Fi?,
    var title: String,
  ){
    fun shouldSave() = fi == null || view.isUpdated

    var loaded: Boolean = false

    internal var hovered: Boolean = false
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
