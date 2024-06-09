package tmi.ui.designer

import arc.Core
import arc.Graphics
import arc.func.Prov
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.input.KeyCode
import arc.math.Angles
import arc.math.Mathf
import arc.math.Rand
import arc.math.geom.Geometry
import arc.math.geom.Vec2
import arc.scene.actions.Actions
import arc.scene.event.ClickListener
import arc.scene.event.ElementGestureListener
import arc.scene.event.InputEvent
import arc.scene.event.Touchable
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.OrderedMap
import arc.struct.Seq
import arc.util.*
import mindustry.core.UI
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.recipe.types.RecipeItem
import tmi.set
import tmi.ui.addEventBlocker
import tmi.util.Consts
import kotlin.math.abs

class ItemLinker @JvmOverloads internal constructor(
  val parentCard: Card,
  val item: RecipeItem<*>,
  val isInput: Boolean,
  val id: Long = Rand(System.nanoTime()).nextLong()
) : Table() {
  val ownerDesigner = parentCard.ownerDesigner
  var expectAmount = 0f

  var links = OrderedMap<ItemLinker, Float>()
  var lines = ObjectMap<ItemLinker, Seq<Any>>() //TODO: Lines
  var dir = 0

  private var linkPos = Vec2()
  private var linking = false
  private var moving = false
  private var tim = false
  private var time = 0f
  private var hovering = Vec2()

  var hover: ItemLinker? = null

  var temp: ItemLinker? = null

  var hoverCard: Card? = null
  var hoverValid: Boolean = false

  init {
    touchablility = Prov { if (ownerDesigner.editLock) Touchable.disabled else Touchable.enabled }

    stack(
      Table { t ->
        t.image(item.icon()).center().scaling(Scaling.fit).size(48f)
      },
      Table { inc ->
        inc.add("", Styles.outlineLabel).padTop(20f).update { l ->
          l.setText(
            if (expectAmount <= 0) "--/s"
            else (if (expectAmount*60 > 1000) UI.formatAmount(
              (expectAmount*60).toLong()
            )
            else Strings.autoFixed(expectAmount*60, 1)) + "/s\n"
          )
        }.get().setAlignment(Align.center)
      }
    ).size(60f)

    fill()

    hovered { Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand) }
    exited { Core.graphics.restoreCursor() }

    update {
      if (tim && Time.globalTime - time > 30) {
        moving = true
        hover = this@ItemLinker
        hovering.set(Core.input.mouse())
        clearActions()
        actions(Actions.scaleTo(1.1f, 1.1f, 0.3f))
        tim = false
      }
    }

    setHandleListener()
    addEventBlocker{ e -> e !is InputEvent || e.keyCode != KeyCode.mouseRight  }
  }

  val isNormalized: Boolean
    get() {
      if (links.size == 1) return true

      var total = 0f
      links.values().forEach { rate ->
        if (rate < 0) return false
        total += rate

        if (total > 1 + Mathf.FLOAT_ROUNDING_ERROR) return false
      }

      return Mathf.equal(total, 1f)
    }

  fun checkMoving() {
    val card = parent as Card

    ownerDesigner.localToDescendantCoordinates(card, hovering)
    adsorption(hovering.x, hovering.y, card)
  }

  fun checkLinking() {
    hover = null
    val card = ownerDesigner.hitCard(hovering.x, hovering.y, false)

    if (card != null && card != parent) {
      ownerDesigner.localToDescendantCoordinates(card, hovering)

      hoverValid = card.checkLinking(this)

      var linker = card.linkerIns.find { l -> l!!.item === item }

      if (linker == null) linker = card.hitLinker(hovering.x, hovering.y)
      if (linker != null) {
        if (!linker.isInput || linker.item.item != item.item) {
          hoverValid = false
        }
        else {
          hover = linker
          hoverCard = card

          hovering[hover!!.x + hover!!.width/2] = hover!!.y + hover!!.height/2
          card.localToAscendantCoordinates(ownerDesigner, hovering)

          return
        }
      }

      hoverCard = card

      if (hover == null) {
        if (temp == null || temp!!.item != item) {
          temp = ItemLinker(parentCard, item, true)
          temp!!.draw()
          temp!!.pack()
        }

        hover = temp
        hover!!.parent = card
      }

      if (!hover!!.adsorption(hovering.x, hovering.y, card)) {
        resetHov()

        card.localToAscendantCoordinates(ownerDesigner, hovering)

        return
      }

      hovering[hover!!.x + hover!!.width/2] = hover!!.y + hover!!.height/2
      card.localToAscendantCoordinates(ownerDesigner, hovering)
      hover!!.parent = null

      return
    }

    resetHov()
  }

  fun adsorption(posX: Float, posY: Float, targetCard: Card?): Boolean {
    if (((posX < targetCard!!.child.x || posX > targetCard.child.x + targetCard.child.width)
          && (posY < targetCard.child.y || posY > targetCard.child.y + targetCard.child.height))
    ) return false

    val angle = Angles.angle(
      targetCard.child.x + targetCard.child.width/2,
      targetCard.child.y + targetCard.child.height/2,
      posX,
      posY
    )
    val check = Angles.angle(targetCard.width, targetCard.height)

    if (angle > check && angle < 180 - check) {
      dir = 1
      val offY = targetCard.child.height/2 + getHeight()/1.5f
      setPosition(posX, targetCard.child.y + targetCard.child.height/2 + offY, Align.center)
    }
    else if (angle > 180 - check && angle < 180 + check) {
      dir = 2
      val offX = -targetCard.child.width/2 - getWidth()/1.5f
      setPosition(targetCard.child.x + targetCard.child.width/2 + offX, posY, Align.center)
    }
    else if (angle > 180 + check && angle < 360 - check) {
      dir = 3
      val offY = -targetCard.child.height/2 - getHeight()/1.5f
      setPosition(posX, targetCard.child.y + targetCard.child.height/2 + offY, Align.center)
    }
    else {
      dir = 0
      val offX = targetCard.child.width/2 + getWidth()/1.5f
      setPosition(targetCard.child.x + targetCard.child.width/2 + offX, posY, Align.center)
    }

    return true
  }

  fun linkTo(target: ItemLinker) {
    check(!isInput) { "Only output can do link" }
    check(target.isInput) { "Cannot link input to input" }

    links.put(target, -1f)
    target.links.put(this, -1f)
    lines.put(target, null)
    target.lines.put(this, null)

    (parent as Card).observeUpdate()
    (target.parent as Card).observeUpdate(true)
  }

  fun setPresent(target: ItemLinker, pres: Float) {
    check(!isInput) { "Only output can do link" }
    check(target.isInput) { "Cannot link input to input" }

    links[target] = pres
    target.links[this] = pres

    (parent as Card).observeUpdate()
    (target.parent as Card).observeUpdate()
  }

  fun deLink(target: ItemLinker) {
    if (target.item.item != item.item) return

    links.remove(target)
    target.links.remove(this)
    lines.remove(target)
    target.lines.remove(this)

    (parent as Card).observeUpdate()
    (target.parent as Card).observeUpdate(true)
  }

  private fun updateLinkPos() {
    val p = Geometry.d4(dir)
    linkPos.set(p.x.toFloat(), p.y.toFloat())
      .scl(width/2 + Scl.scl(24f), height/2 + Scl.scl(24f))
      .add(width/2, height/2)
      .add(x, y)
  }

  private fun resetHov() {
    hover = null
    hoverCard = null
  }

  private fun setHandleListener() {
    addListener(object : ElementGestureListener() {
      var beginX: Float = 0f
      var beginY: Float = 0f
      var panned: Boolean = false

      var moveHandle: MoveLinkerHandle? = null

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (pointer != 0 || button != KeyCode.mouseLeft) return

        (parent as Card).rise()
        ownerDesigner.moveLock(true)

        setOrigin(Align.center)
        isTransform = true

        beginX = x
        beginY = y

        tim = true
        time = Time.globalTime
        linking = false
        moving = false

        panned = false

        if (isInput) {
          tim = false
          moving = true
          hover = this@ItemLinker
          hovering.set(Core.input.mouse())
          clearActions()
          actions(Actions.scaleTo(1.1f, 1.1f, 0.3f))
          tim = false
        }
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (pointer != 0 || button != KeyCode.mouseLeft) return

        moveHandle = null

        if (!isInput && linking) {
          if (hover != null && hoverValid) {
            val hover = hover!!
            val canLink = run a@{
              if (hover.item.item != item.item) return@a false
              val cont = links.containsKey(hover) && hover.links.containsKey(this@ItemLinker)

              !cont
            }

            if (canLink) {
              if (hover.parent == null) hoverCard!!.addIn(hover)
              ownerDesigner.pushHandle(DoLinkHandle(ownerDesigner, this@ItemLinker, hover, false))
            }
            else {
              ownerDesigner.pushHandle(DoLinkHandle(ownerDesigner, this@ItemLinker, hover, true))
              if (hover.links.isEmpty) hoverCard!!.removeChild(hover)
            }
          }
        }

        if (!panned && ownerDesigner.selecting == null) {
          ownerDesigner.selecting = this@ItemLinker
        }

        if (moving) {
          clearActions()
          actions(Actions.scaleTo(1f, 1f, 0.3f))
        }

        ownerDesigner.moveLock(false)
        resetHov()
        temp = null
        tim = false
        linking = false
        moving = false
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        super.pan(event, x, y, deltaX, deltaY)

        if (!panned && abs((x - beginX).toDouble()) < 14 && abs((y - beginY).toDouble()) < 14) return

        if (!panned) {
          panned = true
        }

        hovering.set(x, y)
        localToAscendantCoordinates(ownerDesigner, hovering)
        if (tim && !isInput && Time.globalTime - time < 30) {
          linking = true
          tim = false
        }

        if (linking) {
          checkLinking()
        }
        else if (moving) {
          if (moveHandle == null) moveHandle = MoveLinkerHandle(ownerDesigner, this@ItemLinker)
            .also { ownerDesigner.pushHandle(it) }

          checkMoving()
          moveHandle!!.endX = this@ItemLinker.x
          moveHandle!!.endY = this@ItemLinker.y
          moveHandle!!.endDir = dir
        }
      }
    })

    clicked {
      ownerDesigner.parentDialog.showMenu(this@ItemLinker, Align.bottom, Align.top) { frame ->
        frame.table(Consts.padDarkGrayUIAlpha) {
          //TODO
        }
      }
    }
  }

  override fun draw() {
    super.draw()

    updateLinkPos()

    Lines.stroke(Scl.scl(4f))
    Draw.alpha(parentAlpha)
    for (link in links.keys()) {
      if (!link!!.isInput) continue

      drawLinkLine(link.linkPos, link.dir)
    }

    val c = if (linking)
      if (hoverCard == null || (hoverValid && !hover!!.links.containsKey(this)))
        Pal.accent
      else Color.crimson
    else Color.white
    Draw.color(c, parentAlpha)
    val pos = linkPos

    val angle = dir*90 + (if (isInput) 180 else 0)
    val triangleRad = Scl.scl(12f)
    Fill.tri(
      pos.x + Angles.trnsx(angle.toFloat(), triangleRad),
      pos.y + Angles.trnsy(angle.toFloat(), triangleRad),
      pos.x + Angles.trnsx((angle + 120).toFloat(), triangleRad),
      pos.y + Angles.trnsy((angle + 120).toFloat(), triangleRad),
      pos.x + Angles.trnsx((angle - 120).toFloat(), triangleRad),
      pos.y + Angles.trnsy((angle - 120).toFloat(), triangleRad)
    )

    if (linking) {
      if (hover != null) {
        if (hover!!.parent == null && hoverCard != null) {
          Tmp.v1[hoverCard!!.x] = hoverCard!!.y

          val cx = hover!!.x
          val cy = hover!!.y

          Tmp.v2.set(hovering)
          ownerDesigner.localToDescendantCoordinates(this, Tmp.v2)
          hover!!.setPosition(x + Tmp.v2.x, y + Tmp.v2.y, Align.center)
          Draw.mixcol(Color.crimson, if (hoverValid) 0f else 0.5f)
          hover!!.draw()
          Draw.mixcol()
          Lines.stroke(Scl.scl(4f), c)
          drawLinkLine(hover!!.linkPos, hover!!.dir)
          hover!!.x = cx
          hover!!.y = cy
        }
        else {
          Lines.stroke(Scl.scl(4f), c)
          drawLinkLine(hover!!.linkPos, hover!!.dir)
        }
      }
      else {
        val lin = linkPos
        Tmp.v2.set(hovering)
        ownerDesigner.localToDescendantCoordinates(this, Tmp.v2)

        Lines.stroke(Scl.scl(4f), c)
        drawLinkLine(
          lin.x, lin.y, dir,
          x + Tmp.v2.x, y + Tmp.v2.y, dir - 2
        )

        val an = dir*90
        Fill.tri(
          x + Tmp.v2.x + Angles.trnsx(an.toFloat(), triangleRad),
          y + Tmp.v2.y + Angles.trnsy(an.toFloat(), triangleRad),
          x + Tmp.v2.x + Angles.trnsx((an + 120).toFloat(), triangleRad),
          y + Tmp.v2.y + Angles.trnsy((an + 120).toFloat(), triangleRad),
          x + Tmp.v2.x + Angles.trnsx((an - 120).toFloat(), triangleRad),
          y + Tmp.v2.y + Angles.trnsy((an - 120).toFloat(), triangleRad)
        )
      }
    }
  }

  fun drawLinkLine(to: Vec2, tdir: Int) {
    val from = linkPos
    drawLinkLine(from.x, from.y, dir, to.x, to.y, tdir)
  }

  fun drawLinkLine(from: Vec2, fdir: Int, to: Vec2, tdir: Int) {
    drawLinkLine(from.x, from.y, fdir, to.x, to.y, tdir)
  }

  fun drawLinkLine(fx: Float, fy: Float, fdir: Int, tx: Float, ty: Float, tdir: Int) {
    val dst = Mathf.dst(fx, fy, tx, ty)
    val off = dst*0.35f

    val p1 = Geometry.d4(fdir)
    val p2 = Geometry.d4(tdir)

    Lines.curve(
      fx, fy,
      fx + p1.x*off, fy + p1.y*off,
      tx + p2.x*off, ty + p2.y*off,
      tx, ty,
      (dst/0.45f).toInt()
    )
  }

  fun copy(): ItemLinker {
    val res = ItemLinker(parentCard, item, isInput)

    res.setBounds(x, y, width, height)
    res.expectAmount = expectAmount
    res.dir = dir

    return res
  }
}
