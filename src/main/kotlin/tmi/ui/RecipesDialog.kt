package tmi.ui

import arc.Core
import arc.Graphics
import arc.func.Boolc
import arc.func.Cons
import arc.func.Intc
import arc.func.Intp
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.GlyphLayout
import arc.graphics.g2d.Lines
import arc.input.KeyCode
import arc.math.Mathf
import arc.scene.Element
import arc.scene.event.ElementGestureListener
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.style.BaseDrawable
import arc.scene.style.Drawable
import arc.scene.ui.*
import arc.scene.ui.Slider.SliderStyle
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Align
import arc.util.Scaling
import arc.util.Time
import arc.util.Tmp
import mindustry.Vars
import mindustry.ctype.Content
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Fonts
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import mindustry.world.Block
import tmi.TooManyItems
import tmi.recipe.Recipe
import tmi.recipe.RecipeType
import tmi.recipe.types.RecipeItem
import tmi.ui.RecipesDialog.Mode.*
import tmi.util.Consts.a_z
import tmi.util.Consts.grayUIAlpha
import tmi.util.Consts.padGrayUIAlpha
import java.text.Collator
import java.util.*
import kotlin.math.min

private val nameComparator: Collator = Collator.getInstance(Core.bundle.locale)

open class RecipesDialog : BaseDialog(Core.bundle["dialog.recipes.title"]) {
  var sortings: Seq<Sorting> = Seq.with(
    Sorting(
      Core.bundle["misc.defaultSort"],
      Icon.menu
    ){ a, b -> a.compareTo(b) },
    Sorting(
      Core.bundle["misc.nameSort"],
      a_z
    ){ a, b ->
      nameComparator.compare(
        a.localizedName(), b.localizedName()
      )
    },
    Sorting(
      Core.bundle["misc.modSort"],
      Icon.book
    ){ a, b ->
      if (a.item is Content && b.item is Content) {
        val ca = a.item
        val cb = b.item

        if (ca.minfo.mod == null) if (cb.minfo.mod == null) 0 else -1
        else if (cb.minfo.mod != null) nameComparator.compare(
          ca.minfo.mod.name,
          cb.minfo.mod.name
        )
        else 1
      }
      else 0
    },
    Sorting(
      Core.bundle["misc.typeSort"],
      Icon.file
    ){ a, b ->
      val n = a!!.typeID().compareTo(b!!.typeID())
      if (n == 0) {
        if (a.item is Block && b.item is Block) {
          val ca = a.item
          val cb = b.item

          if (ca.hasBuilding() && cb.hasBuilding()) {
            if (ca.update && cb.update) return@Sorting 0
            else if (ca.update) return@Sorting 1
            else if (cb.update) return@Sorting -1
          }
          else if (ca.hasBuilding()) return@Sorting 1
          else if (cb.hasBuilding()) return@Sorting -1
        }
      }
      n
    }
  )

  var recipesTable: Table? = null
  var contentsTable: Table? = null
  var sortingTab: Table? = null
  var modeTab: Table? = null
  var currZoom: Table? = null
  var currView: RecipeView? = null

  private var _currentSelect: RecipeItem<*>? = null
  var currentSelect: RecipeItem<*>?
    get() = _currentSelect
    set(content) {
      if (currentSelect == content) return
      val old = currentSelect

      _currentSelect = content
      if (currentSelect == null) return
      if (!buildRecipes()) {
        _currentSelect = old
      }
    }
  var recipeMode: Mode? = null
    set(mode) {
      run {
        if (mode == field) return
        val oldMode = field

        field = mode
        if (!buildRecipes()) {
          field = oldMode
        }
      }
    }

  var sorting: Sorting = sortings.first()

  var locked = false
  var contentSearch = ""
  var reverse = false
  var total = 0
  var fold = 0
  var recipeIndex = 0
  var itemPages = 0
  var pageItems = 0
  var currPage = 0

  var lastZoom = -1f

  val ucSeq = Seq<RecipeItem<*>>()

  var contentsRebuild = {}
  var refreshSeq = {}
  var rebuildRecipe = {}

  var toggle: Cons<Recipe>? = null

  fun build(){
    addCloseButton()

    //TODO: not usable yet
    if (Core.settings.getBool("tmi_enable_preview")) buttons.button(Core.bundle["dialog.recipes.designer"], Icon.book) {
      TooManyItems.schematicDesigner.show()
      hide()
    }

    hidden { toggle = null }
    shown { this.buildBase() }
    resized { this.buildBase() }

    hidden {
      currentSelect = null
      recipeMode = null
      currPage = 0
      lastZoom = -1f
      sorting = sortings.first()
      cont.clear()
    }
  }

  protected open fun buildBase() {
    cont.clearChildren()

    if (Core.graphics.isPortrait) {
      recipesTable = cont.table(padGrayUIAlpha).grow().pad(5f).get()
      cont.row()

      val tab = object : Table(grayUIAlpha, { t ->
        contentsTable = t.table(padGrayUIAlpha).growX().height(Core.graphics.height/2f/Scl.scl()).get()
      }) {
        override fun validate() {
          parent.invalidateHierarchy()
          if (getWidth() != parent.width || getHeight() != prefHeight) {
            setSize(parent.width, prefHeight)
            invalidate()
          }
          super.validate()
        }
      }
      tab.visible = false
      cont.addChild(tab)

      cont.button(Icon.up, Styles.clearNonei, 32f) {
        tab.visible = !tab.visible
      }.growX().height(40f).update { i: ImageButton ->
        i.style.imageUp = if (tab.visible) Icon.downOpen else Icon.upOpen
        tab.setSize(tab.parent.width, tab.prefHeight)
        tab.setPosition(i.x, i.y + i.prefHeight + 4, Align.bottomLeft)
      }
    }
    else {
      recipesTable = cont.table(padGrayUIAlpha).grow().pad(5f).get()
      cont.image().color(Pal.accent).growY().pad(0f).width(4f)
      contentsTable = cont.table(padGrayUIAlpha).growY().width(Core.graphics.width/2.5f/Scl.scl()).pad(5f).get()
    }

    buildContents()
    buildRecipes()
  }

  protected open fun buildContents() {
    contentsTable!!.addListener(object : InputListener() {
      override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
        if (amountY < 0 && currPage > 0) {
          currPage--
          contentsRebuild()
        }
        else if (amountY > 0 && currPage < itemPages - 1) {
          currPage++
          contentsRebuild()
        }
        return true
      }

      override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        contentsTable!!.requestScroll()
        super.enter(event, x, y, pointer, fromActor)
      }
    })

    val isScroll = Core.settings.getBool("tmi_items_pane", false)

    contentsTable!!.table { filter ->
      filter.add(Core.bundle["misc.search"])
      filter.image(Icon.zoom).size(36f).scaling(Scaling.fit)
      filter.field(contentSearch) { str: String ->
        contentSearch = str.lowercase(Locale.getDefault())
        refreshSeq()
      }.growX()

      sortingTab = Table(grayUIAlpha) { ta ->
        for (sort in sortings) {
          ta.button({ t: Button ->
            t.defaults().left().pad(5f)
            t.image(sort.icon).size(24f).scaling(Scaling.fit)
            t.add(sort.localized).growX()
          }, Styles.clearNoneTogglei){
            sorting = sort
            refreshSeq()
          }.margin(6f).growX().fillY()
            .update { e: Button -> e.isChecked = sorting == sort }

          ta.row()
        }
      }
      sortingTab!!.visible = false

      val b = filter.button(Icon.up, Styles.clearNonei, 32f) {
        sortingTab!!.visible = !sortingTab!!.visible
      }.size(36f).get()

      b.update {
        b.style.imageUp = sorting.icon
        sortingTab!!.setSize(sortingTab!!.prefWidth, sortingTab!!.prefHeight)
        sortingTab!!.setPosition(b.x, filter.y, Align.top)
      }

      filter.button({ bu: Button ->
        bu.image().size(32f).scaling(Scaling.fit)
          .update { i: Image -> i.drawable = if (reverse) Icon.up else Icon.down }
      }, Styles.clearNonei, {
        reverse = !reverse
        refreshSeq()
      }).size(36f)
      filter.add("").color(Pal.accent)
        .update { l: Label -> l.setText(Core.bundle[if (reverse) "misc.reverse" else "misc.order"]) }
    }.padBottom(12f).growX()
    contentsTable!!.row()
    contentsTable!!.table { t ->
      refreshSeq = run@{
        fold = 0
        total = 0
        ucSeq.clear()

        TooManyItems.itemsManager.list.forEach { item ->
          if (TooManyItems.recipesManager.anyRecipe(item)) {
            total++
            if (item.localizedName().lowercase(Locale.getDefault())?.contains(contentSearch) != true
              && !item.name().lowercase(Locale.getDefault()).contains(contentSearch)) {
              fold++
              return@run
            }
            ucSeq.add(item)
          }
        }

        if (reverse) ucSeq.sort { a: RecipeItem<*>?, b: RecipeItem<*>? -> sorting.sort.compare(b, a) }
        else ucSeq.sort(sorting.sort)
        contentsRebuild()
      }
      contentsRebuild = if (isScroll) {{
        val width = t.width
        val num = (width/Scl.scl(60f)).toInt()
        t.clearChildren()
        t.pane { pane ->
          pane.left().top().defaults().size(60f, 90f)
          for (i in 0 until ucSeq.size) {
            val content = ucSeq[i]
            buildItem(pane, content)

            if ((i + 1)%num == 0) {
              pane.row()
            }
          }
        }.grow()
      }}
      else {{
        val width = t.width
        val height = t.height
        t.clearChildren()
        t.left().top().defaults().size(60f, 90f)

        val xn = (width/Scl.scl(60f)).toInt()
        val yn = (height/Scl.scl(90f)).toInt()

        pageItems = xn*yn
        itemPages = Mathf.ceil(ucSeq.size.toFloat()/pageItems)

        var curX = 0

        if (currPage < 0) {
          val index = ucSeq.indexOf(currentSelect)
          currPage = index/pageItems
        }

        currPage = Mathf.clamp(currPage, 0, itemPages - 1)
        val from = currPage*pageItems
        val to = currPage*pageItems + pageItems
        for (i in from until to) {
          if (i >= ucSeq.size) break

          val content = ucSeq[i]
          buildItem(t, content)

          curX++
          if (curX >= xn) {
            t.row()
            curX = 0
          }
        }
      }}
    }.grow().pad(0f)

    if (!isScroll) {
      contentsTable!!.row()
      contentsTable!!.table { butt ->
        buildPage(butt, { currPage }, { page: Int ->
          currPage = page
          contentsRebuild()
        }, { itemPages })
      }.fillY().growX()
    }

    contentsTable!!.row()
    contentsTable!!.add("").color(Color.gray).left().growX()
      .update { l: Label -> l.setText(Core.bundle.format("dialog.recipes.total", total, fold)) }

    contentsTable!!.addChild(sortingTab)

    Core.app.post { refreshSeq() }
  }

  private fun makePage(page: Table): GlyphLayout {
    val l = GlyphLayout.obtain()
    val i = Mathf.ceil(Mathf.log(itemPages.toFloat(), 10f))
    val s = StringBuilder()
    for (n in 0 until i) {
      s.append("0")
    }
    l.setText(Fonts.def, s.toString())

    page.add(Core.bundle["dialog.recipes.jump_a"])
    return l
  }

  protected open fun buildRecipes(): Boolean {
    val recipes: Seq<Recipe>?

    if (currentSelect != null && currentSelect!!.item !is Block && recipeMode === FACTORY) recipeMode = null

    if (currentSelect == null) {
      recipes = null

      recipesTable!!.clearChildren()
      recipesTable!!.table { top ->
        top.table { t ->
          t.table(Tex.buttonTrans).size(90f)
          t.row()
          t.add(Core.bundle["dialog.recipes.currSelected"]).growX().color(Color.lightGray).get()
            .setAlignment(Align.center)
        }
        top.table { infos ->
          infos.left().top().defaults().left()
          infos.add(Core.bundle["dialog.recipes.unselected"]).color(Pal.accent)
        }.grow().padLeft(12f).padTop(8f)
      }.left().growX().fillY().pad(8f)
      recipesTable!!.row()
      recipesTable!!.add().grow()
    }
    else {
      if (recipeMode == null) {
        recipeMode = if (TooManyItems.recipesManager.anyMaterial(currentSelect)) USAGE
        else if (TooManyItems.recipesManager.anyProduction(currentSelect)) RECIPE
        else if (currentSelect?.item is Block)
          if (TooManyItems.recipesManager.getRecipesByFactory(currentSelect!!).any()) FACTORY
          else RECIPE
        else null
      }

      recipes = if (currentSelect == null || recipeMode == null) null else when (recipeMode!!) {
        USAGE -> TooManyItems.recipesManager.getRecipesByMaterial(currentSelect!!)
        RECIPE -> TooManyItems.recipesManager.getRecipesByProduction(currentSelect!!)
        FACTORY -> TooManyItems.recipesManager.getRecipesByFactory(currentSelect!!)
      }
    }

    val recipeViews = Seq<RecipeView>()
    if (recipes != null) {
      for (recipe in recipes) {
        val view = RecipeView(recipe) { i, _, m -> setCurrSelecting(i.item, m) }
        recipeViews.add(view)
      }
    }

    if (recipes == null || recipeViews.isEmpty) return false

    recipesTable!!.clearListeners()
    recipesTable!!.addListener(object : InputListener() {
      override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
        if (locked) {
          recipeIndex = Mathf.clamp(recipeIndex + (if (amountY > 0) 1 else -1), 0, recipeViews.size - 1)
          return false
        }
        if (currZoom == null) return false
        currZoom!!.setScale(
          Mathf.clamp(currZoom!!.scaleX - amountY/10f*currZoom!!.scaleX, 0.25f, 1f).also { lastZoom = it })
        currZoom!!.setOrigin(Align.center)
        currZoom!!.isTransform = true

        clamp(currZoom)
        return true
      }

      override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        recipesTable!!.requestScroll()
        super.enter(event, x, y, pointer, fromActor)
      }
    })

    recipesTable!!.addCaptureListener(object : ElementGestureListener() {
      override fun zoom(event: InputEvent, initialDistance: Float, distance: Float) {
        if (lastZoom < 0) {
          lastZoom = currZoom!!.scaleX
        }

        currZoom!!.setScale(Mathf.clamp(distance/initialDistance*lastZoom, 0.25f, 1f))
        currZoom!!.setOrigin(Align.center)
        currZoom!!.isTransform = true

        clamp(currZoom)
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        lastZoom = currZoom!!.scaleX
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (locked) return
        currZoom!!.moveBy(deltaX, deltaY)
        clamp(currZoom)
      }
    })

    recipesTable!!.touchable = Touchable.enabled

    currZoom = Table { main ->
      recipeIndex = 0
      rebuildRecipe = {
        main.center()
        main.clearChildren()
        val cur = recipeViews[recipeIndex].also { currView = it }
        cur.validate()
        val currSel = currentSelect!!
        main.table { modes ->
          modeTab = Table(grayUIAlpha) { ta ->
            for (mode in Mode.entries) {
              if (mode === FACTORY && (currSel.item !is Block || TooManyItems.recipesManager.getRecipesByFactory(currSel).isEmpty)) continue
              else if (mode === RECIPE && TooManyItems.recipesManager.getRecipesByProduction(currSel).isEmpty) continue
              else if (mode === USAGE && TooManyItems.recipesManager.getRecipesByMaterial(currSel).isEmpty) continue

              ta.button({ t: Button ->
                  t.defaults().left().pad(5f)
                  t.image(mode.icon()).size(24f).scaling(Scaling.fit)
                  t.add(mode.localized()).growX()
                }, Styles.clearNoneTogglei
              ){ recipeMode = mode }.margin(6f).growX().fillY().update { e: Button -> e.isChecked = mode == recipeMode }
              ta.row()
            }
          }
          modeTab!!.visible = false
          modes.add(object : Button(Styles.clearNonei) {
            init {
              touchable = if (modeTab!!.children.size > 1) Touchable.enabled else Touchable.disabled

              image().scaling(Scaling.fit).size(32f).update { i: Image -> i.drawable = recipeMode!!.icon() }
              add("").padLeft(4f).update { l: Label -> l.setText(recipeMode!!.localized()) }

              clicked { modeTab!!.visible = !modeTab!!.visible }

              update {
                modeTab!!.setSize(modeTab!!.prefWidth, modeTab!!.prefHeight)
                modeTab!!.setPosition(modes.x + x + width/2, modes.y, Align.top)
              }
            }
          }).margin(8f).fill().get()
        }.fill()
        main.row()
        main.table().center().fill().get().add(cur).center().fill().pad(20f)
        main.row()
        main.table { t ->
          t.center().defaults().center()
          cur.recipe.subInfoBuilder?.get(t)
        }.fill().padTop(8f)

        main.addChild(modeTab)

        main.validate()

        main.setSize(main.prefWidth, main.prefHeight)

        var scl = Mathf.clamp((main.parent.width*0.8f)/main.width, 0.25f, 1f)
        scl =
          min(scl.toDouble(), Mathf.clamp((main.parent.height*0.8f - Scl.scl(20f))/main.height, 0.25f, 1f).toDouble())
            .toFloat()
        if (lastZoom <= 0) {
          main.setScale(scl)
        }
        else main.setScale(Mathf.clamp(lastZoom, 0.25f, scl))
        main.setOrigin(Align.center)
        main.isTransform = true

        main.setPosition(main.parent.width/2, main.parent.height/2, Align.center)
        clamp(main)
      }
    }

    recipesTable!!.clearChildren()
    recipesTable!!.fill { t ->
      t.table { clip ->
        clip.clip = true
        clip.addChild(currZoom)
      }.grow().pad(8f)
    }
    recipesTable!!.table { top ->
      top.table { t ->
        t.table(Tex.buttonTrans).size(90f).get().image(currentSelect!!.icon()).size(60f).scaling(Scaling.fit)
        t.row()
        t.add(Core.bundle["dialog.recipes.currSelected"]).growX().fillY().color(Color.lightGray).wrap().get()
          .setAlignment(Align.center)
      }
      top.table { infos ->
        infos.left().top().defaults().left()
        infos.add(currentSelect!!.localizedName()).color(Pal.accent)
        infos.row()
        infos.add(currentSelect!!.name()).color(Color.gray)
        if (currentSelect!!.locked()) {
          infos.row()
          infos.add(Core.bundle["dialog.recipes.locked"]).color(Color.gray)
        }
      }.grow().padLeft(12f).padTop(8f)
    }.left().growX().fillY().pad(8f)
    recipesTable!!.row()
    recipesTable!!.add().grow()
    recipesTable!!.row()
    recipesTable!!.table { bu ->
      bu.button(Icon.add, Styles.clearNonei, 36f) {
        toggle!![recipes[recipeIndex]]
      }.margin(5f).disabled {
        val r = recipes[recipeIndex]
        if (r.recipeType === RecipeType.building) return@disabled true
        var ba = false
        for (key in r.productions.keys()) {
          if (!RecipeType.generator.isPower(key)) ba = true
        }
        !ba
      }
    }.visible { toggle != null }
    recipesTable!!.row()
    recipesTable!!.table { butt ->
      buildPage(butt, { recipeIndex }, { page: Int ->
        recipeIndex = page
        rebuildRecipe()
      }, { recipeViews.size })
    }.pad(8f).growX().fillY()

    Core.app.post(rebuildRecipe)

    return true
  }


  private fun buildPage(table: Table, currPage: Intp, setPage: Intc, maxPage: Intp) {
    table.button(Icon.leftOpen, Styles.clearNonei, 32f) { setPage[currPage.get() - 1] }
      .disabled { currPage.get() <= 0 }.size(45f)
    table.button("<<", Styles.cleart) { setPage[0] }
      .disabled { currPage.get() <= 0 }.size(45f).get().style.disabled = Styles.none
    table.table { t ->
      t.touchable = Touchable.enabled
      val buildPage = arrayOfNulls<Boolc>(1)
      buildPage[0] = Boolc { b: Boolean ->
        t.clear()
        t.hovered { Core.graphics.cursor(Graphics.Cursor.SystemCursor.hand) }
        t.exited { Core.graphics.restoreCursor() }
        if (b) {
          val l = makePage(t)
          t.field(
            (currPage.get() + 1).toString(),
            { _, c -> Character.isDigit(c) },
            { st -> setPage[Mathf.clamp(if (st.isEmpty()) 0 else st.toInt() - 1, 0, maxPage.get())] }
          ).width(l.width + 45)
            .update { f: TextField ->
              if (f.text.isEmpty()) return@update
              f.text = (currPage.get() + 1).toString()
            }
          t.add(Core.bundle.format("dialog.recipes.jump_b", maxPage.get()))
          t.update {
            if (Core.input.justTouched() && Core.scene.hit(
                Core.input.mouseX().toFloat(),
                Core.input.mouseY().toFloat(),
                true
              ).parent !== t
            ) buildPage[0]!![false]
          }

          l.free()
        }
        else {
          t.add("").update { l: Label ->
            l.setAlignment(Align.center)
            l.setText(Core.bundle.format("dialog.recipes.pages", currPage.get() + 1, maxPage.get()))
          }.growX()
          t.clicked { buildPage[0]!![true] }
        }
      }
      buildPage[0]!![false]
    }.growX()
    table.button(">>", Styles.cleart) {
      setPage[maxPage.get() - 1]
    }.disabled { currPage.get() >= maxPage.get() - 1 }.size(45f).get().style.disabled = Styles.none
    table.button(Icon.rightOpen, Styles.clearNonei, 32f) {
      setPage[currPage.get() + 1]
    }.disabled { currPage.get() >= maxPage.get() - 1 }.size(45f)

    table.row()
    val slider = table.slider(0f, maxPage.get().toFloat(), 0.001f, 1f) { f: Float -> setPage[Mathf.round(f)] }
      .grow().colspan(5)
      .update { s: Slider ->
        s.setRange(0f, Mathf.maxZero((maxPage.get() - 1).toFloat()))
        if (!s.isDragging) s.setValue(currPage.get().toFloat())
      }.visible { maxPage.get() > 1 }.pad(4f, 12f, 4f, 12f).get()
    slider.setStyle(pageSlider(maxPage))
    slider.addListener(object : InputListener() {
      var touching: Boolean = false
      var hovering: Boolean = false
      override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        hovering = true
        locked = true
      }

      override fun exit(event: InputEvent, x: Float, y: Float, pointer: Int, toActor: Element?) {
        hovering = false
        if (!touching) locked = false
      }

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
        touching = true
        locked = true
        return true
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        touching = false
        if (!hovering) locked = false
      }
    })
  }

  private fun clamp(currZoom: Table?) {
    val par = currZoom!!.parent ?: return

    val zoomW = currZoom.width*currZoom.scaleX
    val zoomH = currZoom.height*currZoom.scaleY
    val zoomX = currZoom.x + currZoom.width/2
    val zoomY = currZoom.y + currZoom.height/2

    val originX = par.width/2
    val originY = par.height/2

    val diffX = zoomX - originX
    val diffY = zoomY - originY
    val maxX = if (par.width > zoomW) (par.width - zoomW)/2.1f
    else (zoomW - par.width)/2f

    val maxY = if (par.height > zoomH) (par.height - zoomH)/2.1f
    else (zoomH - par.height)/2f

    val cx = Mathf.clamp(diffX, -maxX, maxX)
    val cy = Mathf.clamp(diffY, -maxY, maxY)

    currZoom.setPosition(originX + cx, originY + cy, Align.center)
  }

  private fun buildItem(t: Table, content: RecipeItem<*>) {
    t.add(object : Table() {
      var progress: Float = 0f
      var alpha: Float = 0f
      var activity: Boolean = false
      var touched: Boolean = false
      var time: Float = 0f
      var clicked: Int = 0

      init {
        defaults().padLeft(8f).padRight(8f)

        hovered { activity = true }
        exited { activity = false }
        tapped {
          touched = true
          time = Time.globalTime
        }
        released {
          touched = false
          if (Time.globalTime - time < 12) {
            if (!Vars.mobile || Core.settings.getBool("keyboard")) {
              TooManyItems.recipesDialog.setCurrSelecting(
                content,
                if (Core.input.keyDown(TooManyItems.binds.hotKey))
                  if (content.item is Block && TooManyItems.recipesManager.getRecipesByFactory(content).any()) FACTORY
                  else USAGE
                else RECIPE
              )
            }
            else {
              clicked++
              TooManyItems.recipesDialog.setCurrSelecting(
                content,
                if (clicked%2 == 0)
                  if (content.item is Block && TooManyItems.recipesManager.getRecipesByFactory(content).any()) FACTORY
                  else USAGE
                else RECIPE
              )
            }
          }
          else {
            if (content.hasDetails() && progress >= 0.95f) {
              content.displayDetails()
            }
          }
        }

        update {
          alpha = Mathf.lerpDelta(alpha, (if (currentSelect === content || touched || activity) 1 else 0).toFloat(), 0.08f)
          progress = Mathf.approachDelta(progress, (if (content.hasDetails() && touched) 1 else 0).toFloat(), 1/60f)
          if (clicked > 0 && Time.globalTime - time > 12) clicked = 0
        }
        add(object : Element() {
          var elemWidth: Float = 0f
          var elemHeight: Float = 0f

          init {
            val layout = GlyphLayout.obtain()
            layout.setText(Fonts.outline, content.localizedName())

            elemWidth = layout.width*Scl.scl()
            elemHeight = layout.height*Scl.scl()

            layout.free()
          }

          override fun draw() {
            super.draw()

            val backWidth = elemWidth + Scl.scl(12f)
            val backHeight = height
            Draw.color(Color.lightGray, 0.25f*alpha)
            Fill.rect(x + width/2, y + height/2, backWidth*progress, backHeight)

            Fonts.outline.draw(
              content.localizedName(), x + width/2, y + backHeight/2 + elemHeight/2, Tmp.c1.set(
                Color.white
              ).a(alpha), 1f, false, Align.center
            )
          }
        }).height(35f)
        row()

        if (content.locked()) {
          stack(
            Image(content.icon()).setScaling(Scaling.fit),
            Table { t ->
              t.right().bottom().defaults().right().bottom().pad(4f)
              t.image(Icon.lock).scaling(Scaling.fit).size(10f).color(Color.lightGray)
            }
          ).grow().padBottom(10f)
        }
        else {
          image(content.icon()).scaling(Scaling.fit).grow().padBottom(10f)
        }
      }

      override fun drawBackground(x: Float, y: Float) {
        if (currentSelect === content) {
          Draw.color(Color.darkGray, parentAlpha)
          Fill.rect(x + width/2, y + height/2, width, height)
        }
        else if (activity) {
          Draw.color(Color.lightGray, parentAlpha)
          Lines.stroke(4f)
          Lines.line(x + 8, y + 2, x + width - 8, y + 2)
        }
        else super.drawBackground(x, y)
      }
    })
  }

  fun setCurrSelecting(content: RecipeItem<*>?, mode: Mode) {
    if (_currentSelect == content && mode == recipeMode) return
    val old = _currentSelect
    val oldMode = recipeMode

    _currentSelect = content
    recipeMode = mode
    if (_currentSelect == null) return
    if (!buildRecipes()) {
      _currentSelect = old
      recipeMode = oldMode

      Vars.ui.showInfoFade(Core.bundle["dialog.recipes.no_" + (if (mode == RECIPE) "recipe" else "usage")])
    }
  }

  fun show(content: RecipeItem<*>?) {
    recipeMode = null
    currentSelect = content
    currPage = -1
    show()
  }

  data class Sorting (
    val localized: String,
    val icon: Drawable,
    val sort: Comparator<RecipeItem<*>>
  )

  enum class Mode {
    USAGE {
      override fun icon(): Drawable? {
        return Icon.info
      }
    },
    RECIPE {
      override fun icon(): Drawable? {
        return Icon.tree
      }
    },
    FACTORY {
      override fun icon(): Drawable? {
        return Icon.production
      }
    };

    fun localized(): String {
      return Core.bundle["dialog.recipes.mode_${name.lowercase()}"]
    }

    abstract fun icon(): Drawable?
  }
}

private fun pageSlider(counts: Intp): SliderStyle {
  return object : SliderStyle() {
    init {
      background = object : BaseDrawable() {
        init {
          minHeight = 40f
        }

        override fun draw(x: Float, y: Float, width: Float, height: Float) {
          Lines.stroke(Scl.scl(4f), Color.lightGray)
          Lines.line(x, y + height/2, x + width, y + height/2)

          val n = counts.get() - 1
          val step = counts.get()/10 + 1
          var i = 0
          while (i < n) {
            Lines.line(x + width/n*i, y + height/2, x + width/n*i, y + height/2 + Scl.scl(8f))
            i += step
          }
          Lines.line(x + width, y + height/2, x + width, y + height/2 + Scl.scl(8f))
        }
      }
      knob = object : BaseDrawable() {
        init {
          minHeight = 30f
        }

        override fun draw(x: Float, y: Float, width: Float, height: Float) {
          Draw.color(Color.lightGray)
          Fill.circle(x + width/2, y + height/2, Scl.scl(12f))
        }
      }
      knobOver = object : BaseDrawable() {
        init {
          minHeight = 30f
        }

        override fun draw(x: Float, y: Float, width: Float, height: Float) {
          Draw.color(Pal.accent)
          Fill.circle(x + width/2, y + height/2, Scl.scl(12f))
        }
      }
      knobDown = object : BaseDrawable() {
        init {
          minHeight = 30f
        }

        override fun draw(x: Float, y: Float, width: Float, height: Float) {
          Draw.color(Color.white)
          Fill.circle(x + width/2, y + height/2, Scl.scl(12f))
        }
      }
    }
  }
}
