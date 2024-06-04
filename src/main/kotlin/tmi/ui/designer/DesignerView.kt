package tmi.ui.designer

import arc.Core
import arc.func.Cons
import arc.graphics.*
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.Lines
import arc.graphics.g2d.TextureRegion
import arc.graphics.gl.FrameBuffer
import arc.input.KeyCode
import arc.math.Interp
import arc.math.Mathf
import arc.math.geom.Rect
import arc.math.geom.Vec2
import arc.scene.Element
import arc.scene.Group
import arc.scene.actions.Actions
import arc.scene.event.*
import arc.scene.style.Drawable
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.LongMap
import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import arc.util.Align
import arc.util.Buffers
import arc.util.Time
import arc.util.Tmp
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.input.Binding
import mindustry.type.Item
import mindustry.ui.Styles
import mindustry.world.Block
import tmi.TooManyItems
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItem
import tmi.util.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class DesignerView(val parentDialog: SchematicDesignerDialog) : Group() {
  companion object{
    val seq: Seq<ItemLinker> = Seq()
  }

  var maximumHistories = 300
  var currAlignIcon: Drawable = Icon.none
  var removeMode = false
  var cardAlign = -1
  var selectMode = false
  var editLock = false
  var isSelecting = false
  var newSet: Card? = null
  var selecting: ItemLinker? = null

  val cards = Seq<Card>()
  val selects = ObjectSet<Card>()

  private val history = Seq<DesignerHandle>()
  private var historyIndex = 0

  private val selectBegin: Vec2 = Vec2()
  private val selectEnd: Vec2 = Vec2()

  private var enabled: Boolean = false
  private var shown: Boolean = false
  private var timer: Float = 0f

  private var lock: Boolean = false

  private var lastZoom: Float = -1f
  private var panX: Float = 0f
  private var panY: Float = 0f

  private var _container: Group? = null
  private var zoomBar: Table? = null

  val container: Group
    get() = _container!!

  val zoom: Group = object : Group() {
    init {
      setFillParent(true)
      isTransform = true
    }

    override fun draw() {
      validate()
      super.draw()
    }
  }

  fun build() {
    _container = object : Group() {
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

    update {
      if (Core.input.axis(Binding.move_x) > 0) {
        panX -= 10*Time.delta/zoom.scaleX/Scl.scl()
        clamp()
      }
      else if (Core.input.axis(Binding.move_x) < 0) {
        panX += 10*Time.delta/zoom.scaleX/Scl.scl()
        clamp()
      }
      if (Core.input.axis(Binding.move_y) > 0) {
        panY -= 10*Time.delta/zoom.scaleY/Scl.scl()
        clamp()
      }
      else if (Core.input.axis(Binding.move_y) < 0) {
        panY += 10*Time.delta/zoom.scaleY/Scl.scl()
        clamp()
      }

      if (TooManyItems.binds.undo.isTap(Core.input)) undoHistory()
      if (TooManyItems.binds.redo.isTap(Core.input)) redoHistory()
    }

    setTapListener()
    setPanListener()
    setSelectListener()
    setZoomListener()
    setAreaSelectListener()
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

  fun addIO(item: RecipeItem<*>, isInput: Boolean): IOCard {
    return IOCard(this, item, isInput).also {
      pushHandle(AddCardHandle(this, it))
      buildCard(it)
      newSet = it
    }
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
    val v1 = container.localToStageCoordinates(vec2.set(card.x, card.y))
    val v2 = container.localToStageCoordinates(vec3.set(card.x, card.y).add(card.width, card.height))

    return v1.x < Core.scene.width && v2.x > Core.scene.viewport.screenX
        && v1.y < Core.scene.height && v2.y > Core.scene.viewport.screenY
  }

  fun addCard(card: Card) {
    cards.add(card)
    container.addChild(card)
  }

  fun buildCard(card: Card, x: Float = Core.scene.width/2, y: Float = Core.scene.height/2) {
    container.stageToLocalCoordinates(vec1.set(x, y))

    card.build()
    card.pack()
    card.buildLinker()
    card.draw()
    card.setPosition(vec1.x, vec1.y, Align.center)

    card.gridAlign(cardAlign)
  }

  fun removeCard(card: Card) {
    cards.remove(card)
    container.removeChild(card)

    seq.clear().addAll(card.linkerIns).addAll(card.linkerOuts)
    for (linker in seq) {
      for (link in linker!!.links.keys().toSeq()) {
        linker.deLink(link)
        if (link!!.links.isEmpty && link.isInput) link.remove()
      }
    }
  }

  fun eachCard(range: Rect, cons: Cons<Card>, inner: Boolean) {
    for (card in cards) {
      val (v1, v2) = checkNorm(inner, card)
      val ox = v1.x
      val oy = v1.y
      val wx = v2.x
      val wy = v2.y

      rect1.set(ox, oy, wx - ox, wy - oy)
      if (range.contains(rect1) || (!inner && range.overlaps(rect1))) cons[card]
    }
  }

  fun eachCard(stageX: Float, stageY: Float, inner: Boolean, cons: Cons<Card>) {
    Tmp.r1[stageX, stageY, 0f] = 0f
    eachCard(Tmp.r1, cons, inner)
  }

  fun hitCard(stageX: Float, stageY: Float, inner: Boolean): Card? {
    for (s in cards.size - 1 downTo 0) {
      val card = cards[s]

      val (v1, v2) = checkNorm(inner, card)

      val ox = v1.x
      val oy = v1.y
      val wx = v2.x
      val wy = v2.y

      if (ox < stageX && stageX < wx && oy < stageY && stageY < wy) {
        return card
      }
    }

    return null
  }

  fun standardization() {
    val (v1, v2) = normBound()

    v2.add(v1).scl(0.5f)

    val offX = -v2.x - container.width/2
    val offY = -v2.y - container.height/2

    pushHandle(StandardizeHandle(this, offX, offY))

    panX = 0f
    panY = 0f
  }

  fun pushHandle(handle: DesignerHandle){
    if (history.size <= historyIndex) {
      history.add(handle)
      if (history.size > maximumHistories) history.remove(0)
      else historyIndex++
    }
    else {
      history[historyIndex] = handle
      if (historyIndex < history.size - 1) history.removeRange(historyIndex + 1, history.size - 1)
      historyIndex++
    }
    handle.handle()
  }

  fun redoHistory() {
    if (historyIndex < history.size) {
      history[historyIndex].handle()
      historyIndex++
    }
  }

  fun undoHistory() {
    if (historyIndex > 0) {
      historyIndex--
      history[historyIndex].quash()
    }
  }

  fun toImage(boundX: Float, boundY: Float, scl: Float): TextureRegion {
    val buffer = toBuffer(FrameBuffer(), boundX, boundY, scl)
    buffer.bind()
    Gl.pixelStorei(Gl.packAlignment, 1)
    val numBytes = buffer.width*buffer.height*4
    val pixels = Buffers.newByteBuffer(numBytes)
    Gl.readPixels(0, 0, buffer.width, buffer.height, Gl.rgba, Gl.unsignedByte, pixels)

    val lines = ByteArray(numBytes)

    val numBytesPerLine = buffer.width*4
    for (i in 0 until buffer.height) {
      pixels.position((buffer.height - i - 1)*numBytesPerLine)
      pixels[lines, i*numBytesPerLine, numBytesPerLine]
    }

    val fullPixmap = Pixmap(buffer.width, buffer.height)
    Buffers.copy(lines, 0, fullPixmap.pixels, lines.size)

    return TextureRegion(Texture(fullPixmap))
  }

  fun toBuffer(buff: FrameBuffer, boundX: Float, boundY: Float, scl: Float): FrameBuffer {
    val (v1, v2) = normBound()

    val width = v2.x - v1.x + boundX*2
    val height = v2.y - v1.y + boundY*2

    val dx = v1.x - boundX
    val dy = v1.y - boundY

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

    container.draw()

    val imageWidth = (width*scl).toInt()
    val imageHeight = (height*scl).toInt()

    buff.resize(imageWidth, imageHeight)
    buff.begin(Color.clear)
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

  fun read(read: Reads) {
    cards.each { actor: Card? -> container.removeChild(actor) }
    cards.clear()
    panX = 0f
    panY = 0f
    zoom.scaleX = 1f
    zoom.scaleY = 1f

    val linkerMap = LongMap<ItemLinker>()

    val links = ObjectMap<ItemLinker, Seq<Pair<Long, Float>>>()

    val head = read.i()
    if (head != SchematicDesignerDialog.FI_HEAD) throw IOException(
      "file format error, unknown file head: " + Integer.toHexString(head)
    )

    val ver = read.i()

    val cardsLen = read.i()
    for (i in 0 until cardsLen) {
      val card: Card = Card.read(read, ver)
      addCard(card)
      card.build()
      card.mul = read.i()
      if (ver >= 1) card.effScale = read.f()
      card.setBounds(read.f(), read.f(), read.f(), read.f())

      val inputs = read.i()
      val outputs = read.i()

      for (l in 0 until inputs) {
        val linker = readLinker(read, ver)
        linkerMap.put(linker.id, linker)
        card.addIn(linker)
      }

      for (l in 0 until outputs) {
        val linker = readLinker(read, ver)
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
        val target = linkerMap[pair.first]
        link.key.linkTo(target)
        link.key.setPresent(target, pair.second)
      }
    }

    newSet = null
  }

  fun write(write: Writes) {
    write.i(SchematicDesignerDialog.FI_HEAD)
    write.i(1)

    write.i(cards.size)
    for (card in cards) {
      card!!.write(write)
      write.i(card.mul)
      write.f(card.effScale)
      write.f(card.x)
      write.f(card.y)
      write.f(card.width)
      write.f(card.height)

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
          write.f(entry.value)
        }
      }
    }
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

  private fun buildItems(items: Table, list: Seq<RecipeItem<*>>, callBack: Cons<RecipeItem<*>>) {
    var i = 0
    var reverse = false
    var search = ""
    var rebuild = {}

    items.table { top ->
      top.image(Icon.zoom).size(32f)
      top.field("") { str ->
        search = str
        rebuild()
      }.growX()

      top.button(Icon.none, Styles.clearNonei, 36f) {
        i = (i + 1)%TooManyItems.recipesDialog.sortings.size
        rebuild()
      }.margin(2f).update { b -> b.style.imageUp = TooManyItems.recipesDialog.sortings[i].icon }
      top.button(Icon.none, Styles.clearNonei, 36f) {
        reverse = !reverse
        rebuild()
      }.margin(2f).update { b -> b.style.imageUp = if (reverse) Icon.up else Icon.down }
    }.growX()
    items.row()

    items.pane(Styles.smallPane) { cont ->
      rebuild = {
        cont.clearChildren()
        var ind = 0

        val sorting = TooManyItems.recipesDialog.sortings[i].sort
        val ls: Seq<RecipeItem<*>> = list.copy()
          .removeAll { e: RecipeItem<*> -> !e.name().contains(search) && !e.localizedName().contains(search) }
          .sort(if (reverse) java.util.Comparator { a: RecipeItem<*>?, b: RecipeItem<*>? -> sorting.compare(b, a) } else sorting)

        ls.forEach { item ->
          if (item.locked() || (item.item is Item && Vars.state.rules.hiddenBuildItems.contains(item.item)) || item.hidden()) return@forEach

          cont.button(TextureRegionDrawable(item.icon()), Styles.clearNonei, 32f) {
            callBack[item]
          }.margin(4f).tooltip(item.localizedName()).get()

          if (ind++%8 == 7) {
            cont.row()
          }
        }
      }
      rebuild()
    }.padTop(6f).padBottom(4f).height(400f).fillX()
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
            val v = card!!.stageToLocalCoordinates(vec1.set(x, y))
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

          selectEnd[x] = y
          vec1.set(selectBegin)
          localToStageCoordinates(vec1)
          rect.setPosition(vec1.x, vec1.y)
          vec1.set(selectEnd)
          localToStageCoordinates(vec1)
          rect.setSize(vec1.x - rect.x, vec1.y - rect.y)

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
            eachCard(rect, { key -> selects.add(key) }, true)
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
        zoom.setOrigin(Align.center)
        zoom.isTransform = true

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
        if (shown) {
          parentDialog.hideMenu()
          shown = false
        }
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
            selects.add(selecting)
          }

          buildMenu(x, y)
        }

        enabled = false
      }

      override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
        if (pointer != 0) return
        if (Mathf.dst(x - begin.x, y - begin.y) > 12) enabled = false
      }
    })
  }

  private fun setPanListener() {
    addListener(object : InputListener() {
      override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
        zoom.setScale(Mathf.clamp(zoom.scaleX - amountY/10f*zoom.scaleX, 0.25f, 1f).also { lastZoom = it })
        zoom.setOrigin(Align.center)
        zoom.isTransform = true
        zoomBar!!.clearActions()
        zoomBar!!.actions(
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

  private fun setTapListener() {
    addListener(object : ClickListener(KeyCode.mouseLeft) {
      var other: ItemLinker? = null
      var dragged: Boolean = false

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
        dragged = false
        return super.touchDown(event, x, y, pointer, button)
      }

      override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
        dragged = true
      }

      override fun clicked(event: InputEvent, x: Float, y: Float) {
        other = null

        if (selecting != null) {
          eachCard(x, y, false) { c: Card? ->
            if (other != null) return@eachCard
            val v = c!!.stageToLocalCoordinates(vec1.set(x, y))
            other = c.hitLinker(v.x, v.y)
            if (other == selecting) {
            }
            else {
              //TODO: 连接路径数据配置
            }
          }
        }

        shown = false
        isSelecting = false
        moveLock(false)
        parentDialog.hideMenu()
      }

      override fun isOver(element: Element, x: Float, y: Float): Boolean {
        return !dragged
      }
    })
  }

  private fun buildMenu(x: Float, y: Float) {
    shown = true
    val selecting = hitCard(x, y, true)
    parentDialog.showMenu(this, Align.bottomLeft, Align.bottomLeft, false) { tab ->
      tab.table(Consts.darkGrayUIAlpha) { menu ->
        menu.defaults().growX().fillY().minWidth(300f)
        if (!selects.isEmpty) {
          menu.button(Core.bundle["misc.remove"], Icon.trash, Styles.cleart, 22f) {
            parentDialog.hideMenu()
            pushHandle(RemoveCardHandle(this, selects.toList()))
            selects.clear()
          }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
          menu.row()

          menu.button(Core.bundle["misc.copy"], Icon.copy, Styles.cleart, 22f) {
            parentDialog.hideMenu()
            parentDialog.setClipboard(*selects.map { it.copy() }.toTypedArray())
          }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
          menu.row()

          menu.button(Core.bundle["misc.paste"], Icon.paste, Styles.cleart, 22f) {
            parentDialog.hideMenu()
            parentDialog.getClipboard().forEach {
              vec1.set(it.x + it.width/2 + 40, it.y + it.height/2 - 40)
              container.localToStageCoordinates(vec1)
              addCard(it)
              buildCard(it, vec1.x, vec1.y)
            }
          }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
          menu.row()

          var any = false
          for (card in selects) {
            if (card!!.isSizeAlign) {
              any = true
              break
            }
          }
          val fa = any
          menu.button(
            Core.bundle[if (any) "dialog.calculator.unAlignSize" else "dialog.calculator.sizeAlign"],
            if (any) Icon.diagonal else Icon.resize,
            Styles.cleart, 22f
          ) {
            parentDialog.hideMenu()
            for (card in selects) {
              card!!.adjustSize(!fa)
            }
          }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
          menu.row()

          menu.image().height(4f).pad(4f).padLeft(0f).padRight(0f).growX().color(Color.lightGray)
          menu.row()
        }

        if (selecting == null) {
          val hit = AtomicBoolean(false)
          eachCard(x, y, false) { c: Card? ->
            if (hit.get()) return@eachCard
            Tmp.v1[x] = y
            c!!.stageToLocalCoordinates(Tmp.v1)
            val linker = c.hitLinker(Tmp.v1.x, Tmp.v1.y) ?: return@eachCard

            if (linker.isInput) {
              menu.button(Core.bundle["dialog.calculator.removeLinker"], Icon.trash, Styles.cleart, 22f) {
                for (link in linker.links.orderedKeys()) {
                  link!!.deLink(linker)
                }
                linker.remove()
                parentDialog.hideMenu()
              }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
              menu.row()
              menu.button(Core.bundle["dialog.calculator.addInputAs"], Icon.download, Styles.cleart, 22f) {
                IOCard(this, linker.item, true).also {
                  pushHandle(AddCardHandle(this, it))
                  buildCard(it, x, y)
                  newSet = it
                }
                parentDialog.hideMenu()
              }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
              menu.row()
            }
            else {
              menu.button(Core.bundle["dialog.calculator.addOutputAs"], Icon.upload, Styles.cleart, 22f) {
                IOCard(this, linker.item, false).also {
                  pushHandle(AddCardHandle(this, it))
                  buildCard(it, x, y)
                  newSet = it
                }
                parentDialog.hideMenu()
              }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
              menu.row()
            }

            menu.image().height(4f).pad(4f).padLeft(0f).padRight(0f).growX().color(Color.lightGray)
            menu.row()
          }
        }

        menu.button(Core.bundle["dialog.calculator.addRecipe"], Icon.book, Styles.cleart, 22f) {
          TooManyItems.recipesDialog.toggle = Cons { r ->
            TooManyItems.recipesDialog.hide()
            addRecipe(r)

            vec1[x] = y
            container.stageToLocalCoordinates(vec1)
            newSet!!.setPosition(vec1.x, vec1.y, Align.center)
            newSet!!.gridAlign(cardAlign)
          }
          TooManyItems.recipesDialog.show()
          parentDialog.hideMenu()
        }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
        menu.row()

        menu.button(Core.bundle["dialog.calculator.addInput"], Icon.download, Styles.cleart, 22f) {
          //addCard(new IOCard(linker.item, true), x, y, true);
          parentDialog.showMenu(this, Align.bottomLeft, Align.bottomLeft, false) { list ->
            list.table(Consts.darkGrayUIAlpha) { items ->
              val l = TooManyItems.itemsManager.list.removeAll { e -> !TooManyItems.recipesManager.anyMaterial(e) || e.item is Block }
              buildItems(items, l) { item ->
                IOCard(this, item, true).also {
                  pushHandle(AddCardHandle(this, it))
                  buildCard(it, x, y)
                  newSet = it
                }
                parentDialog.hideMenu()
              }
            }.update { t -> align(x, y, list, t) }.margin(8f)
          }
        }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
        menu.row()
        menu.button(Core.bundle["dialog.calculator.addOutput"], Icon.upload, Styles.cleart, 22f) {
          //addCard(new IOCard(linker.item, false), x, y, true);
          parentDialog.showMenu(this, Align.bottomLeft, Align.bottomLeft, false) { list ->
            list.table(Consts.darkGrayUIAlpha) { items ->
              val l = TooManyItems.itemsManager.list.removeAll { e -> !TooManyItems.recipesManager.anyProduction(e) || e.item is Block }
              buildItems(items, l) { item ->
                IOCard(this, item, false).also {
                  pushHandle(AddCardHandle(this, it))
                  buildCard(it, x, y)
                  newSet = it
                }
                parentDialog.hideMenu()
              }
            }.update { t -> align(x, y, list, t) }.margin(8f)
          }
        }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
        menu.row()
      }.update { t -> align(x, y, tab, t) }
      tab.setSize(width, height)
    }
  }

  private fun clamp() {
    val par = parent ?: return

  }

  private fun checkNorm(c: IOCard, out: ItemLinker): Boolean {
    var sum = 0f
    for (linker in out.links.keys()) {
      if (!linker!!.isNormalized) {
        Vars.ui.showInfo(Core.bundle.format("misc.assignInvalid"))
        return true
      }

      val rate = if (linker.links.size == 1) 1f else linker.links[out]?:-1f
      sum += linker.expectAmount*rate
    }
    c.stack.amount = sum
    return false
  }

  private fun checkNorm(inner: Boolean, card: Card): Pair<Vec2, Vec2> {
    if (inner) vec2.set(card.child.x, card.child.y).add(card.x, card.y)
    else vec2[card.x] = card.y
    card.parent.localToStageCoordinates(vec2)

    if (inner) vec3.set(card.child.x + card.child.width, card.child.y + card.child.height).add(
      card.x, card.y
    )
    else vec3[card.x + card.width] = card.y + card.height
    card.parent.localToStageCoordinates(vec3)

    return vec2 to vec3
  }

  private fun writeLinker(write: Writes, linker: ItemLinker?) {
    write.l(linker!!.id)
    write.str(linker.item.name())
    write.bool(linker.isInput)
    write.i(linker.dir)
    write.f(linker.expectAmount)

    write.f(linker.x)
    write.f(linker.y)
    write.f(linker.width)
    write.f(linker.height)
  }

  private fun readLinker(read: Reads, ver: Int): ItemLinker {
    val id = read.l()
    val res = ItemLinker(
      this,
      TooManyItems.itemsManager.getByName<Any>(read.str()),
      read.bool(),
      id
    )
    res.dir = read.i()
    res.expectAmount = read.f()
    res.setBounds(read.f(), read.f(), read.f(), read.f())
    return res
  }

  private fun normBound(): Pair<Vec2, Vec2> {
    val v1 = Vec2(Float.MAX_VALUE, Float.MAX_VALUE)
    val v2 = v1.cpy().scl(-1f)
    for (card in cards) {
      v1.x = min(v1.x.toDouble(), card!!.x.toDouble()).toFloat()
      v1.y = min(v1.y.toDouble(), card.y.toDouble()).toFloat()

      v2.x = max(v2.x.toDouble(), (card.x + card.width).toDouble()).toFloat()
      v2.y = max(v2.y.toDouble(), (card.y + card.height).toDouble()).toFloat()
    }
    return Pair(v1, v2)
  }

  private fun align(x: Float, y: Float, tab: Table, t: Table) {
    var align = if (x + t.width > tab.width) Align.right else Align.left

    align = if (y - t.height < 0) align or Align.bottom
    else align or Align.top

    t.setPosition(x, y, align)
  }
}