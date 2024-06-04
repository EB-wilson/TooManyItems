package tmi.ui.designer

import arc.Core
import arc.Graphics
import arc.files.Fi
import arc.func.Boolp
import arc.func.Cons
import arc.func.Prov
import arc.graphics.*
import arc.graphics.g2d.Fill
import arc.graphics.g2d.PixmapPacker.Page
import arc.math.Interp
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.actions.Actions
import arc.scene.event.*
import arc.scene.style.Drawable
import arc.scene.ui.*
import arc.scene.ui.layout.Table
import arc.struct.*
import arc.util.*
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.TooManyItems
import tmi.invoke
import tmi.util.Consts
import tmi.util.vec1
import java.lang.Integer.max
import java.util.concurrent.atomic.AtomicBoolean


open class SchematicDesignerDialog : BaseDialog("") {
  companion object {
    val seq: Seq<ItemLinker> = Seq()
    const val FI_HEAD: Int = -0x315240ff

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

    fun useKeyboard(): Boolean {
      return !Vars.mobile || Core.settings.getBool("keyboard")
    }
  }

  data class ViewPage(
    val view: DesignerView,
    var fi: Fi? = null,
    var title: String = fi?.nameWithoutExtension()?:"untitled",
  ){
    var updated: Boolean = fi == null

    internal var hovered: Boolean = false
  }

  var currPage: ViewPage? = null
    private set

  private val clipboard = Seq<Card>()
  private val pages = Seq<ViewPage>()

  private var rebuildPages = {}

  private var topTable: Table? = null
  private var viewTable: Table? = null
  private var sideTable: Table? = null
  private var pageTable: Table? = null
  private val menuTable: Table = object : Table() {
    init {
      visible = false
    }
  }
  private val export by lazy { ExportDialog(this) }
  //private val balance by lazy { BalanceDialog(this) }

  private var removeArea: Table? = null

  private var sideButtonEntries: Seq<SideBtn> = Seq.with(
    SideBtn(Core.bundle["dialog.calculator.add"], Icon.add) {
      TooManyItems.recipesDialog.toggle = Cons { r ->
        TooManyItems.recipesDialog.hide()
        currPage!!.view.addRecipe(r)
      }
      TooManyItems.recipesDialog.show()
    },
    SideBtn(Core.bundle["dialog.calculator.undo"], Icon.undo) { currPage!!.view.undoHistory() },
    SideBtn(Core.bundle["dialog.calculator.redo"], Icon.redo) { currPage!!.view.redoHistory() },
    SideBtn(Core.bundle["dialog.calculator.standard"], Icon.refresh) { currPage!!.view.standardization() },
    SideBtn(Core.bundle["dialog.calculator.align"], { currPage?.view?.currAlignIcon?: Icon.none }) {
      if (menuTable.visible) {
        hideMenu()
      }
      else {
        showMenu(it, Align.right, Align.left, true) { t ->
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
    },
    SideBtn(Core.bundle["dialog.calculator.selecting"], Icon.resize, {
      currPage!!.view.selectMode = !currPage!!.view.selectMode
      if (!currPage!!.view.selectMode) currPage!!.view.selects.clear()
    }) { currPage?.view?.selectMode?:false },
    //SideBtn(Core.bundle["dialog.calculator.read"], Icon.download) {
    //  Vars.platform.showFileChooser(true, "shd") { file ->
    //    try {
    //      currPage!!.view.read(file.reads())
    //    } catch (e: Exception) {
    //      Vars.ui.showException(e)
    //      Log.err(e)
    //    }
    //  }
    //},
    //SideBtn(
    //  Core.bundle["dialog.calculator.save"], Icon.save
    //) {
    //  Vars.platform.showFileChooser(false, "shd") { file ->
    //    try {
    //      file.writes().apply {
    //        currPage!!.view.write(this)
    //        close()
    //      }
    //    } catch (e: Exception) {
    //      Vars.ui.showException(e)
    //      Log.err(e)
    //    }
    //  }
    //},
    //SideBtn(Core.bundle["dialog.calculator.exportIMG"], Icon.export) { export.show() },
    SideBtn(Core.bundle["dialog.calculator.delete"], Icon.trash, {
      val view = currPage!!.view
      view.removeMode = !view.removeMode
      removeArea!!.clearActions()
      if (view.removeMode) {
        removeArea!!.actions(
          Actions.parallel(
            Actions.sizeTo(removeArea!!.width, Core.scene.height*0.15f, 0.12f),
            Actions.alpha(0.6f, 0.12f)
          )
        )
      }
      else removeArea!!.actions(
        Actions.parallel(
          Actions.sizeTo(removeArea!!.width, 0f, 0.12f),
          Actions.alpha(0f, 0.12f)
        )
      )
    }, { currPage?.view?.removeMode?: false }),
    SideBtn(Core.bundle["dialog.calculator.lock"], { Icon.lock.takeIf { currPage?.view?.editLock == true }?: Icon.lockOpen }, {
      currPage!!.view.editLock = !currPage!!.view.editLock
    }, { currPage?.view?.editLock?: false })
  )

  class SideBtn {
    var desc: String
    var icon: Prov<Drawable>
    var action: Cons<Button>
    var checked: Boolp?

    constructor(desc: String, icon: Drawable, action: Cons<Button>) : this(desc, Prov<Drawable> { icon }, action)

    constructor(desc: String, icon: Prov<Drawable>, action: Cons<Button>) {
      this.desc = desc
      this.icon = icon
      this.action = action
      this.checked = null
    }

    constructor(desc: String, icon: Drawable, action: Cons<Button>, checked: Boolp) : this(
      desc,
      Prov<Drawable> { icon },
      action,
      checked
    )

    constructor(desc: String, icon: Prov<Drawable>, action: Cons<Button>, checked: Boolp?) {
      this.desc = desc
      this.icon = icon
      this.action = action
      this.checked = checked
    }
  }

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
    fill { t ->
      val fold = AtomicBoolean(false)
      t.right().stack(object : Table() {
        var main: Table? = null

        init {
          table(Tex.buttonSideLeft) { but ->
            but.touchable = Touchable.enabled
            val img = but.image(Icon.rightOpen).size(36f).get()
            actions(Actions.run {
              img.setOrigin(Align.center)
              img.setRotation(180f)
              setPosition(main!!.width, 0f)
              fold.set(true)
            })
            but.clicked {
              if (fold.get()) {
                clearActions()
                actions(Actions.moveTo(0f, 0f, 0.3f, Interp.pow2Out), Actions.run { fold.set(false) })
                img.setOrigin(Align.center)
                img.actions(Actions.rotateBy(180f, 0.3f), Actions.rotateTo(0f))
              }
              else {
                clearActions()
                actions(Actions.moveTo(main!!.width, 0f, 0.3f, Interp.pow2Out), Actions.run { fold.set(true) })
                img.setOrigin(Align.center)
                img.actions(Actions.rotateBy(180f, 0.3f), Actions.rotateTo(180f))
              }
            }
            but.hovered {
              img.setColor(Pal.accent)
              Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand)
            }
            but.exited {
              img.setColor(Color.white)
              Core.graphics.restoreCursor()
            }
            resized {
              actions(Actions.run {
                img.setOrigin(Align.center)
                img.setRotation((if (fold.get()) 180 else 0).toFloat())
                setPosition(if (fold.get()) main!!.width else 0f, 0f)
              })
            }
          }.size(36f, 122f).padRight(-5f)
          table(Tex.buttonSideLeft) { main: Table? ->
            this.main = main
          }.growY().width(320f)
        }
      }).fillX().growY().padTop(60f).padBottom(60f)
    }

    buildView()
    buildTopBar()
    buildSideBar()
    buildPageBar()
  }

  private fun buildPageBar() {
    pageTable!!.clear()
    pageTable!!.table().grow().get().left().apply {
      pane { pane ->
        rebuildPages = {
          pane.clearChildren()

          pages.forEach { page ->
            pane.button(
              { b ->
                arrayOf(
                  b.table().growY().fillX().get().image(Icon.map).size(20f).scaling(Scaling.fit).padLeft(4f),
                  b.add(page.title).padLeft(12f),
                  b.add("*").padLeft(2f).padRight(4f).visible { page.fi == null || page.updated },
                ).forEach {
                  it.update { i -> i.setColor(Color.white.takeIf { page.hovered || page == currPage }?:Color.lightGray) }
                }

                b.button(Icon.cancelSmall, Styles.clearNonei, 20f) {
                  val index = pages.indexOf(page)
                  pages.remove(index)
                  if (page == currPage){
                    currPage = if (pages.isEmpty) null else pages[max(index - 1, 0)]
                  }
                  page.view.clear()
                  rebuildPages()
                  buildView()
                }.margin(5f).visible { page.hovered || page == currPage }
              },
              Styles.clearNoneTogglei
            ){
              currPage = page
              buildView()
            }.growY().marginLeft(10f).marginRight(10f)
              .scrollX(false).scrollY(true)
              .update{ it.isChecked = currPage == page }
              .get().apply {
                style.over = style.up
                hovered{ page.hovered = true }
                exited{ page.hovered = false }
              }
          }

          pane.button(Icon.addSmall, Styles.clearNonei, 24f){
            DesignerView(this@SchematicDesignerDialog).also {
              pages.add(ViewPage(it).apply { currPage = this })
            }
            buildView()
            rebuildPages()
          }.size(42f)
        }
        rebuildPages()
      }.scrollY(false).scrollX(true).fillX().growY()

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
    pageTable!!.button(Icon.downOpen, Styles.clearNonei) {}
  }

  private fun buildSideBar() {
    sideTable!!.clear()
    sideTable!!.top().pane(Styles.noBarPane) { list ->
      list.top().defaults().size(40f).padBottom(8f)
      for (entry in sideButtonEntries) {
        var btn: Button? = null
        btn = list.button(Icon.none, Styles.clearNoneTogglei, 32f) { entry.action[btn] }
          .update { b ->
            b.isChecked = entry.checked != null && entry.checked!!.get()
            b.style.imageUp = entry.icon.get()
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

  private fun buildTopBar() {
    topTable!!.table{ it.image(Consts.tmi).scaling(Scaling.fit).size(32f) }.growY().marginLeft(8f).marginRight(8f)
    topTable!!.button({ it.add("@misc.file") }, Styles.cleart){}.growY().marginLeft(20f).marginRight(20f)
    topTable!!.button({ it.add("@misc.edit") }, Styles.cleart){}.growY().marginLeft(20f).marginRight(20f)
    topTable!!.button({ it.add("@misc.help") }, Styles.cleart){}.growY().marginLeft(20f).marginRight(20f)
    topTable!!.add().growX()
    topTable!!.button(Icon.cancel, Styles.clearNonei, 32f) { this.hide() }.margin(5f)
  }

  private fun buildView(){
    viewTable!!.clear()
    if (currPage != null){
      viewTable!!.add(currPage!!.view).grow()
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

  fun setClipboard(vararg card: Card){
    clipboard.clear()
    clipboard.addAll(*card)
  }

  fun getClipboard(): Iterable<Card>{
    return clipboard
  }

  fun showMenu(showOn: Element, alignment: Int, tableAlign: Int, pack: Boolean, tabBuilder: Cons<Table>) {
    menuTable.clear()
    tabBuilder(menuTable)
    menuTable.draw()
    menuTable.act(1f)
    if (pack) menuTable.pack()

    menuTable.visible = true

    val v = Vec2()
    val r: Runnable
    menuTable.update(Runnable {
      if (showOn.parent == null) return@Runnable // 未知原因，在切换页面时首次尝试打开菜单会出现异常的父级空值

      if (pack) menuTable.pack()
      v[showOn.x] = showOn.y

      if ((alignment and Align.right) != 0) v.x += showOn.width
      else if ((alignment and Align.left) == 0) v.x += showOn.width/2

      if ((alignment and Align.top) != 0) v.y += showOn.height
      else if ((alignment and Align.bottom) == 0) v.y += showOn.height/2

      var align = tableAlign
      showOn.parent.localToStageCoordinates(vec1.set(v))

      if ((align and Align.right) != 0 && vec1.x - menuTable.width < 0) align = align and Align.right.inv() or Align.left
      if ((align and Align.left) != 0 && vec1.x + menuTable.width > Core.scene.width) align =
        align and Align.left.inv() or Align.right

      if ((align and Align.top) != 0 && vec1.y - menuTable.height < 0) align = align and Align.top.inv() or Align.bottom
      if ((align and Align.bottom) != 0 && vec1.y + menuTable.height > Core.scene.height) align =
        align and Align.bottom.inv() or Align.top

      showOn.parent.localToAscendantCoordinates(this, v)
      menuTable.setPosition(v.x, v.y, align)
    }.also { r = it })

    r.run()
  }

  fun hideMenu() {
    menuTable.visible = false
  }
}
