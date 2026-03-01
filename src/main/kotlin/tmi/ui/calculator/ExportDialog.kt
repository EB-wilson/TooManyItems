package tmi.ui.calculator

import arc.Core
import arc.files.Fi
import arc.graphics.Color
import arc.graphics.Pixmap
import arc.graphics.PixmapIO
import arc.graphics.g2d.TextureRegion
import arc.graphics.gl.FrameBuffer
import arc.math.Mathf
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.layout.Scl
import arc.util.*
import mindustry.Vars
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.graphic.ChunkedFrameBuffer
import tmi.util.Consts
import kotlin.math.min

class ExportDialog: BaseDialog("", Consts.transparentBack) {
  private val imgPreview = TextureRegion((Tex.nomap as TextureRegionDrawable).region)
  private val previewBuffer = FrameBuffer()
  private val chunkedBuffer = ChunkedFrameBuffer()

  private var imageUpdated = false
  private var exportTarget: CalculatorView? = null
  private var exportFile: Fi? = null

  private var imageScale = 1f/Scl.scl()
  private var padding = 20f
  private var backgroundAlpha = 1f

  init {
    titleTable.clear()
    build()

    hidden {
      exportTarget = null
    }

    resized {
      cont.clearChildren()
      build()
    }
  }

  fun show(target: CalculatorView) {
    exportTarget = target
    imageScale = 1f/Scl.scl()
    padding = 20f
    backgroundAlpha = 1f

    imageUpdated = true

    show()
  }

  fun build(){
    cont.table(Consts.darkGrayUIAlpha) { t ->
      t.table(Consts.darkGrayUI) { top ->
        top.left().add(
          Core.bundle["dialog.calculator.export"]
        ).pad(8f)
      }.growX().fillY().padBottom(12f)
      t.row()
      t.table(Consts.darkGrayUI) { inner ->
        inner.table { prev ->
          prev.left().defaults().grow().pad(5f)
          val img = prev.table(Tex.pane).margin(4f).size(340f).get()
            .image(imgPreview).scaling(Scaling.fit).update { i ->
              if (imageUpdated) {
                flush()

                i.setDrawable(imgPreview)
                imageUpdated = false
              }
            }.get()
          img.clicked {
            BaseDialog("").apply {
              cont.pane(Styles.horizontalPane) { pane ->
                pane.image(imgPreview).scaling(Scaling.fit).grow()
              }.grow().pad(30f).scrollX(true).scrollY(true)
              cont.row()
              cont.add("").color(Color.lightGray).update { l ->
                l.setText(
                  Core.bundle.format(
                    "dialog.calculator.exportPrev",
                    previewBuffer.width,
                    previewBuffer.height,
                    Mathf.round(Scl.scl(imageScale)*100)
                  )
                )
              }
              titleTable.clear()
              addCloseButton()
            }.show()
          }
          prev.row()
          prev.add("").color(Color.lightGray).update { l ->
            l.setText(
              Core.bundle.format(
                "dialog.calculator.exportPrev",
                previewBuffer.width,
                previewBuffer.height,
                Mathf.round(Scl.scl(imageScale)*100)
              )
            )
          }
        }.fill()

        if (Core.graphics.isPortrait) inner.row()

        inner.pane(Styles.smallPane) { pane ->
          pane.defaults().growX().fillY()
          pane.add(Core.bundle["dialog.calculator.exportOptions"]).color(Pal.accent)
          pane.row()
          pane.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(6f)
          pane.row()
          pane.table { s ->
            s.left().defaults().left()
            s.add(Core.bundle["dialog.calculator.exportPadding"])
            s.add("").update { l -> l.setText(padding.toInt().toString() + "px") }
              .width(80f).padLeft(5f).color(Color.lightGray).right()
            s.slider(0f, Mathf.floor(Scl.scl(350f)).toFloat(), 1f, padding) { f ->
              padding = f
              imageUpdated = true
            }.minWidth(240f).growX().padLeft(5f)
          }
          pane.row()
          pane.add(Core.bundle["dialog.calculator.exportScale"])
          pane.row()
          pane.table { scl ->
            scl.defaults().growX().height(45f)
            var n = 0
            var scale = 0.25f
            while (scale <= 2f) {
              n++
              val real = scale/Scl.scl()
              scl.button(Mathf.round(scale*100).toString() + "%", Styles.flatTogglet) {
                imageScale = real
                imageUpdated = true
              }.update { b -> b.isChecked = Mathf.equal(imageScale, real) }
              if (n%2 == 0) scl.row()
              scale += 0.25f
            }
          }
          pane.row()
          pane.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(6f)
          pane.row()
          pane.table { s ->
            s.left().defaults().left()
            s.add(Core.bundle["dialog.calculator.backAlpha"])
            s.add("").update { l -> l.setText("${Mathf.round(backgroundAlpha*100)}%") }
              .width(80f).padLeft(5f).color(Color.lightGray)
            s.slider(0f, 1f, 0.05f, backgroundAlpha) { f ->
              backgroundAlpha = f
              imageUpdated = true
            }.minWidth(240f).growX().padLeft(5f)
          }
          pane.row()
          pane.table { s ->
            s.left().defaults().left()
            s.check(Core.bundle["dialog.calculator.showGrid"]){
              exportTarget?.showGrid = it
              imageUpdated = true
            }.disabled { exportTarget == null }.update { it.isChecked = exportTarget?.showGrid?:false }
          }
        }.grow()
      }.fill().margin(12f)
      t.row()
      t.table { file ->
        file.left().defaults().left().pad(4f)
        file.add(Core.bundle["dialog.calculator.exportFile"])
        file.add("").color(Color.lightGray).ellipsis(true).growX()
          .update { l -> l.setText(if (exportFile == null) Core.bundle["misc.unset"] else exportFile!!.absolutePath()) }
        file.button({ it.add(Core.bundle["misc.select"]).pad(6f).padLeft(12f).padRight(12f) }, Styles.cleart) {
          Vars.platform.showFileChooser(false, "png") { f ->
            exportFile = f
          }
        }
      }.fill()
      t.row()
      t.table { buttons ->
        buttons.right().defaults().size(92f, 36f).pad(6f)
        buttons.button(Core.bundle["misc.cancel"], Styles.cleart) { this.hide() }
        buttons.button(Core.bundle["misc.export"], Styles.cleart) {
          previewBuffer.beginBind()
          try {
            val pixmap = chunkedBuffer.toPixmap()
            PixmapIO.writePng(exportFile!!, pixmap)
            Vars.ui.showInfo(Core.bundle["dialog.calculator.exportSuccess"])
          } catch (e: ArcRuntimeException) {
            Vars.ui.showException(Core.bundle["dialog.calculator.exportFailed"], e)
            Log.err(e)
          }
          previewBuffer.endBind()
        }.disabled { exportFile == null }
      }.growX()
    }.fill().margin(8f).pad(40f)
  }

  private fun flush(){
    if (exportTarget == null) {
      imgPreview.set((Tex.nomap as TextureRegionDrawable).region)
    }
    else {
      drawCalculatorView(
        chunkedBuffer,
        padding,
        imageScale,
        backgroundAlpha,
      )

      val wf = chunkedBuffer.imageWidth.toFloat()/ChunkedFrameBuffer.maxTextureSize
      val hf = chunkedBuffer.imageHeight.toFloat()/ChunkedFrameBuffer.maxTextureSize
      val rwf = if (wf < 1f) 1f else 1f/wf
      val rhf = if (hf < 1f) 1f else 1f/hf
      val factor = min(rwf, rhf)
      previewBuffer.resize(
        (chunkedBuffer.imageWidth*factor).toInt(),
        (chunkedBuffer.imageHeight*factor).toInt(),
      )
      previewBuffer.begin(Color.clear)
      chunkedBuffer.blit()
      previewBuffer.end()

      val texture = previewBuffer.texture
      imgPreview.set(texture)
      imgPreview.flip(false, true)
    }
  }

  private fun drawCalculatorView(
    buff: ChunkedFrameBuffer,
    padding: Float,
    scl: Float,
    backAlpha: Float,
  ) {
    val view = exportTarget!!
    val bound = view.getBound()

    val width = bound.width + padding*2
    val height = bound.height + padding*2
    val imageWidth = (width*scl).toInt()
    val imageHeight = (height*scl).toInt()

    buff.resize(imageWidth, imageHeight)
    view.drawToBuffer(buff, padding, backAlpha)
  }
}