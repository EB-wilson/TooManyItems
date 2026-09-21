package tmi.ui

import arc.Core
import arc.files.Fi
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
import universe.ui.markdown.BaseProvider
import universe.ui.markdown.Markdown
import universe.ui.markdown.UrlHandler
import java.io.InputStream
import java.net.URLDecoder

class DocumentDialog : BaseDialog("") {
  var rebuilder: Intc? = null
  var resize: Runnable? = null
  var lastPane: Table? = null

  var rootPath: Fi? = null

  private val docCont: Cell<Table> = cont.table().pad(20f).maxWidth(1280f).grow()
  private val doc: Table = docCont.get()

  init {
    addEventBlocker()

    addCloseButton()
    keyDown(KeyCode.escape) { hide() }
    resized { rebuilder?.get(0) }

    hidden { doc.clearChildren() }
  }

  fun contLayout(layout: Cons<Cell<Table>>): DocumentDialog {
    layout.get(docCont)
    doc.invalidateHierarchy()
    return this
  }

  //showDocument
  fun showDocument(title: String, mdStyle: Markdown.MarkdownStyle, vararg markdowns: String) {
    showDocument(title, null, mdStyle, *markdowns)
  }

  //showDocument
  fun showDocument(title: String, rootFile: Fi?, mdStyle: Markdown.MarkdownStyle, vararg markdowns: String) {
    rootPath = rootFile

    val pages = markdowns.map { md -> Markdown(md, mdStyle, object: BaseProvider(){
      override fun defaultUrlHandler(): UrlHandler = RootRelativeUrlHandler(rootPath)
    }) }

    showDocument(title, *pages.toTypedArray())
  }

  fun showDocument(title: String, vararg tableBuilder: Cons<Table>) {
    val pages = tableBuilder.map { builder -> Table(builder) }

    showDocument(title, *pages.toTypedArray())
  }

  fun showDocument(title: String, vararg documents: Element) {
    titleTable.clearChildren()
    titleTable.add(title).color(Pal.accent)

    var currPage = 0
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
              currPage--
              rebuilder?.get(-1)
            }.disabled { currPage <= 0 }.get()
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
                lastPane?.actions(
                  Actions.parallel(
                    Actions.alpha(0f, 0.5f, Interp.pow3In),
                    Actions.moveBy(-clip.getWidth()/2*i, 0f, 0.5f, Interp.pow3In)
                  ),
                  Actions.run {
                    clip.removeChild(lastPane)
                    lastPane = clip.table(Consts.padGrayUIAlpha) { page ->
                      page.top().table().grow().get().pane(
                        Styles.smallPane, Table{ it.add(documents[currPage]).grow().pad(16f) }
                      ).grow().scrollX(false)
                    }.scrollX(false).grow().get()
                    lastPane?.color?.a = 0f

                    val w = clip.getWidth()
                    val h = clip.getHeight()
                    lastPane?.actions(
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
                    Styles.smallPane, Table{ it.add(documents[currPage]).grow().pad(16f) }
                  ).grow().scrollX(false)
                }.grow().get()
              }
            }
            rebuilder?.get(0)
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
              currPage++
              rebuilder?.get(1)
            }.disabled { _ -> currPage >= documents.size - 1 }.get()
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
        resize?.run()
        resized(resize)
      }.grow()
      doc.row()
      doc.add("").update { l ->
        l.setText(Core.bundle.format("dialog.recipes.pages", currPage + 1, documents.size))
      }
    }

    show()
  }
}

/**
 * 处理不含协议声明的url(如 `images/a.png`、`./a.png`、`../a.png`)的处理器。
 *
 * markdown库会把这类url交给默认处理器([universe.ui.markdown.MarkdownProvider.defaultUrlHandler]),
 * 因此这里以 [rootPath] 为根, 将其按相对路径解析为模组内的文件, 并返回相应的资源句柄。
 *
 * [rootPath] 既可以是文档所在的目录, 也可以是文档文件本身(此时以该文件的父目录为根)。
 * 以 `/` 开头的url同样以 [rootPath] 为根解析。
 */
private class RootRelativeUrlHandler(rootPath: Fi?): UrlHandler {
  /**解析基准目录。*/
  private val root: Fi? = rootPath?.let {
    when {
      //目录本身直接作为根
      it.isDirectory -> it
      //文档文件(或带扩展名的路径)以其所在目录为根
      it.exists() || it.extension().isNotEmpty() -> it.parent()
      else -> it
    }
  }

  /**本处理器只作为默认处理器使用, 不参与协议匹配。*/
  override fun matchedSchemes(): List<String> = emptyList()

  override fun openUrl(url: String) {
    val fi = locate(url)
    if (!Core.app.openFolder(fi.absolutePath()))
      throw IllegalStateException("Cannot open file: ${fi.absolutePath()} located from url: $url")
  }

  override fun getResource(url: String): UrlHandler.ResourceHandle = object: UrlHandler.ResourceHandle(){
    //延迟到读取时定位, 使文件缺失等问题能被读取线程捕获并反馈为错误占位图
    override fun openStream(): InputStream = locate(url).read()
  }

  /**将无协议声明的url解析为 [root] 下的文件。*/
  private fun locate(url: String): Fi {
    val base = root?: throw IllegalStateException("No root path was provided, cannot locate relative url: $url")

    //去除查询串与锚点, 统一分隔符, 并还原被转义的非ASCII字符(路径中的 `+` 不应被视作空格)
    var path = url.substringBefore('#').substringBefore('?').replace('\\', '/')
    if (path.contains('%')) {
      path = try {
        URLDecoder.decode(path.replace("+", "%2B"), Charsets.UTF_8)
      }
      catch (e: IllegalArgumentException) {
        path
      }
    }

    var fi = base
    path.split('/').forEach { segment ->
      when (segment) {
        "", "." -> {}
        ".." -> fi = fi.parent()?: base
        else -> fi = fi.child(segment)
      }
    }

    return fi
  }
}
