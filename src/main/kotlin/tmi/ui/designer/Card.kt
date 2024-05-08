package tmi.ui.designer

import arc.Core
import arc.func.*
import arc.graphics.*
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.input.KeyCode
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.event.*
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.IntMap
import arc.struct.Seq
import arc.util.Align
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.graphics.Pal
import tmi.recipe.RecipeItemStack
import tmi.util.Consts

abstract class Card(protected val ownerDesigner: SchematicDesignerDialog) : Table() {
  val child: Table = object : Table(Consts.darkGrayUIAlpha) {
    override fun drawBackground(x: Float, y: Float) {
      if (ownerDesigner.view!!.newSet === this@Card) {
        Lines.stroke(Scl.scl(5f))
        Draw.color(Pal.accentBack, parentAlpha)
        Lines.rect(x - Scl.scl(45f), y - Scl.scl(45f), getWidth() + 2*Scl.scl(40f), getHeight() + 2*Scl.scl(40f))
        Draw.color(Pal.accent, parentAlpha)
        Lines.rect(x - Scl.scl(40f), y - Scl.scl(40f), getWidth() + 2*Scl.scl(40f), getHeight() + 2*Scl.scl(40f))
        Draw.color()
      }
      super.drawBackground(x, y)
    }

    override fun getPrefWidth(): Float {
      if (!isSizeAlign) return super.getPrefWidth()

      val gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", 150).toFloat())
      return Mathf.ceil(super.getPrefWidth()/gridSize)*gridSize
    }

    override fun getPrefHeight(): Float {
      if (!isSizeAlign) return super.getPrefHeight()

      val gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", 150).toFloat())
      return Mathf.ceil(super.getPrefHeight()/gridSize)*gridSize
    }
  }.margin(12f)

  val linkerOuts = Seq<ItemLinker>()
  val linkerIns = Seq<ItemLinker>()

  var removing = false
  var aligning = false
  var alignPos = Vec2()

  var mul = 1
  var effScale = 1f
  var isSizeAlign = false
    private set

  fun build() {
    add(child).center().fill().pad(100f)
    buildCard()
  }

  fun rise() {
    ownerDesigner.view!!.cards.remove(this)
    ownerDesigner.view!!.container.removeChild(this)

    ownerDesigner.view!!.cards.add(this)
    ownerDesigner.view!!.container.addChild(this)
  }

  fun addIn(linker: ItemLinker?) {
    linkerIns.add(linker)
    addChild(linker)
  }

  fun addOut(linker: ItemLinker?) {
    linkerOuts.add(linker)
    addChild(linker)
  }

  fun hitLinker(x: Float, y: Float): ItemLinker? {
    for (linker in SchematicDesignerDialog.Companion.seq.clear().addAll(linkerIns).addAll(linkerOuts)) {
      if (x > linker!!.x - linker.width/2
        && x < linker.x + linker.width*1.5f
        && y > linker.y - linker.height/2
        && y < linker.y + linker.height*1.5f) {
        return linker
      }
    }
    return null
  }

  override fun removeChild(element: Element, unfocus: Boolean): Boolean {
    if (element is ItemLinker) {
      linkerIns.remove(element)
      linkerOuts.remove(element)
    }
    return super.removeChild(element, unfocus)
  }

  override fun draw() {
    if (aligning) {
      Draw.reset()
      Consts.darkGrayUIAlpha.draw(x + alignPos.x, y + alignPos.y, child.width, child.height)
    }
    Draw.mixcol(
      if (removing) Color.crimson else Pal.accent, if (removing || ownerDesigner.selects.contains(
          this
        )
      ) 0.5f
      else 0f
    )
    super.draw()
    Draw.mixcol()
  }

  abstract fun buildCard()

  abstract fun buildLinker()

  abstract fun accepts(): Iterable<RecipeItemStack>

  abstract fun outputs(): Iterable<RecipeItemStack>

  protected fun moveListener(element: Element): EventListener {
    return object : ElementGestureListener() {
      var enabled: Boolean = false

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (pointer != 0 || button != KeyCode.mouseLeft || ownerDesigner.view!!.isSelecting) return
        enabled = true
        ownerDesigner.moveLock(true)
        this@Card.removing = false
        ownerDesigner.selects.each { e: Card? -> e!!.removing = false }
        rise()
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (pointer != 0 || button != KeyCode.mouseLeft || ownerDesigner.view!!.isSelecting) return
        val align = ownerDesigner.cardAlign
        val selects = ownerDesigner.selects

        enabled = false
        ownerDesigner.moveLock(false)

        SchematicDesignerDialog.Companion.tmp.set(element.x, element.y)
        element.parent.localToStageCoordinates(SchematicDesignerDialog.Companion.tmp)

        if (ownerDesigner.removeMode && SchematicDesignerDialog.Companion.tmp.y < Core.scene.height*0.15f) {
          if (selects.contains(this@Card)) selects.each { card: Card? -> ownerDesigner.view!!.removeCard(card) }
          else ownerDesigner.view!!.removeCard(this@Card)
        }
        else if (align != -1) {
          if (ownerDesigner.selects.contains(this@Card)) {
            ownerDesigner.selects.each { e: Card? ->
              e!!.aligning = false
              e.gridAlign(align)
            }
          }
          else {
            aligning = false
            gridAlign(align)
          }
        }
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (!enabled) return
        val align = ownerDesigner.cardAlign
        if (ownerDesigner.selects.contains(this@Card)) {
          ownerDesigner.selects.each { e: Card? ->
            e!!.moveBy(deltaX, deltaY)
            e.aligning = align != -1
            if (e.aligning) {
              e.alignPos(align, e.alignPos)
              e.parentToLocalCoordinates(e.alignPos.add(e.child.x, e.child.y))
            }
          }
        }
        else {
          moveBy(deltaX, deltaY)
          aligning = align != -1
          if (aligning) {
            alignPos(align, alignPos)
            parentToLocalCoordinates(alignPos.add(child.x, child.y))
          }
        }

        SchematicDesignerDialog.Companion.tmp.set(element.x, element.y)
        element.parent.localToStageCoordinates(SchematicDesignerDialog.Companion.tmp)

        val b = ownerDesigner.removeMode && SchematicDesignerDialog.Companion.tmp.y < Core.scene.height*0.15f
        if (ownerDesigner.selects.contains(this@Card)) ownerDesigner.selects.each { e: Card? -> e!!.removing = b }
        else this@Card.removing = b
      }
    }
  }

  fun adjustSize(alignGrid: Boolean) {
    isSizeAlign = alignGrid
    if (alignGrid) {
      invalidate()
      validate()
    }
    else {
      child.pack()
    }
    pack()

    gridAlign(ownerDesigner.cardAlign)

    for (linker in Seq(linkerIns).addAll(linkerOuts)) {
      linker!!.adsorption(linker.x + linker.width/2, linker.y + linker.height/2, this)
    }
  }

  fun gridAlign(align: Int) {
    if (align == -1) return

    val out = alignPos(align, SchematicDesignerDialog.Companion.tmp)
    setPosition(out.x, out.y)
  }

  fun alignPos(align: Int, out: Vec2): Vec2 {
    val gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", 150).toFloat())
    var ex = this@Card.x + child.getX(align)
    var ey = this@Card.y + child.getY(align)

    ex = Mathf.round(ex/gridSize)*gridSize
    ey = Mathf.round(ey/gridSize)*gridSize

    if ((align and Align.left) != 0) ex -= child.x
    else if ((align and Align.right) != 0) ex += getWidth() - child.x - child.width

    if ((align and Align.bottom) != 0) ey -= child.y
    else if ((align and Align.top) != 0) ey += getHeight() - child.y - child.height

    if ((align and Align.right) != 0) ex -= width
    else if ((align and Align.left) == 0) ex -= width/2

    if ((align and Align.top) != 0) ey -= height
    else if ((align and Align.bottom) == 0) ey -= height/2

    return out.set(ex, ey)
  }

  abstract fun copy(): Card

  abstract fun write(write: Writes)

  companion object {
    @JvmStatic
    protected val provs: IntMap<Func<Reads, Card>> = IntMap()

    fun read(read: Reads, ver: Int): Card {
      val id = read.i()
      return provs[id][read]
    }
  }
}
