package tmi.ui.calculator

import arc.Core
import arc.files.Fi
import arc.func.Cons
import arc.func.Prov
import arc.graphics.Color
import arc.input.KeyCode
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.ui.Button
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Align
import arc.util.Log
import arc.util.Scaling
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.util.invoke
import tmi.ui.TmiUI
import tmi.ui.addEventBlocker
import tmi.util.Consts
import tmi.util.vec1
import tmi.util.vec2
import kotlin.math.max

class CalculatorDialog: BaseDialog("") {
  private val pages = Seq<ViewPage>()
  private val recentPages = Seq<ViewPage>()
  private var currPage: ViewPage? = null

  private lateinit var topTable: Table
  private lateinit var viewTable: Table
  private lateinit var sideTable: Table

  private val menuTable = Table().apply{ visible = false }

  init {
    build()

    addListener(object: InputListener(){
      override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
        hideMenu()
        return false
      }
    })
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

    topTable.table{ def ->
      def.left()
      buildPages(def)
    }

    topTable.add().growX()

    topTable.button(Icon.cancel, Styles.clearNonei, 32f) { this.hide() }
      .marginLeft(5f).marginRight(5f).growY()
  }

  private fun buildPages(pagesTable: Table) {
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
        if (currPage.shouldSave()) currPage.view.save(currPage.fi!!)
      }
      else {
        Vars.platform.showFileChooser(false, currPage.title, "shd") { file ->
          if (currPage.view.save(file)) {
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
    recentMark: Boolean
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
              TmiUI.showChoiceIcons(
                Core.bundle["misc.unsaved"],
                Core.bundle["misc.ensureClose"],
                true,
                Core.bundle["misc.save"] to Icon.save to Runnable {
                  if (page.fi != null) {
                    page.view.save(page.fi!!)
                    deletePage(page)
                  }
                  else {
                    Vars.platform.showFileChooser(false, "shd") { file ->
                      page.view.save(file)
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
  //Menu
  private fun showMenu(
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

  private fun makeSubMenu(
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

  private fun hideMenu() {
    menuTable.visible = false
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

  private fun addRecentPage(fi: Fi) {
    recentPages.removeAll { it.fi?.exists()?.let { b -> !b }?: true }
    if (!fi.exists()) return
    if (recentPages.contains { it.fi == fi }) return
    recentPages.add(ViewPage(fi, fi.nameWithoutExtension()){ CalculatorView() })

    Core.settings.put("tmi-schematic-recent-pages", recentPages.joinToString(";") { it.fi!!.path() })
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

  class ViewPage(
    var fi: Fi?,
    var title: String,
    private val viewProv: Prov<CalculatorView>
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
}