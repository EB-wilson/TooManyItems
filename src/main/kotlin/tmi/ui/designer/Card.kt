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
import arc.scene.style.Drawable
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.IntMap
import arc.struct.ObjectSet
import arc.struct.Seq
import arc.util.Align
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.graphics.Pal
import tmi.TooManyItems
import tmi.f
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.set
import tmi.ui.TmiUI
import tmi.util.Consts
import tmi.util.vec1

abstract class Card(val ownerDesigner: DesignerView) : Table() {
  companion object {
    private val tmp = Vec2()

    @JvmStatic
    protected val provs: IntMap<Func2<Reads, Int, Card>> = IntMap()

    init {
      provs[IOCard.CLASS_ID] = Func2<Reads, Int, Card>{ r, rev ->
        if (rev <= 2) r.str()

        val res = IOCard(
          TmiUI.schematicDesigner.currPage!!.view,
          r.bool()
        )

        if (rev <= 2) r.f()
        res
      }

      provs[RecipeCard.CLASS_ID] = Func2<Reads, Int, Card> { r, _ ->
        RecipeCard(
          TmiUI.schematicDesigner.currPage!!.view,
          TooManyItems.recipesManager.getByID(r.i())
        )
      }
    }

    fun read(read: Reads, rev: Int): Card {
      val id = read.i()
      val card = provs[id][read, rev]
      if (rev >= 3) card.isFold = read.bool()
      card.read(read, rev)
      return card
    }
  }

  val pane: Table = object : Table(Consts.darkGrayUIAlpha) {
    override fun drawBackground(x: Float, y: Float) {
      if (ownerDesigner.newSet === this@Card) {
        Lines.stroke(Scl.scl(5f))
        Draw.color(Pal.accentBack, parentAlpha)
        Lines.rect(x - Scl.scl(45f), y - Scl.scl(45f), getWidth() + 2*Scl.scl(40f), getHeight() + 2*Scl.scl(40f))
        Draw.color(Pal.accent, parentAlpha)
        Lines.rect(x - Scl.scl(40f), y - Scl.scl(40f), getWidth() + 2*Scl.scl(40f), getHeight() + 2*Scl.scl(40f))
        Draw.color()
      }
      super.drawBackground(x, y)
    }

    override fun draw() {
      if (!inStage) return
      super.draw()
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

  val linkerOuts = ObjectSet<ItemLinker>()
  val linkerIns = ObjectSet<ItemLinker>()

  protected var observeUpdated = false
  protected var allUpdate = false
  protected var inStage = false

  abstract val balanceValid: Boolean

  var removing = false
  var aligning = false
  var alignPos = Vec2()
  var isFold = false
    set(value){
      field = value

      linkerIns.forEach { it.updateLinks() }
      linkerOuts.forEach { it.updateLinks() }
    }

  val panePos: Vec2
    get() = Vec2(pane.x, pane.y)
  val paneSize: Vec2
    get() = Vec2(pane.width, pane.height)

  var foldIcon: Drawable? = null
  val foldColor: Color = Color.white.cpy()
  val iconColor: Color = Color.white.cpy()
  val backColor: Color = Pal.darkestGray.cpy()

  var isSizeAlign = false
    private set

  fun build() {
    add(pane).center().fill().pad(100f)
    buildCard()
  }

  fun singleRend(){
    inStage = true
  }

  fun rise() {
    if(isFold){
      val pos = localToAscendantCoordinates(ownerDesigner, vec1.set(pane.x, pane.y))
      ownerDesigner.removeCard(this, false)
      ownerDesigner.addChild(this)
      setPosition(pos.x - pane.x * scaleX, pos.y - pane.y * scaleY)
    }
    else {
      ownerDesigner.removeCard(this, false)
      ownerDesigner.cards.add(this)
      ownerDesigner.container.addChild(this)
    }
  }

  open fun addIn(linker: ItemLinker) {
    if (!linkerIns.add(linker)) return
    addChild(linker)

    observeUpdate()
  }

  open fun addOut(linker: ItemLinker) {
    if (!linkerOuts.add(linker)) return
    addChild(linker)

    observeUpdate()
  }

  fun hitLinker(x: Float, y: Float): ItemLinker? {
    for (linker in SchematicDesignerDialog.seq.clear().addAll(linkerIns).addAll(linkerOuts)) {
      if (x > linker.x
        && x < linker.x + linker.width
        && y > linker.y
        && y < linker.y + linker.height) {
        return linker
      }
    }
    return null
  }

  override fun layout() {
    super.layout()
    linkerIns.forEach { it.adsorption(it.getX(Align.center), it.getY(Align.center), this) }
    linkerOuts.forEach { it.adsorption(it.getX(Align.center), it.getY(Align.center), this) }
  }

  override fun act(delta: Float) {
    super.act(delta)

    linkerIns.forEach { it.visible = !isFold }
    linkerOuts.forEach { it.visible = !isFold }

    inStage = ownerDesigner.checkCardClip(this)

    if (observeUpdated) {
      observeUpdated = false

      calculateBalance()
      onObserveUpdated()

      allUpdate = false
    }
  }

  override fun removeChild(element: Element, unfocus: Boolean): Boolean {
    if (element is ItemLinker) {
      linkerIns.remove(element)
      linkerOuts.remove(element)

      observeUpdate()
    }
    return super.removeChild(element, unfocus)
  }

  override fun draw() {
    if (aligning) {
      Draw.reset()
      Consts.darkGrayUIAlpha.draw(x + alignPos.x, y + alignPos.y, pane.width, pane.height)
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

  fun observeUpdate(allUpdate: Boolean = false){
    observeUpdated = true
    this.allUpdate = this.allUpdate or allUpdate
  }

  abstract fun accepts(): List<RecipeItemStack>

  abstract fun outputs(): List<RecipeItemStack>

  abstract fun added()

  abstract fun calculateBalance()

  protected abstract fun buildCard()

  open fun onObserveUpdated(){
    linkerIns.forEach{l ->
      l.links.forEach {
        (it.key.parent as? Card)?.observeUpdate()
      }
    }

    if (allUpdate) {
      linkerOuts.forEach {l ->
        l.links.forEach {
          (it.key.parent as? Card)?.observeUpdate()
        }
      }
    }
  }

  protected fun moveListener(element: Element): EventListener {
    return object : ElementGestureListener() {
      var enabled: Boolean = false
      var moveHandle: MoveCardHandle? = null

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (pointer != 0 || button != KeyCode.mouseLeft || ownerDesigner.isSelecting) return
        enabled = true
        ownerDesigner.moveLock(true)
        this@Card.removing = false
        ownerDesigner.selects.each { e -> e!!.removing = false }
        rise()
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (pointer != 0 || button != KeyCode.mouseLeft || ownerDesigner.isSelecting) return
        val align = ownerDesigner.cardAlign

        enabled = false
        ownerDesigner.moveLock(false)

        tmp.set(element.x, element.y)
        element.parent.localToAscendantCoordinates(ownerDesigner, tmp)

        if (isFold) {
          if (tmp.y < ownerDesigner.foldHeight){
            val n = ownerDesigner.localToDescendantCoordinates(ownerDesigner.foldPane, vec1.set(this@Card.x, this@Card.y))
            ownerDesigner.addCard(this@Card)
            this@Card.setPosition(n.x, n.y)
            if (moveHandle != null) {
              moveHandle?.post()
              ownerDesigner.pushHandle(moveHandle!!)
            }
            else ownerDesigner.alignFoldCard(this@Card)
          }
          else {
            val v = ownerDesigner.localToDescendantCoordinates(
              ownerDesigner.container,
              localToAscendantCoordinates(ownerDesigner, vec1.set(pane.getX(Align.center), pane.getY(Align.center)))
            )
            setPosition(v.x, v.y, Align.center)
            ownerDesigner.pushHandle(FoldCardHandle(
              ownerDesigner,
              this@Card,
              Vec2(v.x - width/2, v.y - height/2),
              false
            ))
          }
        }
        else if (ownerDesigner.removeMode && tmp.y < ownerDesigner.height*0.15f) {
          if (moveHandle != null) {
            ownerDesigner.pushHandle(CombinedHandles(
              ownerDesigner,
              moveHandle!!,
              RemoveCardHandle(
                ownerDesigner,
                ownerDesigner.selects.run { if (contains(this@Card)) toList() else listOf(this@Card) }
              )
            ))
          }
          else ownerDesigner.pushHandle(RemoveCardHandle(
            ownerDesigner,
            ownerDesigner.selects.run { if (contains(this@Card)) toList() else listOf(this@Card) }
          ))
        }
        else if (moveHandle != null) {
          if (align != -1) {
            if (ownerDesigner.selects.contains(this@Card)) {
              ownerDesigner.selects.each {
                it.aligning = false
                it.gridAlign(align)
              }
            }
            else {
              aligning = false
              gridAlign(align)
            }
          }

          moveHandle?.post()
          ownerDesigner.pushHandle(moveHandle!!)
        }

        moveHandle = null
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (!enabled) return

        if (moveHandle == null) ownerDesigner.selects.run { if (contains(this@Card)) toList() else listOf(this@Card) }
          .also { cards ->
            moveHandle = MoveCardHandle(
              ownerDesigner,
              cards,
            )
          }
        moveHandle!!.moveX += deltaX*scaleX
        moveHandle!!.moveY += deltaY*scaleY

        moveHandle!!.sync()

        if (isFold) return

        val align = ownerDesigner.cardAlign
        if (ownerDesigner.selects.contains(this@Card)) {
          ownerDesigner.selects.each { e ->
            e.aligning = align != -1
            if (e.aligning) {
              e.alignPos(align, e.alignPos)
              e.parentToLocalCoordinates(e.alignPos.add(e.pane.x, e.pane.y))
            }
          }
        }
        else {
          aligning = align != -1
          if (aligning) {
            alignPos(align, alignPos)
            parentToLocalCoordinates(alignPos.add(pane.x, pane.y))
          }
        }

        tmp.set(element.x, element.y)
        element.parent.localToStageCoordinates(tmp)

        val removing = ownerDesigner.removeMode && tmp.y < Core.scene.height*0.15f
        if (ownerDesigner.selects.contains(this@Card)) ownerDesigner.selects.each { it.removing = removing }
        else this@Card.removing = removing
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
      pane.pack()
    }
    pack()

    gridAlign(ownerDesigner.cardAlign)

    for (linker in linkerIns.toSeq().addAll(linkerOuts)) {
      linker!!.adsorption(linker.x + linker.width/2, linker.y + linker.height/2, this)
    }
  }

  fun gridAlign(align: Int) {
    if (align == -1) return

    val out = alignPos(align, tmp)
    setPosition(out.x, out.y)
  }

  fun alignPos(align: Int, out: Vec2): Vec2 {
    val gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", 150).toFloat())
    var ex = this@Card.x + pane.getX(align)
    var ey = this@Card.y + pane.getY(align)

    ex = Mathf.round(ex/gridSize)*gridSize
    ey = Mathf.round(ey/gridSize)*gridSize

    if ((align and Align.left) != 0) ex -= pane.x
    else if ((align and Align.right) != 0) ex += getWidth() - pane.x - pane.width

    if ((align and Align.bottom) != 0) ey -= pane.y
    else if ((align and Align.top) != 0) ey += getHeight() - pane.y - pane.height

    if ((align and Align.right) != 0) ex -= width
    else if ((align and Align.left) == 0) ex -= width/2

    if ((align and Align.top) != 0) ey -= height
    else if ((align and Align.bottom) == 0) ey -= height/2

    return out.set(ex, ey)
  }

  abstract fun checkLinking(linker: ItemLinker): Boolean
  abstract fun copy(): Card

  open fun write(write: Writes){
    write.i(foldIcon?.let { Consts.foldCardIcons.indexOf(foldIcon) }?:-1)
    write.f(foldColor.toFloatBits())
    write.f(iconColor.toFloatBits())
    write.f(backColor.toFloatBits())
  }
  open fun read(read: Reads, ver: Int){
    if (ver >= 5){
      foldIcon = read.i().takeIf { it > -1 }?.let { Consts.foldCardIcons[it] }
      foldColor.abgr8888(read.f())
      iconColor.abgr8888(read.f())
      backColor.abgr8888(read.f())
    }
  }

  fun <T: Element> setNodeMoveLinkerListener(node: T, item: RecipeItem<*>, ownerView: DesignerView, beginMove: Cons<T>? = null) {
    node.addListener(object : DragListener() {
      var currLinker: ItemLinker? = null
      var lastMoveHandle: MoveLinkerHandle? = null

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
        return touchDown(event, x, y, pointer, button.ordinal)
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        super.touchUp(event, x, y, pointer, button.ordinal)
      }

      override fun dragStart(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        val other = this@Card.linkerOuts.find { l -> l.item == item }
        if (other == null) {
          currLinker = ItemLinker(this@Card, item, false)
          currLinker!!.pack()
          ownerView.pushHandle(
            AddOutputLinkerHandle(
              ownerView,
              currLinker!!,
              this@Card
            )
          )
        }
        else {
          currLinker = other
          ownerView.pushHandle(MoveLinkerHandle(
            ownerView,
            currLinker!!
          ).also { lastMoveHandle = it })
        }

        beginMove?.get(node)
      }

      override fun drag(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        vec1.set(x, y)
        node.localToAscendantCoordinates(this@Card, vec1)
        currLinker!!.adsorption(vec1.x, vec1.y, this@Card)

        lastMoveHandle?.apply {
          endX = currLinker!!.x
          endY = currLinker!!.y
          endDir = currLinker!!.dir
        }
      }

      override fun dragStop(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        currLinker = null
        lastMoveHandle = null
      }
    }.also { l -> l.button = KeyCode.mouseLeft.ordinal })
  }
}
