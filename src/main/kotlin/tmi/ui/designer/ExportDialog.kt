package tmi.ui.designer

import arc.Core
import arc.files.Fi
import arc.graphics.Color
import arc.graphics.PixmapIO
import arc.graphics.g2d.TextureRegion
import arc.graphics.gl.FrameBuffer
import arc.math.Mathf
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Dialog
import arc.scene.ui.Image
import arc.scene.ui.Label
import arc.scene.ui.TextButton
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Log
import arc.util.Scaling
import mindustry.Vars
import mindustry.gen.Tex
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.util.Consts

class ExportDialog(ownerDesigner: SchematicDesignerDialog) : Dialog("", Consts.transparentBack) {
  private val buffer = FrameBuffer()
  private val tmp = TextureRegion((Tex.nomap as TextureRegionDrawable).region)

  private var exportFile: Fi? = null

  private var imageScale = 1f
  private var boundX = 0f
  private var boundY = 0f
  private var updated = false

  init {
    shown {
      ownerDesigner.view!!.toBuffer(buffer, boundX, boundY, imageScale)
      updated = true
    }

    titleTable.clear()

    val cell = cont.table(Consts.darkGrayUIAlpha) { t ->
      t.table(Consts.darkGrayUI) { top ->
        top.left().add(
          Core.bundle["dialog.calculator.export"]
        ).pad(8f)
      }.grow().padBottom(12f)
      t.row()
      t.table(Consts.darkGrayUI).grow().margin(12f).get().top().pane(Styles.smallPane) { inner ->
        inner.left().defaults().growX().fillY().pad(5f)
        val img = inner.image(tmp).scaling(Scaling.fit).fill().size(400f).update { i ->
          if (updated) {
            tmp.set(buffer.texture)
            tmp.flip(false, true)
            i.setDrawable(tmp)
            updated = false
          }
        }.get()
        img.clicked {
          BaseDialog("").apply {
            cont.image(tmp).grow().pad(30f).scaling(Scaling.fit)
            cont.row()
            cont.add("").color(Color.lightGray).update { l ->
              l.setText(
                Core.bundle.format(
                  "dialog.calculator.exportPrev",
                  buffer.width,
                  buffer.height,
                  Mathf.round(imageScale*100)
                )
              )
            }
            titleTable.clear()
            addCloseButton()
          }.show()
        }
        inner.row()
        inner.add("").color(Color.lightGray).update { l ->
          l.setText(
            Core.bundle.format(
              "dialog.calculator.exportPrev",
              buffer.width,
              buffer.height,
              Mathf.round(imageScale*100)
            )
          )
        }
        inner.row()
        inner.table { s ->
          s.left().defaults().left()
          s.add(Core.bundle["dialog.calculator.exportBoundX"])
          s.add("").update { l -> l.setText(boundX.toInt().toString() + "px") }
            .width(80f).padLeft(5f).color(Color.lightGray).right()
          s.slider(0f, 200f, 1f, boundX) { f ->
            boundX = f
            ownerDesigner.view!!.toBuffer(buffer, boundX, boundY, imageScale)
            updated = true
          }.growX().padLeft(5f)
          s.row()
          s.add(Core.bundle["dialog.calculator.exportBoundY"])
          s.add("").update { l -> l.setText(boundY.toInt().toString() + "px") }
            .width(80f).padLeft(5f).color(Color.lightGray)
          s.slider(0f, 200f, 1f, boundY) { f ->
            boundY = f
            ownerDesigner.view!!.toBuffer(buffer, boundX, boundY, imageScale)
            updated = true
          }.growX().padLeft(5f)
        }
        inner.row()
        inner.add(Core.bundle["dialog.calculator.exportScale"])
        inner.row()
        inner.table { scl ->
          scl.defaults().growX().height(45f)
          var n = 0
          var scale = 0.25f
          while (scale <= 2f) {
            n++
            val fs = scale
            scl.button(Mathf.round(scale*100).toString() + "%", Styles.flatTogglet) {
              imageScale = fs
              ownerDesigner.view!!.toBuffer(buffer, boundX, boundY, imageScale)
              updated = true
            }.update { b -> b.isChecked = Mathf.equal(imageScale, fs) }
            if (n%2 == 0) scl.row()
            scale += 0.25f
          }
        }
      }.minWidth(420f).grow().margin(8f)
      t.row()
      t.table { file ->
        file.left().defaults().left().pad(4f)
        file.add(Core.bundle["dialog.calculator.exportFile"])
        file.add("").color(Color.lightGray).ellipsis(true).growX()
          .update { l -> l.setText(if (exportFile == null) Core.bundle["misc.unset"] else exportFile!!.absolutePath()) }
        file.button(Core.bundle["misc.select"], Styles.cleart) {
          Vars.platform.showFileChooser(false, "png") { f: Fi? ->
            exportFile = f
          }
        }.size(60f, 42f)
      }.width(420f)
      t.row()
      t.table { buttons ->
        buttons.right().defaults().size(92f, 36f).pad(6f)
        buttons.button(Core.bundle["misc.cancel"], Styles.cleart) { this.hide() }
        buttons.button(Core.bundle["misc.export"], Styles.cleart) {
          try {
            val region = ownerDesigner.view!!.toImage(boundX, boundY, imageScale)
            PixmapIO.writePng(exportFile, region.texture.textureData.pixmap)

            Vars.ui.showInfo(Core.bundle["dialog.calculator.exportSuccess"])
          } catch (e: Exception) {
            Vars.ui.showException(Core.bundle["dialog.calculator.exportFailed"], e)
            Log.err(e)
          }
        }.disabled { exportFile == null }
      }.growX()
    }.grow().margin(8f)

    resized {
      cell.maxSize(Core.scene.width/Scl.scl(), Core.scene.height/Scl.scl())
      cell.get().invalidateHierarchy()
    }
  }
}
