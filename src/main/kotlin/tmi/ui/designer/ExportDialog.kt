package tmi.ui.designer

import arc.Core
import arc.files.Fi
import arc.graphics.Color
import arc.graphics.GL30
import arc.graphics.Gl
import arc.graphics.g2d.TextureRegion
import arc.graphics.gl.FrameBuffer
import arc.math.Mathf
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Dialog
import arc.scene.ui.layout.Scl
import arc.util.*
import mindustry.Vars
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.ui.Side
import tmi.util.Consts
import kotlin.math.max

class ExportDialog(private val view: DesignerView) : Dialog("", Consts.transparentBack) {
  private val assembledBuffer = FrameBuffer()
  private val cardContainerBuffer = FrameBuffer()
  private val foldCardsBuffer = FrameBuffer()
  private val tmp = TextureRegion((Tex.nomap as TextureRegionDrawable).region)

  private var exportFile: Fi? = null
  private var foldPaneSide: Side = Side.LEFT

  private var imageScale = 1f
  private var cardScale = 0.7f
  private var boundX = 0f
  private var boundY = 0f
  private var foldPanePad = Scl.scl(4f)
  private var contUpdated = true
  private var foldUpdated = true

  init {
    titleTable.clear()
    buildInner()
  }

  private fun buildInner() {
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
          val img = prev.table(Tex.pane).margin(4f).size(340f).get().image(tmp).scaling(Scaling.fit).update { i ->
            val b = contUpdated or foldUpdated
            if (contUpdated) {
              SchematicGraphDrawer.drawCardsContainer(view, cardContainerBuffer, boundX, boundY, imageScale)
              contUpdated = false
            }
            if (foldUpdated) {
              SchematicGraphDrawer.drawFoldPane(
                view,
                foldCardsBuffer,
                imageScale,
                foldPaneSide,
                cardScale,
                foldPanePad,
                cardContainerBuffer.width/imageScale,
                cardContainerBuffer.height/imageScale
              )
              foldUpdated = false
            }

            if (b) {
              assembleImage()
              tmp.set(assembledBuffer.texture)
              tmp.flip(false, true)
              i.setDrawable(tmp)
            }
          }.get()
          img.clicked {
            BaseDialog("").apply {
              cont.pane(Styles.horizontalPane) { pane ->
                pane.image(tmp).grow().scaling(Scaling.fit)
              }.grow().pad(30f).scrollX(true).scrollY(true)
              cont.row()
              cont.add("").color(Color.lightGray).update { l ->
                l.setText(
                  Core.bundle.format(
                    "dialog.calculator.exportPrev",
                    cardContainerBuffer.width,
                    cardContainerBuffer.height,
                    Mathf.round(imageScale*100)
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
                cardContainerBuffer.width,
                cardContainerBuffer.height,
                Mathf.round(imageScale*100)
              )
            )
          }
        }.fill()

        if (Core.graphics.isPortrait) inner.row()

        inner.pane(Styles.smallPane) { pane ->
          pane.defaults().growX().fillY()
          pane.add(Core.bundle["dialog.calculator.mainView"]).color(Pal.accent)
          pane.row()
          pane.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(6f)
          pane.row()
          pane.table { s ->
            s.left().defaults().left()
            s.add(Core.bundle["dialog.calculator.exportBoundX"])
            s.add("").update { l -> l.setText(boundX.toInt().toString() + "px") }
              .width(80f).padLeft(5f).color(Color.lightGray).right()
            s.slider(0f, 300f, 1f, boundX) { f ->
              boundX = f
              contUpdated = true
              foldUpdated = true
            }.minWidth(240f).growX().padLeft(5f)
            s.row()
            s.add(Core.bundle["dialog.calculator.exportBoundY"])
            s.add("").update { l -> l.setText(boundY.toInt().toString() + "px") }
              .width(80f).padLeft(5f).color(Color.lightGray)
            s.slider(0f, 300f, 1f, boundY) { f ->
              boundY = f
              contUpdated = true
              foldUpdated = true
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
              val fs = scale
              scl.button(Mathf.round(scale*100).toString() + "%", Styles.flatTogglet) {
                imageScale = fs
                contUpdated = true
                foldUpdated = true
              }.update { b -> b.isChecked = Mathf.equal(imageScale, fs) }
              if (n%2 == 0) scl.row()
              scale += 0.25f
            }
          }
          pane.row()
          pane.add(Core.bundle["dialog.calculator.foldPane"]).color(Pal.accent)
          pane.row()
          pane.image().color(Pal.accent).growX().height(4f).padTop(4f).padBottom(6f)
          pane.row()
          pane.table { s ->
            s.left().defaults().left()
            s.add(Core.bundle["dialog.calculator.foldedCardPadding"])
            s.add("").update { l -> l.setText(Strings.autoFixed(foldPanePad, 1)) }
              .width(80f).padLeft(5f).color(Color.lightGray).right()
            s.slider(0f, 40f, 0.5f, foldPanePad) { f ->
              foldPanePad = f
              foldUpdated = true
            }.minWidth(240f).growX().padLeft(5f)
            s.row()
            s.add(Core.bundle["dialog.calculator.foldedCardScale"])
            s.add("").update { l -> l.setText("${Mathf.round(cardScale*100)}%") }
              .width(80f).padLeft(5f).color(Color.lightGray)
            s.slider(0.25f, 2f, 0.05f, cardScale) { f ->
              cardScale = f
              foldUpdated = true
            }.minWidth(240f).growX().padLeft(5f)
          }
          pane.row()
          pane.add(Core.bundle["dialog.calculator.exportSide"])
          pane.row()
          pane.table { sides ->
            sides.defaults().growX().height(45f)
            Side.entries.forEach { s ->
              sides.button(Core.atlas.getDrawable("tmi-side_${s.name}"), Styles.clearNoneTogglei) {
                foldPaneSide = s
                foldUpdated = true
              }.update { b -> b.isChecked = foldPaneSide == s }
            }
          }.pad(6f)
        }.grow().maxHeight(400f)
      }.growY().fillX().margin(12f)
      t.row()
      t.table { file ->
        file.left().defaults().left().pad(4f)
        file.add(Core.bundle["dialog.calculator.exportFile"])
        file.add("").color(Color.lightGray).ellipsis(true).growX()
          .update { l -> l.setText(if (exportFile == null) Core.bundle["misc.unset"] else exportFile!!.absolutePath()) }
        file.button(Core.bundle["misc.select"], Styles.cleart) {
          Vars.platform.showFileChooser(false, "png") { f ->
            exportFile = f
          }
        }.size(60f, 42f)
      }.growY().fillX()
      t.row()
      t.table { buttons ->
        buttons.right().defaults().size(92f, 36f).pad(6f)
        buttons.button(Core.bundle["misc.cancel"], Styles.cleart) { this.hide() }
        buttons.button(Core.bundle["misc.export"], Styles.cleart) {
          assembledBuffer.beginBind()
          try {
            ScreenUtils.saveScreenshot(
              exportFile!!,
              0, 0, assembledBuffer.width, assembledBuffer.height
            )
            Vars.ui.showInfo(Core.bundle["dialog.calculator.exportSuccess"])
          } catch (e: ArcRuntimeException) {
            Vars.ui.showException(Core.bundle["dialog.calculator.exportFailed"], e)
            Log.err(e)
          }
          assembledBuffer.endBind()
        }.disabled { exportFile == null }
      }.growX()
    }.grow().margin(8f).pad(40f)
  }

  private fun assembleImage() {
    if (foldPaneSide == Side.LEFT || foldPaneSide == Side.RIGHT) {
      val w = cardContainerBuffer.width + foldCardsBuffer.width
      val h = max(cardContainerBuffer.height, foldCardsBuffer.height)

      assembledBuffer.resize(w, h)
    }
    else {
      val w = max(cardContainerBuffer.width, foldCardsBuffer.width)
      val h = cardContainerBuffer.height + foldCardsBuffer.height

      assembledBuffer.resize(w, h)
    }

    val offCX: Int
    val offCY: Int
    val offFX: Int
    val offFY: Int

    when(foldPaneSide) {
      Side.LEFT -> { offCX = foldCardsBuffer.width; offCY = 0; offFX = 0; offFY = 0 }
      Side.RIGHT -> { offCX = 0; offCY = 0; offFX = cardContainerBuffer.width; offFY = 0; }
      Side.TOP -> { offCX = 0; offCY = 0; offFX = 0; offFY = cardContainerBuffer.height; }
      Side.BOTTOM -> { offCX = 0; offCY = foldCardsBuffer.height; offFX = 0; offFY = 0; }
    }

    assembledBuffer.begin(Color.clear)
    assembledBuffer.end()
    Gl.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, assembledBuffer.framebufferHandle)

    Gl.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, cardContainerBuffer.framebufferHandle)
    Core.gl30.glBlitFramebuffer(
      0, 0, cardContainerBuffer.width, cardContainerBuffer.height,
      offCX, offCY, offCX + cardContainerBuffer.width, offCY + cardContainerBuffer.height,
      Gl.colorBufferBit, Gl.nearest
    )
    Gl.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, foldCardsBuffer.framebufferHandle)
    Core.gl30.glBlitFramebuffer(
      0, 0, foldCardsBuffer.width, foldCardsBuffer.height,
      offFX, offFY, offFX + foldCardsBuffer.width, offFY + foldCardsBuffer.height,
      Gl.colorBufferBit, Gl.nearest
    )
  }
}
