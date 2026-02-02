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
import arc.scene.Element
import arc.scene.actions.Actions
import arc.scene.event.ElementGestureListener
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.OrderedMap
import arc.util.*
import mindustry.Vars
import mindustry.core.UI
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.recipe.types.RecipeItem
import tmi.util.set
import tmi.ui.addEventBlocker
import tmi.util.*
import kotlin.math.abs

@Deprecated("Use recipe calculator")
data class LinkEntry(val link: ItemLinker){
  var rate: Float = -1f
}

@Deprecated("Use recipe calculator")
class ItemLinker @JvmOverloads internal constructor(
  val parentCard: Card,
  val item: RecipeItem<*>,
  val isInput: Boolean,
  id: Long = Rand(System.nanoTime()).nextLong()
) : Table() {
  var id = id
    private set

  val ownerDesigner = parentCard.ownerDesigner
  var expectAmount = 0f

  val links = OrderedMap<ItemLinker, LinkEntry>()
  val linkFoldCtrl = ObjectMap<ItemLinker, FoldLink>()

  var dir = 0

  var linking = false
  var moving = false

  var linkPos = Vec2()

  private var timing = false
  private var time = 0f
  private var hovering = Vec2()
  private var linksUpdated = false

  private var temp: ItemLinker? = null
  private var tempFold: FoldLink? = null

  var hover: ItemLinker? = null

  var hoverCard: Card? = null
  var hoverValid: Boolean = false

  var lineColor: Color = Color.white
    set(value) {
      field = value
      colorSetCount = 1
    }
  private var colorSetCount = 0

  init {
    touchablility = Prov { if (ownerDesigner.editLock) Touchable.disabled else Touchable.enabled }

    stack(
      Table { t ->
        t.image(item.icon).center().scaling(Scaling.fit).size(48f)
      },
      Table { inc ->
        inc.add("", Styles.outlineLabel).padTop(20f).update { l ->
          l.setText(
            if (expectAmount <= 0) "--/s"
            else {
              val (value, unit) = Utils.unitTimed(expectAmount)

              (if (value > 1000) UI.formatAmount(value.toLong()) else Strings.autoFixed(value, 1)) + unit + "\n"
            }
          )
        }.get().setAlignment(Align.center)
      }
    ).size(60f)

    fill()

    hovered { Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand) }
    exited { Core.graphics.restoreCursor() }

    update {
      if (timing && Time.globalTime - time > 30) {
        moving = true
        hover = this@ItemLinker
        hovering.set(Core.input.mouse())
        clearActions()
        actions(Actions.scaleTo(1.1f, 1.1f, 0.3f))
        timing = false
      }
    }

    setHandleListener()
    addEventBlocker { e -> e !is InputEvent || e.keyCode != KeyCode.mouseRight  }
  }

  val isNormalized: Boolean
    get() {
      if (links.size == 1) return true

      var total = 0f
      links.values().forEach { ent ->
        if (ent.rate < 0) return false
        total += ent.rate

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
    val card = ownerDesigner.hitCard(hovering.x, hovering.y, false) { c ->
      c.inputTypes().contains(item)
    }?: ownerDesigner.hitCard(hovering.x, hovering.y, false)

    linkFoldCtrl.values().forEach { it.linesColor.set(Pal.accent) }
    if (card != null && card != parent) {
      ownerDesigner.localToDescendantCoordinates(card, hovering)

      hoverValid = card.checkLinking(this)

      var linker = card.linkerIns.find { l -> l.item === item }

      if (linker == null) linker = card.hitLinker(hovering.x, hovering.y)
      if (linker != null) {
        if (!linker.isInput || linker.item.item != item.item) {
          hoverValid = false
        }
        else {
          hover = linker
          hoverCard = card

          val fold = linkFoldCtrl[linker]
          if (fold != null && hover!!.links.containsKey(this)){
            fold.linesColor.set(Color.crimson)
            ownerDesigner.showLines = fold
          }

          hovering.set(hover!!.x + hover!!.width/2, hover!!.y + hover!!.height/2)
          card.localToAscendantCoordinates(ownerDesigner, hovering)

          return
        }
      }

      hoverCard = card

      if (hover == null) {
        if (temp == null || temp!!.item != item || temp!!.parentCard != card) {
          temp = ItemLinker(card, item, true)
          temp!!.draw()
          temp!!.pack()
        }

        hover = temp
        hover!!.parent = card
      }

      val hover = hover!!

      if (!hover.adsorption(hovering.x, hovering.y, card)) {
        resetHov()

        card.localToAscendantCoordinates(ownerDesigner, hovering)

        return
      }

      hovering.set(hover.x + hover.width/2, hover.y + hover.height/2)
      card.localToAscendantCoordinates(ownerDesigner, hovering)
      hover.parent = null

      if (hoverValid && card.isFold){
        if (tempFold != null) return

        object: FoldLink(card, hover, false){
          override fun draw() {
            super.draw()

            val offV = Geometry.d4(dir)

            val from = linkPos
            val to = vec1.set(getX(Align.center) - offV.x*width/2f, getY(Align.center) - offV.y*height/2)

            val orig = color1.set(Draw.getColor())
            Lines.stroke(Scl.scl(4f), card.foldColor)
            Lines.curve(
              from.x, from.y,
              from.x + offV.x*Scl.scl(20f), from.y + offV.y*Scl.scl(20f),
              to.x - offV.x*Scl.scl(20f), to.y - offV.y*Scl.scl(20f),
              to.x, to.y,
              100
            )
            Draw.color(orig)
          }
        }.also {
          tempFold = it
          linkFoldCtrl[hover] = it
          it.setSize(Scl.scl(50f))
          addChild(it)

          ownerDesigner.showLines = it
        }
      }
    }
    else resetHov()

    if (card == null || !card.isFold || !hoverValid){
      tempFold?.remove()
      if (tempFold != null) linkFoldCtrl.remove(tempFold!!.linker)
      tempFold = null
      ownerDesigner.showLines = null
    }
  }

  fun adsorption(posX: Float, posY: Float, targetCard: Card): Boolean {
    val cardWidth = targetCard.width
    val cardHeight = targetCard.height

    if (((posX < 0f || posX > cardWidth)
      && (posY < 0f || posY > cardHeight))
    ) return false

    dir = Geom.angleToD4Integer(
      posX - cardWidth/2,
      posY - cardHeight/2,
      targetCard.width,
      targetCard.height
    )

    val off = getHeight()/1.5f
    when(dir){
      0 -> setPosition(cardWidth + off, posY, Align.center)
      1 -> setPosition(posX, cardHeight + off, Align.center)
      2 -> setPosition(-off, posY, Align.center)
      3 -> setPosition(posX, -off, Align.center)
    }

    return true
  }

  fun linkTo(target: ItemLinker) {
    if (target.item.item != item.item) return

    target.linksUpdated = true
    linksUpdated = true

    links.put(target, LinkEntry(target))
    target.links.put(this, LinkEntry(this))

    (parent as Card).observeUpdate()
    (target.parent as Card).observeUpdate(true)
  }

  fun deLink(target: ItemLinker) {
    if (target.item.item != item.item) return

    target.linksUpdated = true
    linksUpdated = true

    links.remove(target)
    target.links.remove(this)

    (parent as Card).observeUpdate()
    (target.parent as Card).observeUpdate(true)
  }

  fun setProportion(target: ItemLinker, pres: Float) {
    if (target.item.item != item.item) return

    links[target].rate = pres
    target.links[this].rate = pres

    (parent as Card).observeUpdate()
    (target.parent as Card).observeUpdate()
  }

  fun averange() {
    if (!isInput) return
    val pros = links.associate { it.key to (it.value.rate.takeIf { n -> n >= 0f }?: (1f/links.size)) }

    val total = pros.values.sum()
    links.forEach { it.value.rate = pros[it.key]!!/total }
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

        setOrigin(Align.center)

        beginX = x
        beginY = y

        timing = true
        time = Time.globalTime
        linking = false
        moving = false

        panned = false

        if (isInput) {
          timing = false
          moving = true
          hover = this@ItemLinker
          hovering.set(Core.input.mouse())
          timing = false
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

        if (!panned && isInput && links.size > 1) {
          showProportionConfigure()
        }

        resetHov()
        temp = null
        timing = false
        linking = false
        moving = false
        tempFold?.remove()
        if (tempFold != null) linkFoldCtrl.remove(tempFold!!.linker)
        tempFold = null
        ownerDesigner.showLines = null
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        super.pan(event, x, y, deltaX, deltaY)

        if (!panned && abs((x - beginX).toDouble()) < 14 && abs((y - beginY).toDouble()) < 14) return

        if (!panned) {
          panned = true
        }

        hovering.set(x, y)
        localToAscendantCoordinates(ownerDesigner, hovering)
        if (timing && !isInput && Time.globalTime - time < 30) {
          linking = true
          timing = false
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
  }

  private fun showProportionConfigure() {
    ownerDesigner.parentDialog.showMenu(this@ItemLinker, Align.bottom, Align.top) { frame ->
      standardizeProportion()

      val pie = PieChartSetter(
        proportionEntries = links.map { it.key to it.value.rate },
        callback = { l ->
          ownerDesigner.pushHandle(
            SetLinkPresentHandle(
              ownerDesigner,
              this@ItemLinker,
              l.toMap()
            )
          )
        },
        colorSetter = { to, c -> to.lineColor = c }
      )
      frame.table(Consts.padDarkGrayUIAlpha) { table ->
        table.table(Consts.grayUI) { main ->
          main.add(Core.bundle["dialog.calculator.proportionAssign"]).pad(5f)
          main.row()
          main.add(pie).pad(12f)
          main.row()
          main.button(Core.bundle["misc.average"], Icon.rotateSmall, Styles.cleart, 24f) { pie.average() }
            .fill().margin(4f).pad(4f)
        }.pad(4f).fill().get()
      }
    }
  }

  fun standardizeProportion() {
    if (isNormalized) {
      if (links.size == 1) setProportion(links.first().key, 1f)
      return
    }

    val pros = links.associate { it.key to (it.value.rate.takeIf { f -> f >= 0 }?:(1f/links.size)) }

    val total = pros.values.sum()
    val set = links.associate { it.key to (pros[it.key] ?: (1f/links.size))/total }

    ownerDesigner.pushHandle(SetLinkPresentHandle(ownerDesigner, this, set))
  }

  fun updateLinks() {
    linksUpdated = true
    links.keys().forEach { it.linksUpdated = true }
  }

  fun newSetId() {
    id = Rand(System.nanoTime()).nextLong()
  }

  override fun act(delta: Float) {
    super.act(delta)

    if (linksUpdated && !parentCard.isFold){
      linksUpdated = false

      linkFoldCtrl.values().forEach { it.remove() }
      linkFoldCtrl.clear()
      links.keys().forEach { link ->
        if (link.parentCard.isFold) {
          val elem = object: FoldLink(link.parentCard, link, false){
            override fun draw() {
              super.draw()
              if (!link.parentCard.isFold) return

              val offV = Geometry.d4(dir)

              val from = linkPos
              val to = vec1.set(getX(Align.center) - offV.x*width/2f, getY(Align.center) - offV.y*height/2)

              val orig = color1.set(Draw.getColor())
              Lines.stroke(Scl.scl(4f), link.parentCard.foldColor)
              Lines.curve(
                from.x, from.y,
                from.x + offV.x*Scl.scl(20f), from.y + offV.y*Scl.scl(20f),
                to.x - offV.x*Scl.scl(20f), to.y - offV.y*Scl.scl(20f),
                to.x, to.y,
                100
              )
              Draw.color(orig)
            }
          }
          addChild(elem)
          elem.setSize(Scl.scl(50f))
          elem.addEventBlocker()

          elem.addListener(object: InputListener(){
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?)
              { ownerDesigner.showLines = elem }
            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?)
              { if (toActor != elem) ownerDesigner.showLines = null }
          })
          elem.clicked(KeyCode.mouseLeft) {
            ownerDesigner.focusTo(link.parentCard)
          }

          linkFoldCtrl.put(link, elem)
        }
      }
    }

    if (!parentCard.isFold){
      val lenH = linkFoldCtrl.size*Scl.scl(72f)
      val offV = Geometry.d4(dir)
      val offH = Geometry.d4(dir - 1)
      val from = vec1.set(offV.x.toFloat(), offV.y.toFloat())
        .scl(width/2 + Scl.scl(24f), height/2 + Scl.scl(24f))
        .add(width/2, height/2)

      linkFoldCtrl.forEachIndexed { i, e ->
        val linkCtrl = e.value
        val offset = -lenH/2f + (i + 0.5f)*Scl.scl(72f)

        val to = Vec2(from.x + offV.x*Scl.scl(70f) + offH.x*offset, from.y + offV.y*Scl.scl(70f) + offH.y*offset)

        linkCtrl.setPosition(
          to.x + offV.x*linkCtrl.width/2,
          to.y + offV.y*linkCtrl.height/2,
          Align.center
        )
      }
    }

    if (colorSetCount > 0){
      colorSetCount--
      return
    }

    lineColor = Color.white
  }

  override fun draw() {
    updateLinkPos()

    val inStage = parentCard.inStage
    if (inStage) {
      super.draw()
    }

    if (!isInput) {
      Draw.reset()
      Lines.stroke(Scl.scl(4f), lineColor)
      Draw.alpha(parentAlpha)

      for (link in links.keys()) {
        if (link.parentCard.isFold || !(link.parentCard.inStage || inStage)) continue

        drawLinkLine(link.linkPos, link.dir)
      }
    }

    val c = if (linking)
      if (hoverCard == null || (hoverValid && !hover!!.links.containsKey(this)))
        Pal.accent
      else Color.crimson
    else lineColor

    Lines.stroke(Scl.scl(4f))
    Draw.alpha(parentAlpha)
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
      drawLinking(c, triangleRad)
    }
  }

  private fun drawLinking(c: Color, triangleRad: Float) {
    if (hover != null) {
      if (hoverCard!!.isFold) return

      val hover = hover!!
      if (hover.parent == null && hoverCard != null) {
        Tmp.v1.set(hoverCard!!.x, hoverCard!!.y)

        val cx = hover.x
        val cy = hover.y

        Tmp.v2.set(hovering)
        ownerDesigner.localToDescendantCoordinates(this, Tmp.v2)
        hover.setPosition(x + Tmp.v2.x, y + Tmp.v2.y, Align.center)
        Draw.mixcol(Color.crimson, if (hoverValid) 0f else 0.5f)
        hover.draw()
        Draw.mixcol()
        Lines.stroke(Scl.scl(4f), c)
        drawLinkLine(hover.linkPos, hover.dir)
        hover.x = cx
        hover.y = cy
      }
      else {
        Lines.stroke(Scl.scl(4f), c)
        drawLinkLine(hover.linkPos, hover.dir)
      }
    }
    else {
      val lin = linkPos
      Tmp.v2.set(hovering)
      ownerDesigner.localToDescendantCoordinates(this, Tmp.v2)

          Vars.indexer
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
