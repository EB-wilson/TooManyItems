package tmi.ui.designer

import arc.Core
import arc.graphics.Camera
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.graphics.gl.FrameBuffer
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Tmp
import mindustry.graphics.Pal
import tmi.ui.Side

object SchematicGraphDrawer {
  fun drawFoldPane(
    view: DesignerView,
    buff: FrameBuffer,
    scl: Float,
    foldedSide: Side,
    cardScl: Float,
    padding: Float,
    targetWidth: Float,
    targetHeight: Float
  ){
    val pane = buildFoldedPane(view, foldedSide, padding, cardScl, targetWidth, targetHeight)
    val size = Vec2(pane.width, pane.height)

    buff.resize((size.x*scl).toInt(), (size.y*scl).toInt())
    buff.begin(Pal.darkestGray)
    val camera = Camera()
    camera.width = size.x
    camera.height = size.y
    camera.position.x = size.x/2f
    camera.position.y = size.y/2f
    camera.update()
    Draw.proj(camera)

    pane.setPosition(0f, 0f)
    pane.draw()
    Lines.stroke(Scl.scl(2f), Color.black)
    Lines.quad(
      0f, 0f,
      size.x, 0f,
      size.x, size.y,
      0f, size.y
    )

    Draw.proj()
    buff.end()
  }

  private fun buildFoldedPane(
    view: DesignerView,
    foldedSide: Side,
    padding: Float,
    cardScl: Float,
    targetWidth: Float,
    targetHeight: Float
  ): Table {
    val res = Table()
    res.add(Core.bundle["dialog.calculator.foldedCards"]).pad(16f).growX().fillY()
    res.row()
    res.image().color(Color.black).growX().height(4f).padBottom(8f)
    res.row()

    val list = mutableListOf<Table>()
    if (foldedSide == Side.LEFT || foldedSide == Side.RIGHT) {
      val cont = res.table().fillX().growY().get().top()
      var table = Table()
      view.foldCards.forEach { card ->
        val elem = buildCardShower(view, card, foldedSide, cardScl, padding)

        if (table.prefHeight + elem.prefHeight > targetHeight) {
          list.add(table)
          table = Table()
        }
        table.center().add(elem).fillY().growX().pad(padding).row()
      }
      if (table.children.any()) list.add(table)

      if (foldedSide == Side.RIGHT) list.forEach { cont.add(it).fill() }
      else list.reversed().forEach { cont.add(it).fill() }

      res.setSize(
        res.prefWidth,
        targetHeight
      )
    }
    else {
      val cont = res.table().fillY().growX().get().left()
      var table = Table()
      view.foldCards.forEach { card ->
        val elem = buildCardShower(view, card, foldedSide, cardScl, padding)

        if (table.prefWidth + elem.prefWidth > targetWidth) {
          list.add(table)
          table = Table()
        }
        table.top().add(elem).fillX().growY().pad(padding)
      }
      if (table.children.any()) list.add(table)

      if (foldedSide == Side.TOP) list.forEach { cont.add(it).fill().row() }
      else list.reversed().forEach { cont.add(it).fill().row() }

      res.setSize(
        targetWidth,
        res.prefHeight
      )
    }

    return res
  }

  private fun buildCardShower(
    view: DesignerView,
    card: Card,
    side: Side,
    cardScl: Float,
    padding: Float
  ): Element {
    val linker = view.foldLinkers[card]
    val dh = card.height*cardScl + padding + linker.height

    return object: Element(){
      val mh = card.height + padding + linker.height
      override fun draw() {
        validate()

        val dx: Float
        val dy: Float
        when(side) {
          Side.LEFT -> { dx = x + width - card.width*cardScl; dy = y }
          Side.RIGHT -> { dx = x; dy = y + height - mh }
          Side.TOP -> { dx = x; dy = y }
          Side.BOTTOM -> { dx = x; dy = y + height - mh }
        }
        drawFoldCard(
          card,
          cardScl,
          linker,
          dx, dy,
          padding
        )
      }

      override fun getPrefWidth(): Float {
        return card.width*cardScl
      }

      override fun getPrefHeight(): Float {
        return dh
      }
    }
  }

  private fun drawFoldCard(
    card: Card,
    cardScl: Float,
    foldLinker: FoldLink,
    x: Float,
    y: Float,
    padding: Float,
  ) {
    val origPar = card.parent
    val origLinPar = foldLinker.parent
    val origX = card.x
    val origY = card.y
    val origLinX = foldLinker.x
    val origLinY = foldLinker.y
    val origTrans = Tmp.m1.set(Draw.trans())

    foldLinker.parent = null
    card.x = 0f
    card.y = 0f
    foldLinker.x = x + card.width*cardScl/2 - foldLinker.width/2
    foldLinker.y = y + card.height*cardScl + padding

    card.singleRend()
    card.invalidate()
    Draw.trans(Tmp.m2.setToTranslation(x, y).scale(cardScl, cardScl))
    card.isTransform = false
    card.draw()
    card.isTransform = true
    Draw.trans(origTrans)
    card.invalidate()

    foldLinker.invalidate()
    foldLinker.draw()
    foldLinker.invalidate()

    card.parent = origPar
    foldLinker.parent = origLinPar
    card.x = origX
    card.y = origY
    foldLinker.x = origLinX
    foldLinker.y = origLinY
  }

  fun drawCardsContainer(
    view: DesignerView,
    buff: FrameBuffer,
    boundX: Float,
    boundY: Float,
    scl: Float
  ): FrameBuffer {
    val bound = view.getBound()

    val width = bound.width + boundX*2
    val height = bound.height + boundY*2

    val dx = bound.x - boundX
    val dy = bound.y - boundY

    val camera = Camera()
    camera.width = width
    camera.height = height
    camera.position.x = dx + width/2f
    camera.position.y = dy + height/2f
    camera.update()

    val pan = view.panned
    val par = view.container.parent
    val x = view.container.x
    val y = view.container.y
    val sclX = view.zoom.scaleX
    val sclY = view.zoom.scaleY
    val scW = Core.scene.width
    val scH = Core.scene.height

    view.zoom.scaleX = 1f
    view.zoom.scaleY = 1f
    view.panned = Vec2.ZERO
    view.container.parent = null
    view.container.x = 0f
    view.container.y = 0f
    Core.scene.viewport.worldWidth = width
    Core.scene.viewport.worldHeight = height

    view.cards.forEach { it.singleRend() }
    view.container.draw()

    val imageWidth = (width*scl).toInt()
    val imageHeight = (height*scl).toInt()

    buff.resize(imageWidth, imageHeight)
    buff.begin(Pal.darkerGray)
    Draw.proj(camera)
    view.drawToImage()
    Draw.flush()
    buff.end()

    view.container.parent = par
    view.container.x = x
    view.container.y = y
    view.zoom.scaleX = sclX
    view.zoom.scaleY = sclY
    view.panned = pan
    Core.scene.viewport.worldWidth = scW
    Core.scene.viewport.worldHeight = scH

    return buff
  }
}