package tmi.ui.designer

import arc.Core
import arc.Graphics
import arc.func.Boolp
import arc.func.Cons
import arc.func.Prov
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
import arc.scene.ui.*
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.*
import arc.util.*
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.input.Binding
import mindustry.type.Item
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import mindustry.world.Block
import tmi.TooManyItems
import tmi.recipe.Recipe
import tmi.recipe.types.RecipeItem
import tmi.util.Consts
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

open class SchematicDesignerDialog : BaseDialog("") {
  companion object {
    const val FI_HEAD: Int = -0x315240ff

    val seq: Seq<ItemLinker?> = Seq()
    val tmp: Vec2 = Vec2()
    val tmp1: Vec2 = Vec2()
    val tmp2: Vec2 = Vec2()
    private val rect = Rect()
    private val alignTable = intArrayOf(
      Align.topLeft, Align.top, Align.topRight,
      Align.left, Align.center, Align.right,
      Align.bottomLeft, Align.bottom, Align.bottomRight,
    )
    private val alignIcon = arrayOf(
      Core.atlas.drawable("tmi-ui-top-left"), Core.atlas.drawable("tmi-ui-top"), Core.atlas.drawable("tmi-ui-top-right"),
      Core.atlas.drawable("tmi-ui-left"), Core.atlas.drawable("tmi-ui-center"), Core.atlas.drawable("tmi-ui-right"),
      Core.atlas.drawable("tmi-ui-bottom-left"), Core.atlas.drawable("tmi-ui-bottom"), Core.atlas.drawable("tmi-ui-bottom-right"),
    )

    protected fun useKeyboard(): Boolean {
      return !Vars.mobile || Core.settings.getBool("keyboard")
    }
  }

  var view: View? = null

  private val menuTable: Table = object : Table() {
    init {
      visible = false
    }
  }
  private val export by lazy { ExportDialog(this) }
  private val balance by lazy { BalanceDialog(this) }

  var selects: ObjectSet<Card?> = ObjectSet()

  private var removeArea: Table? = null
  private var sideTools: Table? = null

  var editLock = false
  var removeMode = false
  var selectMode = false
  var cardAlign = -1

  private var currAlignIcon: Drawable = Icon.none

  private var sideButtonEntries: Seq<SideBtn> = Seq.with(
    SideBtn(Core.bundle["dialog.calculator.add"], Icon.add) {
      TooManyItems.recipesDialog.toggle = Cons { r ->
        TooManyItems.recipesDialog.hide()
        addRecipe(r)
      }
      TooManyItems.recipesDialog.show()
    },
    SideBtn(Core.bundle["dialog.calculator.standard"], Icon.refresh) { view!!.standardization() },
    SideBtn(Core.bundle["dialog.calculator.align"], { currAlignIcon }) {
      if (menuTable.visible) {
        hideMenu()
      }
      else {
        showMenu(it, Align.right, Align.left, true) { t ->
          t.table(Tex.paneLeft) { ta ->
            for (i in alignTable.indices) {
              val align = alignTable[i]
              ta.button(alignIcon[i], Styles.clearNoneTogglei, 32f) {
                if (cardAlign == align) {
                  cardAlign = -1
                  currAlignIcon = Icon.none
                }
                else {
                  cardAlign = align
                  currAlignIcon = alignIcon[i]
                }
              }.size(40f).checked { cardAlign == align }

              if ((i + 1)%3 == 0) ta.row()
            }
          }.fill()
        }
      }
    },
    SideBtn(Core.bundle["dialog.calculator.selecting"], Icon.resize, {
      selectMode = !selectMode
      if (!selectMode) selects.clear()
    }) { selectMode },
    SideBtn(Core.bundle["dialog.calculator.read"], Icon.download) {
      Vars.platform.showFileChooser(true, "shd") { file ->
        try {
          view!!.read(file.reads())
        } catch (e: Exception) {
          Vars.ui.showException(e)
          Log.err(e)
        }
      }
    },
    SideBtn(
      Core.bundle["dialog.calculator.save"], Icon.save
    ) {
      Vars.platform.showFileChooser(false, "shd") { file ->
        try {
          file.writes().apply {
            view!!.write(this)
            close()
          }
        } catch (e: Exception) {
          Vars.ui.showException(e)
          Log.err(e)
        }
      }
    },
    SideBtn(Core.bundle["dialog.calculator.exportIMG"], Icon.export) { export.show() },
    SideBtn(Core.bundle["dialog.calculator.delete"], Icon.trash, {
      removeMode = !removeMode
      removeArea!!.clearActions()
      if (removeMode) {
        removeArea!!.actions(
          Actions.parallel(
            Actions.sizeTo(removeArea!!.width, Core.scene.height*0.15f, 0.12f),
            Actions.alpha(0.6f, 0.12f)
          )
        )
      }
      else removeArea!!.actions(
        Actions.parallel(
          Actions.sizeTo(removeArea!!.width, 0f, 0.12f),
          Actions.alpha(0f, 0.12f)
        )
      )
    }, { removeMode }),
    SideBtn(Core.bundle["dialog.calculator.lock"], { if (editLock) Icon.lock else Icon.lockOpen }, {
      editLock = !editLock
    }, { editLock })
  )

  class SideBtn {
    var desc: String
    var icon: Prov<Drawable>
    var action: Cons<Button>
    var checked: Boolp?

    constructor(desc: String, icon: Drawable, action: Cons<Button>) : this(desc, Prov<Drawable> { icon }, action)

    constructor(desc: String, icon: Prov<Drawable>, action: Cons<Button>) {
      this.desc = desc
      this.icon = icon
      this.action = action
      this.checked = null
    }

    constructor(desc: String, icon: Drawable, action: Cons<Button>, checked: Boolp) : this(
      desc,
      Prov<Drawable> { icon },
      action,
      checked
    )

    constructor(desc: String, icon: Prov<Drawable>, action: Cons<Button>, checked: Boolp?) {
      this.desc = desc
      this.icon = icon
      this.action = action
      this.checked = checked
    }
  }

  fun build(){
    titleTable.clear()

    cont.table().grow().get().add(View().also { view = it }).grow()

    addChild(menuTable)

    hidden {
      removeMode = false
      removeArea!!.height = 0f
      removeArea!!.color.a = 0f
      selectMode = false
      selects.clear()
      editLock = false
      hideMenu()
    }
    fill { t ->
      t.table { c ->
        val re = Runnable {
          c.clear()
          if (Core.graphics.isPortrait) c.center().bottom()
            .button("@back", Icon.left) { this@SchematicDesignerDialog.hide() }
            .size(210f, 64f)
          else c.top().right().button(Icon.cancel, Styles.flati, 32f) { this.hide() }
            .margin(5f)
        }
        resized(re)
        re.run()
      }.grow()
    }
    fill { t ->
      t.bottom().table(Consts.darkGrayUI) { area ->
        removeArea = area
        area.color.a = 0f
        area.add(Core.bundle["dialog.calculator.removeArea"])
      }.bottom().growX().height(0f)
    }
    fill { t ->
      t.top().table { zoom ->
        zoom.add("25%").color(Color.gray)
        zoom.table(Consts.darkGrayUIAlpha).fill().get().slider(0.25f, 1f, 0.01f) { f ->
          view!!.zoom.setScale(f)
          view!!.zoom.setOrigin(Align.center)
          view!!.zoom.isTransform = true
        }.update { s -> s.setValue(view!!.zoom.scaleX) }.width(400f)
        zoom.add("100%").color(Color.gray)
      }.growX().top()
      t.row()
      t.add(Core.bundle["dialog.calculator.editLock"], Styles.outlineLabel).padTop(60f).visible { editLock }
    }
    fill { t ->
      val fold = AtomicBoolean(false)
      t.right().stack(object : Table() {
        var main: Table? = null

        init {
          table(Tex.buttonSideLeft) { but ->
            but.touchable = Touchable.enabled
            val img = but.image(Icon.rightOpen).size(36f).get()
            actions(Actions.run {
              img.setOrigin(Align.center)
              img.setRotation(180f)
              setPosition(main!!.width, 0f)
              fold.set(true)
            })
            but.clicked {
              if (fold.get()) {
                clearActions()
                actions(Actions.moveTo(0f, 0f, 0.3f, Interp.pow2Out), Actions.run { fold.set(false) })
                img.setOrigin(Align.center)
                img.actions(Actions.rotateBy(180f, 0.3f), Actions.rotateTo(0f))
              }
              else {
                clearActions()
                actions(Actions.moveTo(main!!.width, 0f, 0.3f, Interp.pow2Out), Actions.run { fold.set(true) })
                img.setOrigin(Align.center)
                img.actions(Actions.rotateBy(180f, 0.3f), Actions.rotateTo(180f))
              }
            }
            but.hovered {
              img.setColor(Pal.accent)
              Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand)
            }
            but.exited {
              img.setColor(Color.white)
              Core.graphics.restoreCursor()
            }
            resized {
              actions(Actions.run {
                img.setOrigin(Align.center)
                img.setRotation((if (fold.get()) 180 else 0).toFloat())
                setPosition(if (fold.get()) main!!.width else 0f, 0f)
              })
            }
          }.size(36f, 122f).padRight(-5f)
          table(Tex.buttonSideLeft) { main: Table? ->
            this.main = main
          }.growY().width(320f)
        }
      }).fillX().growY().padTop(60f).padBottom(60f)
    }

    fill { t ->
      t.left().table(Consts.darkGrayUI) { sideBar ->
        sideBar.top().pane(Styles.noBarPane) { list ->
          sideTools = list
          list.top().defaults().size(40f).padBottom(8f)
          for (entry in sideButtonEntries) {
            var btn: Button? = null
            btn = list.button(Icon.lock, Styles.clearNoneTogglei, 32f) { entry.action[btn] }
              .update { b ->
                b.isChecked = entry.checked != null && entry.checked!!.get()
                b.style.imageUp = entry.icon.get()
              }.get()
            btn.addListener(Tooltip { tip -> tip.table(Tex.paneLeft).get().add(entry.desc) })
            list.row()
          }
        }.fill().padTop(8f)
        sideBar.add().growY()

        sideBar.row()
        sideBar.button(Icon.infoCircle, Styles.clearNonei, 32f) {}.padBottom(0f).size(40f).padBottom(8f)
      }.left().growY().fillX().padTop(100f).padBottom(100f)
    }
  }

  fun addRecipe(recipe: Recipe): RecipeCard {
    val res = RecipeCard(this, recipe)
    view!!.addCard(res)
    res.over!!.visible = true
    res.rebuildConfig()
    return res
  }

  fun addIO(item: RecipeItem<*>, isInput: Boolean) {
    view!!.addCard(IOCard(this, item, isInput))
  }

  fun moveLock(lock: Boolean) {
    view!!.lock = lock
  }

  fun showMenu(showOn: Element?, alignment: Int, tableAlign: Int, pack: Boolean, tabBuilder: Cons<Table>) {
    menuTable.clear()
    tabBuilder[menuTable]
    menuTable.draw()
    menuTable.act(1f)
    if (pack) menuTable.pack()

    menuTable.visible = true

    val v = Vec2()
    var r: Runnable
    menuTable.update(Runnable {
      if (pack) menuTable.pack()
      v[showOn!!.x] = showOn.y

      if ((alignment and Align.right) != 0) v.x += showOn.width
      else if ((alignment and Align.left) == 0) v.x += showOn.width/2

      if ((alignment and Align.top) != 0) v.y += showOn.height
      else if ((alignment and Align.bottom) == 0) v.y += showOn.height/2

      var align = tableAlign
      showOn.parent.localToStageCoordinates(tmp.set(v))

      if ((align and Align.right) != 0 && tmp.x - menuTable.width < 0) align = align and Align.right.inv() or Align.left
      if ((align and Align.left) != 0 && tmp.x + menuTable.width > Core.scene.width) align =
        align and Align.left.inv() or Align.right

      if ((align and Align.top) != 0 && tmp.y - menuTable.height < 0) align = align and Align.top.inv() or Align.bottom
      if ((align and Align.bottom) != 0 && tmp.y + menuTable.height > Core.scene.height) align =
        align and Align.bottom.inv() or Align.top

      showOn.parent.localToAscendantCoordinates(this, v)
      menuTable.setPosition(v.x, v.y, align)
    }.also { r = it })

    r.run()
  }

  fun hideMenu() {
    menuTable.visible = false
  }

  inner class View internal constructor() : Group() {
    var selecting: ItemLinker? = null

    val cards: Seq<Card> = Seq()
    val selectBegin: Vec2 = Vec2()
    val selectEnd: Vec2 = Vec2()
    var isSelecting: Boolean = false

    var enabled: Boolean = false
    var shown: Boolean = false
    var timer: Float = 0f

    var newSet: Card? = null
    var lock: Boolean = false

    var lastZoom: Float = -1f
    var panX: Float = 0f
    var panY: Float = 0f

    val container: Group
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

    init {
      container = object : Group() {
        override fun act(delta: Float) {
          super.act(delta)

          setPosition(panX + parent.width/2f, panY + parent.height/2f, Align.center)
        }

        override fun draw() {
          Consts.grayUI.draw(
            -Core.scene.width/zoom.scaleX,
            -Core.scene.height/zoom.scaleY,
            Core.scene.width/zoom.scaleX*2,
            Core.scene.height/zoom.scaleY*2
          )

          Lines.stroke(Scl.scl(4f), Pal.gray)
          Draw.alpha(parentAlpha)
          val gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", 150).toFloat())
          run {
            var offX = 0f
            while (offX <= (Core.scene.width)/zoom.scaleX - panX) {
              Lines.line(x + offX, -Core.scene.height/zoom.scaleY, x + offX, Core.scene.height/zoom.scaleY*2)
              offX += gridSize
            }
          }
          var offX = 0f
          while (offX >= -(Core.scene.width)/zoom.scaleX - panX) {
            Lines.line(x + offX, -Core.scene.height/zoom.scaleY, x + offX, Core.scene.height/zoom.scaleY*2)
            offX -= gridSize
          }

          run {
            var offY = 0f
            while (offY <= (Core.scene.height)/zoom.scaleY - panY) {
              Lines.line(-Core.scene.width/zoom.scaleX, y + offY, Core.scene.width/zoom.scaleX*2, y + offY)
              offY += gridSize
            }
          }
          var offY = 0f
          while (offY >= -(Core.scene.height)/zoom.scaleY - panY) {
            Lines.line(-Core.scene.width/zoom.scaleX, y + offY, Core.scene.width/zoom.scaleX*2, y + offY)
            offY -= gridSize
          }
          super.draw()
        }
      }
      zoom.addChild(container)
      fill { t -> t.add(zoom).grow() }

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
      }

      //left tap listener
      addCaptureListener(object : ClickListener(KeyCode.mouseLeft) {
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
              val v = c!!.stageToLocalCoordinates(tmp.set(x, y))
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
          hideMenu()
        }

        override fun isOver(element: Element, x: Float, y: Float): Boolean {
          return !dragged
        }
      })

      //zoom and pan with keyboard and mouse
      addListener(object : InputListener() {
        override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
          zoom.setScale(Mathf.clamp(zoom.scaleX - amountY/10f*zoom.scaleX, 0.25f, 1f).also { lastZoom = it })
          zoom.setOrigin(Align.center)
          zoom.isTransform = true

          clamp()
          return true
        }

        override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?) {
          requestScroll()
          super.enter(event, x, y, pointer, fromActor)
        }
      })

      //right tap selecting
      addCaptureListener(object : InputListener() {
        val begin: Vec2 = Vec2()

        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
          if (shown) {
            hideMenu()
            shown = false
          }
          if (!((pointer == 0 && (button == KeyCode.mouseRight || !useKeyboard())).also { enabled = it })) return false
          timer = Time.globalTime
          begin[x] = y
          return true
        }

        override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
          if (pointer != 0 || !((useKeyboard() && button == KeyCode.mouseRight) || (!useKeyboard() && Time.globalTime - timer > 60))) return
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

      //zoom and pan
      addCaptureListener(object : ElementGestureListener() {
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

      //area selecting
      addCaptureListener(object : ElementGestureListener() {
        var enable: Boolean = false
        var panned: Boolean = false
        var beginX: Float = 0f
        var beginY: Float = 0f

        val rect: Rect = Rect()
        val lastSelected: ObjectSet<Card?> = ObjectSet()

        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
          enable = selectMode || button == KeyCode.mouseLeft
          if (enable) {
            var b = view!!.hitCard(x, y, true) != null
            if (!b) view!!.eachCard(x, y, false) { card ->
              if (b) return@eachCard
              val v = card!!.stageToLocalCoordinates(tmp.set(x, y))
              b = b or (card.hitLinker(v.x, v.y) != null)
            }

            if (b) {
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
            tmp.set(selectBegin)
            localToStageCoordinates(tmp)
            rect.setPosition(tmp.x, tmp.y)
            tmp.set(selectEnd)
            localToStageCoordinates(tmp)
            rect.setSize(tmp.x - rect.x, tmp.y - rect.y)

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
              eachCard(rect, { key: Card? -> selects.add(key) }, true)
            }
          }
        }
      })
    }

    private fun buildMenu(x: Float, y: Float) {
      shown = true
      val selecting = hitCard(x, y, true)
      showMenu(view, Align.bottomLeft, Align.bottomLeft, false) { tab ->
        tab.table(Consts.darkGrayUIAlpha) { menu ->
          menu.defaults().growX().fillY().minWidth(300f)
          if (!selects.isEmpty) {
            menu.button(Core.bundle["misc.remove"], Icon.trash, Styles.cleart, 22f) {
              hideMenu()
              selects.each { card: Card? -> this@View.removeCard(card) }
              selects.clear()
            }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
            menu.row()

            menu.button(Core.bundle["misc.copy"], Icon.copy, Styles.cleart, 22f) {
              hideMenu()
              for (card in selects) {
                val clone = card!!.copy()
                tmp[clone.x + clone.width/2 + 40] = clone.y + clone.height/2 - 40
                container.localToStageCoordinates(tmp)
                addCard(clone, tmp.x, tmp.y, true)
              }
            }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
            menu.row()

            menu.button(Core.bundle["misc.balance"], Icon.effect, Styles.cleart, 22f) {
              if (selects.size == 1) {
                val c = selects.first()
                if (c is IOCard) {
                  if (c.isInput) {
                    val out: ItemLinker? = if (c.linkerOuts.isEmpty) null else c.linkerOuts.first()

                    if (out == null || out.links.isEmpty) {
                      Vars.ui.showInfo(Core.bundle.format("misc.assignInvalid"))
                      return@button
                    }

                    if (checkNorm(c, out)) return@button
                  }
                  else {
                    val linkIn: ItemLinker? = if (c.linkerOuts.isEmpty) null else c.linkerIns.first()

                    if (linkIn == null || linkIn.links.isEmpty) {
                      Vars.ui.showInfo(Core.bundle.format("misc.assignInvalid"))
                      return@button
                    }

                    var sum = 0f
                    for (linker in linkIn.links.keys()) {
                      if (!linker!!.isNormalized) {
                        Vars.ui.showInfo(Core.bundle.format("misc.assignInvalid"))
                        return@button
                      }

                      val rate = if (linkIn.links.size == 1) 1f else linkIn.links[linker]!![0]
                      sum += linker.expectAmount*rate
                    }
                    c.stack.amount = sum
                  }
                }
                else balance.show()
              }
              else {
                Vars.ui.showInfo("WIP")
              }
              hideMenu()
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
              hideMenu()
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
                  hideMenu()
                }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
                menu.row()
                menu.button(Core.bundle["dialog.calculator.addInputAs"], Icon.download, Styles.cleart, 22f) {
                  addCard(IOCard(this@SchematicDesignerDialog, linker.item, true), x, y, true)
                  hideMenu()
                }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
                menu.row()
              }
              else {
                menu.button(Core.bundle["dialog.calculator.addOutputAs"], Icon.upload, Styles.cleart, 22f) {
                  addCard(IOCard(this@SchematicDesignerDialog, linker.item, false), x, y, true)
                  hideMenu()
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

              tmp[x] = y
              container.stageToLocalCoordinates(tmp)
              newSet!!.setPosition(tmp.x, tmp.y, Align.center)
              newSet!!.gridAlign(cardAlign)
            }
            TooManyItems.recipesDialog.show()
            hideMenu()
          }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
          menu.row()

          menu.button(Core.bundle["dialog.calculator.addInput"], Icon.download, Styles.cleart, 22f) {
            //addCard(new IOCard(linker.item, true), x, y, true);
            showMenu(view, Align.bottomLeft, Align.bottomLeft, false) { list ->
              list.table(Consts.darkGrayUIAlpha) { items ->
                val l = TooManyItems.itemsManager.list.removeAll { e -> !TooManyItems.recipesManager.anyMaterial(e) || e.item is Block }
                buildItems(items, l) { item ->
                  view!!.addCard(IOCard(this@SchematicDesignerDialog, item, true), x, y, true)
                  hideMenu()
                }
              }.update { t -> align(x, y, list, t) }.margin(8f)
            }
          }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
          menu.row()
          menu.button(Core.bundle["dialog.calculator.addOutput"], Icon.upload, Styles.cleart, 22f) {
            //addCard(new IOCard(linker.item, false), x, y, true);
            showMenu(view, Align.bottomLeft, Align.bottomLeft, false) { list ->
              list.table(Consts.darkGrayUIAlpha) { items ->
                val l = TooManyItems.itemsManager.list.removeAll { e -> !TooManyItems.recipesManager.anyProduction(e) || e.item is Block }
                buildItems(items, l) { item ->
                  view!!.addCard(IOCard(this@SchematicDesignerDialog, item, false), x, y, true)
                  hideMenu()
                }
              }.update { t -> align(x, y, list, t) }.margin(8f)
            }
          }.margin(12f).get().labelCell.padLeft(6f).get().setAlignment(Align.left)
          menu.row()
        }.update { t -> align(x, y, tab, t) }
        tab.setSize(view!!.width, view!!.height)
      }
    }

    private fun checkNorm(c: IOCard, out: ItemLinker): Boolean {
      var sum = 0f
      for (linker in out.links.keys()) {
        if (!linker!!.isNormalized) {
          Vars.ui.showInfo(Core.bundle.format("misc.assignInvalid"))
          return true
        }

        val rate = if (linker.links.size == 1) 1f else linker.links[out]!![0]
        sum += linker.expectAmount*rate
      }
      c.stack.amount = sum
      return false
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

    fun addCard(card: Card, build: Boolean) {
      addCard(card, Core.scene.width/2, Core.scene.height/2, build)
    }

    fun addCard(card: Card, x: Float = Core.scene.width/2, y: Float = Core.scene.height/2, build: Boolean = true) {
      cards.add(card)
      container.addChild(card)

      tmp[x] = y
      container.stageToLocalCoordinates(tmp)

      if (build) {
        card.build()
        card.pack()
        card.buildLinker()
        card.draw()
        card.setPosition(tmp.x, tmp.y, Align.center)

        card.gridAlign(cardAlign)
      }
      newSet = card
    }

    fun removeCard(card: Card?) {
      cards.remove(card)
      container.removeChild(card)

      seq.clear().addAll(card!!.linkerIns).addAll(card.linkerOuts)
      for (linker in seq) {
        for (link in linker!!.links.keys().toSeq()) {
          linker.deLink(link)
          if (link!!.links.isEmpty && link.isInput) link.remove()
        }
      }
    }

    private fun clamp() {
      val par = parent ?: return

    }

    fun eachCard(range: Rect, cons: Cons<Card?>, inner: Boolean) {
      for (card in cards) {
        val (v1, v2) = checkNorm(inner, card)
        val ox = v1.x
        val oy = v1.y
        val wx = v2.x
        val wy = v2.y

        rect.set(ox, oy, wx - ox, wy - oy)
        if (range.contains(rect) || (!inner && range.overlaps(rect))) cons[card]
      }
    }

    private fun checkNorm(inner: Boolean, card: Card): Pair<Vec2, Vec2> {
      if (inner) tmp1.set(card.child.x, card.child.y).add(card.x, card.y)
      else tmp1[card.x] = card.y
      card.parent.localToStageCoordinates(tmp1)

      if (inner) tmp2.set(card.child.x + card.child.width, card.child.y + card.child.height).add(
        card.x, card.y
      )
      else tmp2[card.x + card.width] = card.y + card.height
      card.parent.localToStageCoordinates(tmp2)

      return tmp1 to tmp2
    }

    fun eachCard(stageX: Float, stageY: Float, inner: Boolean, cons: Cons<Card?>) {
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

      for (card in cards) {
        card!!.moveBy(offX, offY)
      }

      panX = 0f
      panY = 0f
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
      if (head != FI_HEAD) throw IOException("file format error, unknown file head: " + Integer.toHexString(head))

      val ver = read.i()

      val cardsLen = read.i()
      for (i in 0 until cardsLen) {
        val card: Card = Card.read(read, ver)
        addCard(card, false)
        card.build()
        card.mul = read.i()
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

    private fun readLinker(read: Reads, ver: Int): ItemLinker {
      val id = read.l()
      val res =
        ItemLinker(this@SchematicDesignerDialog, TooManyItems.itemsManager.getByName<Any>(read.str()), read.bool(), id)
      res.dir = read.i()
      res.expectAmount = read.f()
      res.setBounds(read.f(), read.f(), read.f(), read.f())
      return res
    }

    fun write(write: Writes) {
      write.i(FI_HEAD)
      write.i(0)

      write.i(cards.size)
      for (card in cards) {
        card!!.write(write)
        write.i(card.mul)
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
            write.f(entry.value[0])
          }
        }
      }
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

    private fun align(x: Float, y: Float, tab: Table, t: Table) {
      var align = if (x + t.width > tab.width) Align.right else Align.left

      align = if (y - t.height < 0) align or Align.bottom
      else align or Align.top

      t.setPosition(x, y, align)
    }
  }

  protected fun buildItems(items: Table, list: Seq<RecipeItem<*>>, callBack: Cons<RecipeItem<*>>) {
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

  fun setMoveLocker(inner: Element) {
    inner.addCaptureListener(object : InputListener() {
      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
        moveLock(true)
        return true
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        moveLock(false)
      }
    })
  }
}
