package tmi.ui

import arc.Core
import arc.func.Cons
import arc.func.Intc
import arc.input.KeyCode
import arc.math.Interp
import arc.scene.Element
import arc.scene.actions.Actions
import arc.scene.ui.ImageButton.ImageButtonStyle
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.util.Consts
import universecore.ui.elements.markdown.Markdown
import universecore.ui.elements.markdown.Markdown.MarkdownStyle

class DocumentDialog : BaseDialog("") {
  var rebuilder: Intc? = null
  var resize: Runnable? = null
  var lastPane: Table? = null

  private val docCont: Cell<Table> = cont.table().pad(20f).maxWidth(1280f).grow()
  private val doc: Table = docCont.get()

  init {
    addEventBlocker()

    addCloseButton()
    keyDown(KeyCode.escape) { hide() }

    hidden { doc.clearChildren() }
  }

  fun contLayout(layout: Cons<Cell<Table>>): DocumentDialog {
    layout.get(docCont)
    doc.invalidateHierarchy()
    return this
  }

  //showDocument
  fun showDocument(title: String, mdStyle: MarkdownStyle, vararg markdowns: String) {
    val pages = markdowns.map { md -> Markdown(md, mdStyle) }

    showDocument(title, *pages.toTypedArray())
  }

  fun showDocument(title: String, vararg tableBuilder: Cons<Table>) {
    val pages = tableBuilder.map { builder -> Table(builder) }

    showDocument(title, *pages.toTypedArray())
  }

  fun showDocument(title: String, vararg documents: Element) {
    titleTable.clearChildren()
    titleTable.add(title).color(Pal.accent)

    val index = IntArray(1)
    doc.clearChildren()
    if (documents.isNotEmpty()) {
      doc.top().table { table ->
        fun buildSwitchLeft(t: Table){
          if (documents.size > 1) {
            if (Core.graphics.isPortrait) {
              t.defaults().growX().height(45f)
            }
            else t.defaults().growY().width(40f)

            val bu = t.button(Icon.leftOpen, Styles.clearNonei) {
              index[0]--
              rebuilder!!.get(-1)
            }.disabled { _ -> index[0] <= 0 }.get()
            bu.style.disabled = Consts.grayUIAlpha
            bu.style.up = bu.style.disabled
          }
        }
        val build = Runnable {
          table.table { clip ->
            table.top().defaults().top()
            clip.setClip(true)

            rebuilder = Intc { i ->
              if (i != 0 && lastPane != null) {
                lastPane!!.actions(
                  Actions.parallel(
                    Actions.alpha(0f, 0.5f, Interp.pow3In),
                    Actions.moveBy(-clip.getWidth()/2*i, 0f, 0.5f, Interp.pow3In)
                  ),
                  Actions.run {
                    clip.removeChild(lastPane)
                    lastPane = clip.table(Consts.padGrayUIAlpha) { page ->
                      page.top().table().get().pane(
                        Styles.smallPane, documents[index[0]]
                      ).scrollX(false).get().setFillParent(true)
                    }.scrollX(false).grow().get()
                    lastPane!!.color.a = 0f

                    val w = clip.getWidth()
                    val h = clip.getHeight()
                    lastPane!!.actions(
                      Actions.parallel(
                        Actions.alpha(1f, 0.5f, Interp.pow3Out),
                        Actions.moveTo(w/2*i, 0f),
                        Actions.sizeTo(w, h),
                        Actions.moveTo(0f, 0f, 0.5f, Interp.pow3Out)
                      )
                    )
                  }
                )
              }
              else {
                lastPane = clip.table(Consts.padGrayUIAlpha) { page ->
                  page.top().table().grow().get().pane(
                    Styles.smallPane, documents[index[0]]
                  ).scrollX(false).grow().get().setFillParent(true)
                }.grow().get()
              }
            }
            rebuilder!!.get(0)
          }.grow()
        }

        fun buildSwitchRight(t: Table){
          if (documents.size > 1) {
            if (Core.graphics.isPortrait) {
              t.defaults().growX().height(45f)
            }
            else t.defaults().growY().width(40f)

            val bu = t.button(Icon.rightOpen, object : ImageButtonStyle(Styles.clearNonei) {
              init {
                up = Consts.grayUIAlpha
              }
            }) {
              index[0]++
              rebuilder!!.get(1)
            }.disabled { _ -> index[0] >= documents.size - 1 }.get()
            bu.style.disabled = Consts.grayUIAlpha
            bu.style.up = bu.style.disabled
          }
        }

        resize = Runnable {
          table!!.clearChildren()
          if (Core.graphics.isPortrait) {
            build.run()
            table.row()
            table.table { b ->
              buildSwitchLeft(b)
              buildSwitchRight(b)
            }.fillY().growX()
          }
          else {
            buildSwitchLeft(table.table().growY().fillX().get())
            build.run()
            buildSwitchRight(table.table().growY().fillX().get())
          }
        }
        resize!!.run()
        resized(resize)
      }.grow()
      doc.row()
      doc.add("").update { l ->
        l.setText(Core.bundle.format("dialog.recipes.pages", index[0] + 1, documents.size))
      }
    }

    show()
  }
}