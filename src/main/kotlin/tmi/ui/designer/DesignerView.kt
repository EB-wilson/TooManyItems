package tmi.ui.designer

import arc.Core
import arc.Events
import arc.Graphics.Cursor
import arc.func.Cons
import arc.func.Floatf
import arc.func.Prov
import arc.graphics.*
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.graphics.gl.FrameBuffer
import arc.input.KeyCode
import arc.math.Interp
import arc.math.Mathf
import arc.math.geom.Bezier
import arc.math.geom.Geometry
import arc.math.geom.Rect
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.Group
import arc.scene.actions.Actions
import arc.scene.event.ElementGestureListener
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.style.Drawable
import arc.scene.ui.ScrollPane
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.scene.ui.layout.WidgetGroup
import arc.struct.LongMap
import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import arc.util.Align
import arc.util.Time
import arc.util.Tmp
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.f
import tmi.invoke
import tmi.recipe.Recipe
import tmi.set
import tmi.ui.Side
import tmi.ui.addEventBlocker
import tmi.util.*
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class DesignerView(val parentDialog: SchematicDesignerDialog) : Group() {
  companion object{
    private val seq: Seq<ItemLinker> = Seq()
    private const val SHD_REV = 7
  }

  var currAlignIcon: Drawable = Icon.none
  var removeMode = false
  var cardAlign = -1
  var selectMode = false
  var editLock = false
  var isSelecting = false

  var newSet: Card? = null
  var selecting: ItemLinker? = null
  val menuPos: Element = Element()

  val cards = Seq<Card>()
  val foldCards = Seq<Card>()

  val selects = ObjectSet<Card>()
  val foldLinkers = ObjectMap<Card, FoldLink>()

  private val shownCards = ObjectSet<Card>()

  private val history = Seq<DesignerHandle>()
  private var historyIndex = 0
  private var updatedIndex = historyIndex

  private val selectBegin: Vec2 = Vec2()
  private val selectEnd: Vec2 = Vec2()

  private var enabled: Boolean = false
  private var timer: Float = 0f

  private var lock: Boolean = false

  private var lastZoom: Float = -1f
  private var panX: Float = 0f
  private var panY: Float = 0f

  private lateinit var zoomBar: Table
  private lateinit var foldBottom: Table

  private var foldShown = false

  val isUpdated
    get() = updatedIndex != historyIndex
  fun makeSaved(){ updatedIndex = historyIndex }

  val container: Group = object : Group() {
    override fun act(delta: Float) {
      super.act(delta)

      setPosition(panX + parent.width/2f, panY + parent.height/2f, Align.center)
    }

    override fun draw() {
      Lines.stroke(Scl.scl(4f), Pal.gray)
      Draw.alpha(parentAlpha)
      val gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", 150).toFloat())

      var offX = 0f
      while (offX <= (Core.scene.width)/zoom.scaleX - panX) {
        Lines.line(x + offX, -Core.scene.height/zoom.scaleY, x + offX, Core.scene.height/zoom.scaleY*2)
        offX += gridSize
      }
      offX = 0f
      while (offX >= -(Core.scene.width)/zoom.scaleX - panX) {
        Lines.line(x + offX, -Core.scene.height/zoom.scaleY, x + offX, Core.scene.height/zoom.scaleY*2)
        offX -= gridSize
      }

      var offY = 0f
      while (offY <= (Core.scene.height)/zoom.scaleY - panY) {
        Lines.line(-Core.scene.width/zoom.scaleX, y + offY, Core.scene.width/zoom.scaleX*2, y + offY)
        offY += gridSize
      }
      offY = 0f
      while (offY >= -(Core.scene.height)/zoom.scaleY - panY) {
        Lines.line(-Core.scene.width/zoom.scaleX, y + offY, Core.scene.width/zoom.scaleX*2, y + offY)
        offY -= gridSize
      }
      super.draw()
    }
  }

  val zoom: Group = object : Group() {
    init {
      setFillParent(true)
      isTransform = true

      Core.app.post { setOrigin(Align.center) } // after handle
    }

    override fun draw() {
      validate()
      super.draw()
    }
  }

  var showLines: FoldLink? = null

  private var foldUpdate = false

  private val foldLinkerPane = object: WidgetGroup(){
    override fun act(delta: Float) {
      super.act(delta)

      if (foldUpdate) {
        foldUpdate = false

        clearChildren()
        foldLinkers.clear()

        foldCards.forEach { c ->
          buildFoldLinker(c).also {
            addChild(it)
            foldLinkers.put(c, it)
          }
        }

        validate()
      }

      foldLinkers.each { c, e ->
        val v = c.localToAscendantCoordinates(foldBottom, c.panePos.add(c.paneSize.scl(0.5f)))
        val pos = foldBottom.localToDescendantCoordinates(this, v)

        e.setPosition(pos.x, height/2, Align.center)
      }
    }

    override fun getPrefHeight() = Scl.scl(40f)
  }
  val foldPane: Table = object: Table(){
    var inv = false

    override fun validate() {
      super.validate()
      if (inv) {
        invalidateHierarchy()
        inv = false
      }
    }

    override fun layout() {
      super.layout()
      foldCards.forEach { card ->
        card.isTransform = true
        card.pack()
        val paneSize = card.paneSize
        val panePos = card.panePos
        val scl = height/paneSize.y

        card.width -= 2*panePos.x
        card.height -= 2*panePos.y
        card.width *= scl
        card.height *= scl

        card.setOrigin(Align.center)
        card.scaleX = scl
        card.scaleY = scl

        getCell(card)?.size(
          paneSize.x*scl/Scl.scl(),
          paneSize.y*scl/Scl.scl()
        )?.padLeft(8f)?.padRight(8f)
      }
    }

    override fun childrenChanged() {
      super.childrenChanged()
      inv = true
      foldUpdate = true
    }
  }.left().apply { children.reverse() }
  private val foldScrollPane = ScrollPane(foldPane, Styles.noBarPane)
  private val foldTable = object: Table(Consts.midGrayUI){
    override fun validate() {
      val parent = parent
      if (parent != null) {
        val parentWidth = parent.width

        if (width != parentWidth) {
          setWidth(parentWidth)
          invalidate()
        }
      }
      super.validate()
    }
  }

  val foldHeight: Float
    get() = foldTable.height

  fun build() {
    addChild(menuPos)
    zoom.addChild(container)
    fill { t -> t.add(zoom).grow() }

    fill { t ->
      zoomBar = t.top().table { z ->
        z.add("25%").color(Color.gray)
        z.table(Consts.darkGrayUIAlpha).fill().get().slider(0.25f, 1f, 0.01f){}
          .update { s -> s.setValue(zoom.scaleX) }
          .width(400f)
          .touchable(Touchable.disabled)
        z.add("100%").color(Color.gray)
      }.growX().top().get().also { it.color.a = 0f }
      t.row()
      t.add(Core.bundle["dialog.calculator.editLock"], Styles.outlineLabel).padTop(60f).visible { editLock }
    }

    foldTable.image().color(Pal.darkestGray).height(2f).growX().padBottom(-2f)
    foldTable.row()
    foldTable.add(Element()).growX().height(7f).get().apply {
      hovered { Core.graphics.cursor(Cursor.SystemCursor.verticalResize) }
      exited { Core.graphics.restoreCursor() }
      addListener(object: ElementGestureListener(){
        var off = 0f

        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
          localToAscendantCoordinates(this@DesignerView, vec1.set(x, y))
          off = vec1.y - foldTable.height
        }

        override fun pan(event: InputEvent?, x: Float, y: Float, deltaX: Float, deltaY: Float) {
          localToAscendantCoordinates(this@DesignerView, vec1.set(x, y))
          foldTable.height = Mathf.clamp(vec1.y - off, Scl.scl(180f), this@DesignerView.height)
        }
      })
      touchablility = Prov{ Touchable.enabled.takeIf { foldShown }?: Touchable.disabled }
      addEventBlocker()
    }
    foldTable.row()
    foldTable.table{
      it.left().add("Folded Cards").padLeft(8f)
      it.add().growX()
      val but = it.button(Icon.upOpenSmall, Styles.clearNonei, 28f){}.get()
      but.clicked { foldShown = !foldShown }
      but.update {
        foldTable.y = if (foldShown) 0f else -foldTable.getRowHeight(4)
        but.style.imageUp = Icon.downOpenSmall.takeIf { foldShown }?: Icon.upOpenSmall
      }
    }.growX().fillY().left().margin(5f).marginTop(0f)
    foldTable.row()
    foldTable.image().color(Pal.darkestGray).height(2f).growX()
    foldTable.row()

    foldBottom = foldTable.table{
      it.add(foldLinkerPane).growX().fillY().left().padTop(4f).padBottom(4f)
      it.row()
      it.left().add(foldScrollPane).left().fillX().growY().scrollY(false).scrollX(true)
    }.grow().maxHeight(480f).pad(8f).get()
    val p = foldScrollPane
    foldBottom.touchable = Touchable.enabled
    foldBottom.addEventBlocker{ e -> e !is InputEvent || e.keyCode != KeyCode.mouseRight }
    foldBottom.addListener(object: InputListener(){
      override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?) { p.requestScroll() }

      override fun scrolled(event: InputEvent, x: Float, y: Float, sx: Float, sy: Float): Boolean {
        p.scrollX += min(p.scrollWidth, max((p.scrollWidth*0.9f), (p.maxX*0.1f))/4f)*sy
        return true
      }
    })

    addChild(foldTable)
    foldTable.pack()
    foldTable.height = Scl.scl(320f)
    foldTable.validate()
    foldTable.y = -foldTable.getRowHeight(4)

    fill { _, _, _, _ ->
      val showLines = showLines?:return@fill

      val from = showLines.centerPos
      val card = showLines.card

      val interp = Time.globalTime%180f/180f
      if (showLines.inFold) {
        shownCards.clear()
        seq.clear().addAll(card.linkerOuts).addAll(card.linkerIns).forEach { linker ->
          linker.links.keys().forEach lns@{ other ->
            val lerp = if (linker.isInput) 1 - interp else interp

            if (other.parentCard.isFold){
              val to = foldLinkers[other.parentCard]?.centerPos?: return@fill
              drawFoldCurveLink(from, to, showLines.linesColor, !shownCards.contains(other.parentCard), lerp)
              shownCards.add(other.parentCard)
            }
            else {
              val to = other.linkFoldCtrl[linker]?.centerPos?: return@fill
              drawFoldLineLink(from, to, showLines.linesColor, true, lerp)
            }
          }
        }
      }
      else if (foldShown && showLines.linker != null){
        val lerp = if (showLines.linker.isInput) interp else 1 - interp
        val to = foldLinkers[card]?.centerPos?: return@fill

        drawFoldLineLink(from, to, showLines.linesColor, true, lerp)
      }
    }

    update {
      val mul = 2.8f.takeIf { Core.input.keyDown(TooManyItems.binds.hotKey) }?: 1f
      if (Core.input.keyDown(KeyCode.right)) {
        panX -= 10*Time.delta/zoom.scaleX/Scl.scl()*mul
        clamp()
      }
      if (Core.input.keyDown(KeyCode.left)) {
        panX += 10*Time.delta/zoom.scaleX/Scl.scl()*mul
        clamp()
      }

      if (Core.input.keyDown(KeyCode.up)) {
        panY -= 10*Time.delta/zoom.scaleY/Scl.scl()*mul
        clamp()
      }
      if (Core.input.keyDown(KeyCode.down)) {
        panY += 10*Time.delta/zoom.scaleY/Scl.scl()*mul
        clamp()
      }
    }

    setPanListener()
    setSelectListener()
    setZoomListener()
    setAreaSelectListener()
  }

  private fun drawFoldCurveLink(
    from: Vec2,
    to: Vec2,
    color: Color,
    linkLine: Boolean,
    lerp: Float,
  ) {
    val step = 1f/80

    Lines.stroke(
      Scl.scl(2f + Mathf.absin(Time.globalTime, 6f, 1f)),
      color
    )
    Draw.alpha(0.5f + Mathf.absin(Time.globalTime, 5f, 0.2f))

    if (linkLine) {
      Lines.beginLine()
      for (i in 0..80) {
        val p = Bezier.cubic(
          vec1, step*i,
          from, Tmp.v1.set(from).add(0f, Scl.scl(60f)),
          Tmp.v2.set(to).add(0f, Scl.scl(60f)), to,
          Tmp.v3
        )

        Lines.linePoint(p)
      }
      Lines.endLine()
    }

    val v = Bezier.cubic(
      vec1, Mathf.clamp(lerp),
      from, Tmp.v1.set(from).add(0f, Scl.scl(60f)),
      Tmp.v2.set(to).add(0f, Scl.scl(60f)), to,
      Tmp.v3
    )
    val d = Bezier.cubicDerivative(
      vec2, Mathf.clamp(lerp),
      from, Tmp.v1.set(from).add(0f, Scl.scl(60f)),
      Tmp.v2.set(to).add(0f, Scl.scl(60f)), to,
      Tmp.v3
    ).nor().scl(Scl.scl(12f*Interp.pow5.apply(Interp.slope.apply(lerp))))

    Shapes.line(
      v.x, v.y, color,
      v.x + d.x, v.y + d.y, Tmp.c1.set(color).a(0f)
    )
    Shapes.line(
      v.x, v.y, color,
      v.x - d.x, v.y - d.y, Tmp.c1.set(color).a(0f)
    )
  }

  private fun drawFoldLineLink(
    from: Vec2,
    to: Vec2,
    color: Color,
    linkLine: Boolean,
    lerp: Float,
  ) {
    Lines.stroke(
      Scl.scl(2f + Mathf.absin(Time.globalTime, 6f, 1f)),
      color
    )
    Draw.alpha(0.5f + Mathf.absin(Time.globalTime, 5f, 0.2f))

    if (linkLine) {
      Lines.line(
        from.x, from.y,
        to.x, to.y
      )
    }

    val v = vec1.set(to).sub(from).scl(lerp)
    val d = vec2.set(v).nor().scl(Scl.scl(12f*Interp.pow5.apply(Interp.slope.apply(lerp))))
    v.add(from)

    Shapes.line(
      v.x, v.y, color,
      v.x + d.x, v.y + d.y, Tmp.c1.set(color).a(0f)
    )
    Shapes.line(
      v.x, v.y, color,
      v.x - d.x, v.y - d.y, Tmp.c1.set(color).a(0f)
    )
  }

  private fun buildFoldLinker(c: Card): FoldLink {
    val hover = Vec2()
    var isHovering = false
    var linkValid = false

    var hoveringCard: Card? = null

    val linkerPairs = ObjectMap<ItemLinker, ItemLinker>()
    val existedToLinkers = ObjectSet<ItemLinker>()
    val tmpFromLinkers = ObjectSet<ItemLinker>()
    val tmpToLinkers = ObjectSet<ItemLinker>()
    val linkingFold = ObjectSet<FoldLink>()

    val elem = object : FoldLink(c, null, true) {
      override fun draw() {
        super.draw()

        val interp = Time.globalTime%180f/180f
        if (isHovering && hoveringCard == null) {
          drawFoldLineLink(centerPos, vec1.set(x, y).add(hover), Pal.accent, true, interp)
        }
        else linkingFold.forEach {
          drawFoldLineLink(
            centerPos,
            it.centerPos,
            if (!existedToLinkers.contains(it.linker)) Pal.accent else Color.crimson,
            true,
            interp
          )
        }
      }

      override fun act(delta: Float) {
        super.act(delta)

        linkerPairs.keys().forEach { if (it.parent == null) it.act(delta) }
      }
    }

    val resetPan = fun() {
      c.linkerOuts.removeAll { it.parent != it.parentCard }
      linkerPairs.forEach e@{
        if (tmpToLinkers.contains(it.value) || existedToLinkers.contains(it.key)) return@e
        it.value.linkFoldCtrl.remove(it.key)
      }
      linkingFold.forEach e@{
        if (existedToLinkers.contains(it.linker)) return@e
        it.remove()
      }
      tmpFromLinkers.forEach { it.remove() }
      tmpToLinkers.forEach { it.remove() }

      linkerPairs.clear()
      existedToLinkers.clear()
      tmpFromLinkers.clear()
      tmpToLinkers.clear()
      linkingFold.clear()
      hoveringCard = null
      showLines = null
      linkValid = false
    }

    val checkLinking = fun() {
      val v = elem.localToAscendantCoordinates(this@DesignerView, vec1.set(hover))
      val card = hitCard(v.x, v.y, inner = false, fold = false)

      if (hoveringCard != null && hoveringCard != card) resetPan()

      hoveringCard = card

      if (card != null && card != c) {
        val out = c.outputs().map { it.item }.toSet()
        val accept = card.accepts().map { it.item }.toSet()

        val validItems = out intersect accept

        if (validItems.isEmpty()) return

        validItems.forEach l@{ item ->
          var from = c.linkerOuts.find { it.item == item }
          var to = card.linkerIns.find { it.item == item }

          if (from == null) {
            from = ItemLinker(c, item, false)
            tmpFromLinkers.add(from)
            from.pack()
            from.adsorption(c.width/2, c.height/2, c)
            c.addOut(from)
          }
          if (to == null) {
            to = ItemLinker(card, item, true)
            tmpToLinkers.add(to)
            to.pack()
            card.addIn(to)
          }

          if (card.checkLinking(from)) {
            if (linkerPairs.put(from, to) == null) {
              val linking = to.linkFoldCtrl[from]
              if (linking == null) {
                object : FoldLink(c, to, false) {
                  override fun draw() {
                    super.draw()

                    val offV = Geometry.d4(to.dir)

                    val f = to.linkPos
                    val t = vec1.set(getX(Align.center) - offV.x*width/2f, getY(Align.center) - offV.y*height/2)

                    val orig = color1.set(Draw.getColor())
                    Lines.stroke(Scl.scl(4f), card.foldColor)
                    Lines.curve(
                      f.x, f.y,
                      f.x + offV.x*Scl.scl(20f), f.y + offV.y*Scl.scl(20f),
                      t.x - offV.x*Scl.scl(20f), t.y - offV.y*Scl.scl(20f),
                      t.x, t.y,
                      100
                    )
                    Draw.color(orig)
                  }
                }.also {
                  to.linkFoldCtrl[from] = it
                  it.setSize(Scl.scl(50f))
                  to.addChild(it)
                  linkingFold.add(it)
                }
              }
              else {
                existedToLinkers.add(from)
                linkingFold.add(linking)
              }
            }
          }
        }

        this@DesignerView.localToDescendantCoordinates(card, v)

        linkValid = !linkerPairs.isEmpty

        val panePos = card.panePos
        val paneSize = card.paneSize
        val dir = Geom.angleToD4Integer(
          v.x - panePos.x - paneSize.x/2,
          v.y - panePos.y - paneSize.y/2,
          card.width, card.height
        )
        val d = Geometry.d4(dir + 1)

        val lenH = tmpToLinkers.size*Scl.scl(72f)
        tmpToLinkers.forEachIndexed n@{ i, it ->
          val off = -lenH/2f + (i + 0.5f)*Scl.scl(72f)
          it.adsorption(v.x + d.x*off, v.y + d.y*off, card)
        }
      }
      else {
        resetPan()
      }
    }

    elem.setSize(Scl.scl(40f))

    elem.addEventBlocker()
    elem.addListener(object : InputListener() {
      override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        Core.graphics.cursor(Cursor.SystemCursor.hand)
        showLines = elem
      }

      override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?) {
        Core.graphics.restoreCursor()
        showLines = null
      }

      override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) = true

      override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
        isHovering = false

        if (linkValid) {
          val list = mutableListOf<DesignerHandle>()

          tmpToLinkers.clear()
          tmpFromLinkers.clear()
          linkerPairs.forEach {
            if (existedToLinkers.contains(it.key)) {
              list.add(DoLinkHandle(this@DesignerView, it.key, it.value, true))
            }
            else {
              list.add(DoLinkHandle(this@DesignerView, it.key, it.value, false))
            }
          }
          pushHandle(CombinedHandles(this@DesignerView, *list.toTypedArray()))
        }

        resetPan()
      }

      override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        hover.set(x, y)
        isHovering = true

        checkLinking()
      }
    })
    elem.clicked {
      FoldIconCfgDialog(this, c).show()
    }

    return elem
  }

  fun foldCard(card: Card) {
    card.adjustSize(false)

    card.isFold = true

    removeCard(card, false)

    foldPane.add(card)
    foldCards.add(card)
  }

  fun unfoldCard(card: Card) {
    card.isTransform = false
    card.isFold = false
    card.scaleX = 1f
    card.scaleY = 1f
    card.pack()

    removeCard(card, false)
    
    cards.add(card)
    container.addChild(card)
  }

  fun showFoldPane(card: Card? = null) {
    if (card?.isFold == false) return
    if (!foldShown) foldShown = true

    if (card == null) return

    val x = card.getX(Align.center)
    foldScrollPane.scrollX = Mathf.clamp(
      x - foldScrollPane.width/2,
      0f, foldScrollPane.maxX
    )
  }

  fun addRecipe(recipe: Recipe): RecipeCard {
    return RecipeCard(this, recipe).also {
      pushHandle(AddCardHandle(this, it))
      buildCard(it)
      newSet = it
      it.over!!.visible = true
      it.rebuildConfig()
    }
  }

  fun addIO(ioCard: IOCard) {
    pushHandle(AddCardHandle(this, ioCard))
    buildCard(ioCard)
    newSet = ioCard
  }

  fun setMoveLocker(inner: Element) {
    inner.addListener(object : InputListener() {
      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
        moveLock(true)
        return true
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        moveLock(false)
      }
    })
  }

  fun moveLock(lock: Boolean) {
    this.lock = lock
  }

  fun checkCardClip(card: Card): Boolean {
    if (card.isFold) return foldShown

    val v1 = container.localToAscendantCoordinates(this, vec2.set(card.x, card.y))
    val v2 = container.localToAscendantCoordinates(this, vec3.set(card.x, card.y).add(card.width, card.height))

    return v1.x < width && v2.x > x
        && v1.y < height && v2.y > y
  }

  fun addCard(card: Card, addAction: Boolean = true) {
    if (card.isFold){
      foldCards.add(card)
      foldPane.add(card)
    }
    else {
      cards.add(card)
      container.addChild(card)
    }

    if (addAction) card.added()
  }

  fun alignCard(card: Card, x: Float, y: Float, align: Int){
    card.setPosition(x, y, Align.center)
    card.gridAlign(align)
  }

  fun alignFoldCard(card: Card) {
    foldCards.clear()

    val v = foldPane.stageToLocalCoordinates(
      card.parent.localToStageCoordinates(
        vec1.set(
          card.getX(Align.center),
          card.getY(Align.center)
        )
      )
    )
    val arr = foldPane.cells.select{ it.get() != card }.map { it.get() as Card to it.get().getX(Align.center) }
    arr.add(card to v.x)
    arr.sort(Floatf { it.second })

    foldPane.clearChildren()
    arr.forEach {
      foldPane.add(it.first)
      foldCards.add(it.first)
    }
  }

  fun buildCard(card: Card, x: Float = width/2, y: Float = height/2) {
    localToDescendantCoordinates(container, vec1.set(x, y))

    card.build()
    card.pack()
    card.setPosition(vec1.x, vec1.y, Align.center)

    card.gridAlign(cardAlign)
  }

  fun removeCard(card: Card, delete: Boolean = true) {
    cards.remove(card)
    foldCards.remove(card)

    container.removeChild(card)

    foldPane.clearChildren()
    foldCards.forEach { foldPane.add(it) }
    foldPane.validate()

    if (!delete) return
    seq.clear().addAll(card.linkerIns).addAll(card.linkerOuts)
    for (linker in seq) {
      for (link in linker!!.links.keys().toSeq()) {
        linker.deLink(link)
        if (link!!.links.isEmpty && link.isInput) link.remove()
      }
    }
  }

  fun eachCard(range: Rect, inner: Boolean, fold: Boolean = true, cons: Cons<Card>) {
    val list = if (fold) Seq.withArrays(cards, foldCards) else cards
    for (card in list) {
      val (v1, v2) = checkNorm(inner && !card.isFold, card)
      val ox = v1.x
      val oy = v1.y
      val wx = v2.x
      val wy = v2.y

      rect1.set(ox, oy, wx - ox, wy - oy)
      if (range.contains(rect1) || (!inner && range.overlaps(rect1))) cons(card)
    }
  }

  fun eachCard(x: Float, y: Float, inner: Boolean, fold: Boolean = true, cons: Cons<Card>) {
    Tmp.r1.set(x, y, 0f, 0f)
    eachCard(Tmp.r1, inner, fold, cons)
  }

  fun hitCard(x: Float, y: Float, inner: Boolean, fold: Boolean = true): Card? {
    val list = if (fold) Seq.withArrays(cards, foldCards) else cards
    for (s in list.size - 1 downTo 0) {
      val card = list[s]

      val (v1, v2) = checkNorm(inner && !card.isFold, card)

      val ox = v1.x
      val oy = v1.y
      val wx = v2.x
      val wy = v2.y

      if (ox < x && x < wx && oy < y && y < wy) {
        return card
      }
    }

    return null
  }

  fun standardization() {
    val bound = getBound()

    bound.getCenter(vec1)

    val offX = -vec1.x - container.width/2
    val offY = -vec1.y - container.height/2

    pushHandle(StandardizeHandle(this, offX, offY))

    resetView()
  }

  fun pushHandle(handle: DesignerHandle, doAction: Boolean = true){
    if (history.size <= historyIndex) {
      history.add(handle)
      if (history.size > parentDialog.maximumHistories) history.remove(0)
      else historyIndex++
    }
    else {
      history[historyIndex] = handle
      if (historyIndex < history.size - 1) history.removeRange(historyIndex + 1, history.size - 1)
      historyIndex++
    }
    if (doAction) handle.handle()
  }

  fun canRedo(): Boolean = historyIndex < history.size
  fun canUndo(): Boolean = historyIndex > 0

  fun redoHistory() {
    if (historyIndex < history.size) {
      history[historyIndex].handle()
      Events.fire(RedoEvent(history[historyIndex]))
      historyIndex++
    }
  }

  fun undoHistory() {
    if (historyIndex > 0) {
      historyIndex--
      history[historyIndex].quash()
      Events.fire(UndoEvent(history[historyIndex]))
    }
  }

  fun drawFoldPane(buff: FrameBuffer, scl: Float, foldedSide: Side, cardScl: Float, padding: Float, targetWidth: Float, targetHeight: Float){
    val pane = buildFoldedPane(foldedSide, padding, cardScl, targetWidth, targetHeight)
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

  private fun buildFoldedPane(foldedSide: Side, padding: Float, cardScl: Float, targetWidth: Float, targetHeight: Float): Table {
    val res = Table()
    res.add(Core.bundle["dialog.calculator.foldedCards"]).pad(16f).growX().fillY()
    res.row()
    res.image().color(Color.black).growX().height(4f).padBottom(8f)
    res.row()

    if (foldedSide == Side.LEFT || foldedSide == Side.RIGHT) {
      val cont = res.table().fillX().growY().get().top()
      var table = Table()
      foldCards.forEach { card ->
        val elem = buildCardShower(card, foldedSide, cardScl, padding)

        if (table.prefHeight + elem.prefHeight < targetHeight) {
          table.center().add(elem).fillY().growX().pad(padding).row()
        }
        else {
          cont.add(table).fill()
          table = Table()
        }
      }
      if (table.children.any()) cont.add(table).fill()

      res.setSize(
        res.prefWidth,
        targetHeight
      )
    }
    else {
      val cont = res.table().fillY().growX().get().left()
      var table = Table()
      foldCards.forEach { card ->
        val elem = buildCardShower(card, foldedSide, cardScl, padding)

        if (table.prefWidth + elem.prefWidth < targetWidth) {
          table.top().add(elem).fillX().growY().pad(padding)
        }
        else {
          cont.add(table).fill().row()
          table = Table()
        }
      }
      if (table.children.any()) cont.add(table).fill().row()

      res.setSize(
        targetWidth,
        res.prefHeight
      )
    }

    return res
  }

  private fun buildCardShower(card: Card, side: Side, cardScl: Float, padding: Float,): Element {
    val linker = foldLinkers[card]
    val paneSize = card.paneSize.scl(cardScl)
    val dh = paneSize.y + padding + linker.height

    return object: Element(){
      override fun draw() {
        validate()

        val dx: Float
        val dy: Float
        when(side) {
          Side.LEFT -> { dx = x + width - paneSize.x; dy = y }
          Side.RIGHT -> { dx = x; dy = y + height - dh }
          Side.TOP -> { dx = x; dy = y }
          Side.BOTTOM -> { dx = x; dy = y + height - dh }
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
        return paneSize.x
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
    val pane = card.pane
    val paneSize = card.paneSize.scl(cardScl)

    val origX = pane.x
    val origY = pane.y
    val origLinPar = foldLinker.parent
    val origLinX = foldLinker.x
    val origLinY = foldLinker.y
    val origTrans = Tmp.m1.set(Draw.trans())

    pane.parent = null
    foldLinker.parent = null
    pane.x = 0f
    pane.y = 0f
    foldLinker.x = x + paneSize.x/2 - foldLinker.width/2
    foldLinker.y = y + paneSize.y + padding

    card.singleRend()
    pane.invalidate()
    Draw.trans(Tmp.m2.setToTranslation(x, y).scale(cardScl, cardScl))
    pane.draw()
    Draw.trans(origTrans)
    pane.invalidate()

    foldLinker.invalidate()
    foldLinker.draw()
    foldLinker.invalidate()

    pane.parent = card
    foldLinker.parent = origLinPar
    pane.x = origX
    pane.y = origY
    foldLinker.x = origLinX
    foldLinker.y = origLinY
  }

  fun drawCardsContainer(buff: FrameBuffer, boundX: Float, boundY: Float, scl: Float): FrameBuffer {
    val bound = getBound()

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

    val par = container.parent
    val x = container.x
    val y = container.y
    val px = panX
    val py = panY
    val sclX = zoom.scaleX
    val sclY = zoom.scaleY
    val scW = Core.scene.width
    val scH = Core.scene.height

    zoom.scaleX = 1f
    zoom.scaleY = 1f
    panX = 0f
    panY = 0f
    container.parent = null
    container.x = 0f
    container.y = 0f
    Core.scene.viewport.worldWidth = width
    Core.scene.viewport.worldHeight = height

    cards.forEach { it.singleRend() }
    container.draw()

    val imageWidth = (width*scl).toInt()
    val imageHeight = (height*scl).toInt()

    buff.resize(imageWidth, imageHeight)
    buff.begin(Pal.darkerGray)
    Draw.proj(camera)
    container.draw()
    Draw.flush()
    buff.end()

    container.parent = par
    container.x = x
    container.y = y
    zoom.scaleX = sclX
    zoom.scaleY = sclY
    panX = px
    panY = py
    Core.scene.viewport.worldWidth = scW
    Core.scene.viewport.worldHeight = scH

    container.draw()

    return buff
  }

  fun write(write: Writes) {
    write.i(SchematicDesignerDialog.FI_HEAD)
    write.i(SHD_REV)

    write.f(panX)
    write.f(panY)
    write.f(zoom.scaleX)

    this.writeCards(write)
  }

  fun read(read: Reads) {
    cards.each { actor -> container.removeChild(actor) }
    cards.clear()

    val head = read.i()
    if (head != SchematicDesignerDialog.FI_HEAD) throw IOException(
      "file format error, unknown file head: " + Integer.toHexString(head)
    )

    val ver = read.i()

    if (ver >= 2) {
      panX = read.f()
      panY = read.f()
      zoom.scaleX = read.f()
      zoom.scaleY = zoom.scaleX
    }
    else {
      panX = 0f
      panY = 0f
      zoom.scaleX = 1f
      zoom.scaleY = 1f
    }

    this.readCards(read, ver)

    newSet = null
  }

  fun writeCards(write: Writes) {
    val seq = Seq.withArrays<Card>(cards, foldCards)
    write.i(seq.size)
    for (card in seq) {
      card.write(write)

      write.f(card.x, card.y, card.width, card.height)
      write.f(card.scaleX, card.scaleY)

      write.i(card.linkerIns.size)
      write.i(card.linkerOuts.size)

      for (linker in card.linkerIns) {
        writeLinker(write, linker)
      }

      for (linker in card.linkerOuts) {
        writeLinker(write, linker)

        write.i(linker!!.links.size)

        for (entry in linker.links) {
          write.l(entry.key.id)
          write.f(entry.value.rate)
        }
      }
    }
  }

  fun readCards(
    read: Reads,
    ver: Int = SHD_REV,
  ) {
    val linkerMap = LongMap<ItemLinker>()
    val links = ObjectMap<ItemLinker, Seq<Pair<Long, Float>>>()

    val cardsLen = read.i()
    for (i in 0 until cardsLen) {
      val card = Card.read(read, ver)
      card.build()
      addCard(card, false)

      card.setBounds(read.f(), read.f(), read.f(), read.f())

      card.scaleX = read.f()
      card.scaleY = read.f()

      val inputs = read.i()
      val outputs = read.i()

      for (l in 0 until inputs) {
        val linker = readLinker(read, card, ver)
        linkerMap.put(linker.id, linker)
        card.addIn(linker)
      }

      for (l in 0 until outputs) {
        val linker = readLinker(read, card, ver)
        linkerMap.put(linker.id, linker)
        card.addOut(linker)

        val n = read.i()
        val linkTo = Seq<Pair<Long, Float>>()
        links.put(linker, linkTo)
        for (i1 in 0 until n) {
          linkTo.add(Pair(read.l(), read.f()))
        }
      }
    }

    for (link in links) {
      for (pair in link.value) {
        val target = linkerMap[pair.first] ?: continue
        link.key.linkTo(target)
        link.key.setProportion(target, pair.second)
      }
    }

    // clean
    for (card in cards) {
      card.linkerIns.select { it.links.isEmpty }.forEach { it.remove() }
    }
  }

  private fun writeLinker(write: Writes, linker: ItemLinker) {
    write.l(linker.id)
    write.str(linker.item.name)
    write.bool(linker.isInput)
    write.i(linker.dir)
    write.f(linker.expectAmount)

    write.f(linker.x, linker.y, linker.width, linker.height)
  }

  private fun readLinker(read: Reads, card: Card, ver: Int): ItemLinker {
    val id = read.l()
    val res = ItemLinker(
      card,
      TooManyItems.itemsManager.getByName<Any>(read.str()),
      read.bool(),
      id
    )
    res.dir = read.i()
    res.expectAmount = read.f()
    res.setBounds(read.f(), read.f(), read.f(), read.f())
    return res
  }

  override fun draw() {
    super.draw()
    if (isSelecting) {
      Draw.color(Pal.accent, 0.35f*parentAlpha)
      Fill.rect(
        x + selectBegin.x + (selectEnd.x - selectBegin.x)/2, y + selectBegin.y + (selectEnd.y - selectBegin.y)/2,
        selectEnd.x - selectBegin.x, selectEnd.y - selectBegin.y
      )
    }
  }

  private fun setAreaSelectListener() {
    addListener(object : ElementGestureListener() {
      var enable: Boolean = false
      var panned: Boolean = false
      var beginX: Float = 0f
      var beginY: Float = 0f

      val rect: Rect = Rect()
      val lastSelected: ObjectSet<Card> = ObjectSet()

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        enable = selectMode || button == KeyCode.mouseLeft
        if (enable) {
          var hitted = hitCard(x, y, true) != null
          if (!hitted) eachCard(x, y, false) { card ->
            if (hitted) return@eachCard
            val v = localToDescendantCoordinates(card, vec1.set(x, y))
            hitted = hitted or (card.hitLinker(v.x, v.y) != null)
          }

          if (hitted) {
            enable = false
            return
          }

          moveLock(true)
          lastSelected.clear()
          lastSelected.addAll(selects)

          beginX = x
          beginY = y

          selectBegin.set(selectEnd.set(x, y))
          isSelecting = true
        }
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if ((selectMode || button == KeyCode.mouseLeft)) {
          moveLock(false)

          if (enable && !panned) selects.clear()

          enable = false
          isSelecting = false
          panned = false
        }
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (enable) {
          if (!panned && Mathf.dst(x - beginX, y - beginY) > 14) {
            panned = true
          }

          selectEnd.set(x, y)
          rect.setPosition(selectBegin.x, selectBegin.y)
          rect.setSize(selectEnd.x - selectBegin.x, selectEnd.y - selectBegin.y)

          if (rect.width < 0) {
            rect[rect.x + rect.width, rect.y, -rect.width] = rect.height
          }
          if (rect.height < 0) {
            rect[rect.x, rect.y + rect.height, rect.width] = -rect.height
          }

          if (panned) {
            for (card in selects) {
              if (!lastSelected.contains(card)) selects.remove(card)
            }
            eachCard(rect, true){ key -> selects.add(key) }
          }
        }
      }
    })
  }

  private fun setZoomListener() {
    addListener(object : ElementGestureListener() {
      var panEnable: Boolean = false

      override fun zoom(event: InputEvent, initialDistance: Float, distance: Float) {
        if (lastZoom < 0) {
          lastZoom = zoom.scaleX
        }

        zoom.setScale(Mathf.clamp(distance/initialDistance*lastZoom, 0.25f, 1f))

        clamp()
      }

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (button != KeyCode.mouseMiddle && button != KeyCode.mouseRight || pointer != 0) return
        panEnable = true
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (button != KeyCode.mouseMiddle && button != KeyCode.mouseRight || pointer != 0) return
        lastZoom = zoom.scaleX
        panEnable = false
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (!panEnable || lock) return

        panX += deltaX/zoom.scaleX
        panY += deltaY/zoom.scaleY
        clamp()
      }
    })
  }

  private fun setSelectListener() {
    addListener(object : InputListener() {
      val begin: Vec2 = Vec2()

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
        if (!((pointer == 0 && (button == KeyCode.mouseRight || !SchematicDesignerDialog.useKeyboard())).also {
          enabled = it
        })) return false
        timer = Time.globalTime
        begin[x] = y
        return true
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (pointer != 0 || !((SchematicDesignerDialog.useKeyboard() && button == KeyCode.mouseRight) || (!SchematicDesignerDialog.useKeyboard() && Time.globalTime - timer > 60))) return
        if (enabled) {
          val selecting = hitCard(x, y, true)
          if (selecting != null) {
            selects.clear()
            selects.add(selecting)
          }

          buildMenu(x, y)
        }

        enabled = false
      }

      override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
        if (pointer != 0) return
        if (Mathf.dst(x - begin.x, y - begin.y) > 12) {
          enabled = false
          parentDialog.hideMenu()
        }
      }
    })
  }

  private fun setPanListener() {
    addListener(object : InputListener() {
      override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
        zoom.setScale(Mathf.clamp(zoom.scaleX - amountY/10f*zoom.scaleX, 0.25f, 1f).also { lastZoom = it })
        zoomBar.clearActions()
        zoomBar.actions(
          Actions.alpha(1f),
          Actions.alpha(0f, 3f, Interp.pow5)
        )

        clamp()
        return true
      }

      override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        requestScroll()
        super.enter(event, x, y, pointer, fromActor)
      }
    })
  }

  fun buildMenu(x: Float, y: Float) {
    menuPos.setBounds(x, y, 0f, 0f)

    var linker: ItemLinker? = null
    eachCard(x, y, false) { c ->
      if (linker != null) return@eachCard
      vec1.set(x, y)
      localToDescendantCoordinates(c, vec1)
      linker = c.hitLinker(vec1.x, vec1.y)
    }
    val selecting = linker?:hitCard(x, y, true)

    parentDialog.showMenu(menuPos, Align.topLeft, Align.topLeft, true) { menu ->
      menu.table(Consts.padDarkGrayUIAlpha) { m ->
        m.defaults().growX().fillY().minWidth(300f)

        val map = linkedMapOf<String, Seq<ViewTab>>()
        parentDialog.viewMenuTabs.select {
          it.filter.run { parentDialog.accept(x, y, this@DesignerView, selecting) }
        }.forEach { map.computeIfAbsent(it.group){ Seq<ViewTab>() }.add(it) }

        var first = true
        map.values.forEach { group ->
          if (!first) m.image().height(2f).pad(4f).padLeft(0f).padRight(0f).growX().color(Color.lightGray).row()
          first = false

          group.forEach { tab ->
            m.button(tab.title, tab.icon, Styles.cleart, 14f) {
              tab.clicked.run { parentDialog.accept(x, y, this@DesignerView, selecting) }
            }.margin(8f).get().apply {
              labelCell.padLeft(6f).get().setAlignment(Align.left)
              if (!tab.valid.run { parentDialog.accept(x, y, this@DesignerView, selecting) }) {
                isDisabled = true
                fill { x, y, w, h ->
                  Consts.grayUIAlpha.draw(x, y, w, h)
                }
              }
            }
            m.row()
          }
        }
      }
    }
  }

  fun resetView() {
    panX = 0f
    panY = 0f
    zoom.scaleX = 1f
    zoom.scaleY = 1f
  }

  private fun clamp() {
    val par = parent ?: return

  }

  private fun checkNorm(inner: Boolean, card: Card): Pair<Vec2, Vec2> {
    val panePos = card.panePos
    val paneSize = card.paneSize

    if (inner) vec2.set(panePos.x, panePos.y).add(card.x, card.y)
    else vec2.set(card.x, card.y)

    if (inner) vec3.set(panePos.x + paneSize.x, panePos.y + paneSize.y).add(card.x, card.y)
    else vec3.set(card.x + card.width, card.y + card.height)

    card.parent.localToAscendantCoordinates(this, vec2)
    card.parent.localToAscendantCoordinates(this, vec3)

    return vec2 to vec3
  }

  private fun getBound(): Rect {
    val v1 = Vec2(Float.MAX_VALUE, Float.MAX_VALUE)
    val v2 = v1.cpy().scl(-1f)
    for (card in cards) {
      v1.x = min(v1.x.toDouble(), card!!.x.toDouble()).toFloat()
      v1.y = min(v1.y.toDouble(), card.y.toDouble()).toFloat()

      v2.x = max(v2.x.toDouble(), (card.x + card.width).toDouble()).toFloat()
      v2.y = max(v2.y.toDouble(), (card.y + card.height).toDouble()).toFloat()
    }
    return Rect(v1.x, v1.y, v2.x - v1.x, v2.y - v1.y)
  }
}

data class ViewTab(
  val title: String,
  val icon: Drawable,
  val clicked: ViewAcceptor<Unit>,
  val filter: ViewAcceptor<Boolean> = ViewAcceptor { _, _, _, _ -> true },
  val valid: ViewAcceptor<Boolean> = ViewAcceptor { _, _, _, _ -> true },
  val group: String = "normal",
)

fun interface ViewAcceptor<R>{
  fun SchematicDesignerDialog.accept(
    x: Float, y: Float,
    view: DesignerView,
    hitTarget: Any?,
  ): R

  operator fun invoke(
    dialog: SchematicDesignerDialog,
    x: Float, y: Float,
    view: DesignerView,
    hitTarget: Any?,
  ): R = dialog.accept(x, y, view, hitTarget)
}