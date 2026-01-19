package tmi.ui

import arc.Core
import arc.func.Cons
import arc.func.Cons4
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.input.KeyCode
import arc.math.Mathf
import arc.scene.Element
import arc.scene.event.HandCursorListener
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.ui.Button
import arc.scene.ui.Tooltip
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Interval
import arc.util.Scaling
import arc.util.Time
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Sounds.click
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.TooManyItems
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.util.Consts
import tmi.util.Shapes

/**在[tmi.recipe.RecipeType]进行布局时所操作的元素对象，用于显示单个条目信息和提供控制逻辑 */
class RecipeItemCell(
  val type: CellType,
  vararg val groupItems: RecipeItemStack<*>,
  val clickListener: Cons4<RecipeItemCell, RecipeItemStack<*>, CellType, RecipesDialog.Mode>? = null,
) : Button() {
  private var lastTouchedTime = 0f
  private var progress: Float = 0f
  private var alpha: Float = 0f
  private var clicked = 0
  private var itemIndex = 0
  private var lockedItem: RecipeItem<*>? = null

  private var timer = 0f

  var activity: Boolean = false
  var touched: Boolean = false

  init {
    touchable = Touchable.enabled
    rebuild()

    addListener(Tooltip { t ->
      t.add("", Styles.outlineLabel).update {
        val stack = currentItem()
        it.setText(stack.item.localizedName)
      }
    }.apply {
      allowMobile = true
    })

    addListener(HandCursorListener())

    addListener(object : InputListener() {
      override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        super.enter(event, x, y, pointer, fromActor)
        activity = true
      }

      override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?) {
        super.exit(event, x, y, pointer, toActor)
        activity = false
      }

      override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?): Boolean {
        if (pointer != 0 && button != KeyCode.mouseLeft && button != KeyCode.mouseRight) return false
        lastTouchedTime = Time.globalTime
        touched = true
        activity = true
        return true
      }

      override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: KeyCode?) {
        if (pointer != 0 && button != KeyCode.mouseLeft && button != KeyCode.mouseRight) return
        touched = false
        activity = false

        val stack = currentItem()
        if (stack.item.hasDetails && (progress >= 0.95f || click == null)) {
          stack.item.displayDetails()
          progress = 0f

          return
        }

        if (clickListener != null && Time.globalTime - lastTouchedTime < 12) {
          if (!Vars.mobile || Core.settings.getBool("keyboard")) {
            if (Core.input.keyDown(TooManyItems.binds.hotKey)) clickListener.get(
              this@RecipeItemCell,
              currentItem(),
              type,
              if (button == KeyCode.mouseRight)
                if (type == CellType.BLOCK) RecipesDialog.Mode.FACTORY
                else RecipesDialog.Mode.USAGE
              else RecipesDialog.Mode.RECIPE
            )
            else showSelector { stack ->
              clickListener.get(
                this@RecipeItemCell,
                stack,
                type,
                if (button == KeyCode.mouseRight)
                  if (type == CellType.BLOCK) RecipesDialog.Mode.FACTORY
                  else RecipesDialog.Mode.USAGE
                else RecipesDialog.Mode.RECIPE
              )
            }
          }
          else {
            clicked++
            if (clicked >= 2) {
              if (Core.input.keyDown(TooManyItems.binds.hotKey)) clickListener.get(
                this@RecipeItemCell,
                currentItem(),
                type,
                RecipesDialog.Mode.USAGE
              )
              else showSelector { stack ->
                clickListener.get(
                  this@RecipeItemCell,
                  stack,
                  type,
                  if (type == CellType.BLOCK) RecipesDialog.Mode.FACTORY
                  else RecipesDialog.Mode.USAGE
                )
              }
              clicked = 0
            }
          }
        }
      }
    })
  }

  fun rebuild() {
    clearChildren()

    val stack = groupItems[itemIndex]
    buildButton(this, stack)

    fill {
      it.top().left().add("*", Styles.outlineLabel).size(10f).fontScale(1.2f)
        .pad(12f).padTop(18f)
      it.visible { groupItems.size > 1 }
    }
  }

  private fun buildButton(table: Table, stack: RecipeItemStack<*>) {
    table.stack(
      Table {
        it.image(stack.item.icon).grow().scaling(Scaling.fit)
      },
      Table {
        it.left().bottom()
        it.add(stack.getAmount(), Styles.outlineLabel)
          .apply {
            var last = false
            if (stack.alternativeFormat != null) update { l ->
              val isDown = Core.input.keyDown(TooManyItems.binds.hotKey)
              if (last != isDown) {
                l.setText(
                  if (isDown && stack.alternativeFormat != null) stack.alternativeFormat!!.format(stack.amount)
                  else stack.getAmount()
                )
                last = isDown
              }
            }
          }
        it.pack()
      },
      Table {
        if (!stack.item.locked) return@Table
        it.right().top().defaults().right().bottom().pad(4f)
        it.image(Icon.lock).scaling(Scaling.fit).size(10f).color(Color.lightGray)
      }
    ).grow().pad(5f)
  }

  private fun showSelector(callback: Cons<RecipeItemStack<*>>){
    if (groupItems.size == 1) callback.get(groupItems.first())
    else BaseDialog("").apply {
      title.clear()

      cont.table(Consts.grayUIAlpha) { pane ->
        pane.add(Core.bundle["dialog.calculator.groupSelect"]).color(Pal.accent).pad(24f)
        pane.row()
        pane.table(Consts.darkGrayUIAlpha) { items ->
          items.top().left().pane(Styles.smallPane) { pane ->
            groupItems.forEachIndexed { i, stack ->
              if (i > 0 && i % 8 == 0) pane.row()

              pane.button({ button -> buildButton(button, stack)}){
                callback.get(stack)
                hide()
              }.size(80f).pad(6f).get().addListener(Tooltip { t ->
                t.add(stack.item.localizedName, Styles.outlineLabel)
              }.apply {
                allowMobile = true
              })
            }
          }.fill()
        }.grow().pad(6f)
      }.fill().minWidth(92*8f).height(92*4f)
      cont.row()
      cont.button(Core.bundle["misc.cancel"], Icon.cancel, Styles.flatt) { this.hide() }
        .size(210f, 64f).pad(8f).margin(12f)

      show()
    }
  }

  fun setLockedItem(item: RecipeItem<*>) {
    itemIndex = groupItems.indexOfFirst { it.item == lockedItem }
    if (itemIndex < 0) return
    lockedItem = item
  }

  fun resetLockedItem(){
    lockedItem = null
    itemIndex = 0
  }

  fun currentItem() = groupItems[itemIndex]
  fun getItems() = groupItems.toList()

  override fun act(delta: Float) {
    super.act(delta)

    if (groupItems.size > 1 && lockedItem == null && !Core.input.keyDown(TooManyItems.binds.hotKey)){
      timer += delta
    }

    if (timer >= 1){
      timer = 0f
      itemIndex++
      itemIndex %= groupItems.size

      rebuild()
    }

    val stack = groupItems[itemIndex]
    alpha = Mathf.lerpDelta(alpha, (if (touched || activity) 1 else 0).toFloat(), 0.08f)
    progress = Mathf.approachDelta(progress, if (stack.item.hasDetails && click != null && touched) 1f else 0f, 1/60f)

    if (clickListener != null && Time.globalTime - lastTouchedTime > 12 && clicked == 1) {
      if (Core.input.keyDown(TooManyItems.binds.hotKey)) clickListener.get(
        this,
        currentItem(),
        type,
        RecipesDialog.Mode.RECIPE
      )
      else showSelector { stack ->
        clickListener.get(
          this,
          stack,
          type,
          RecipesDialog.Mode.RECIPE
        )
      }
      clicked = 0
    }
  }

  override fun drawBackground(x: Float, y: Float) {
    super.drawBackground(x, y)
    Draw.color(Color.lightGray)
    Draw.alpha(0.5f)

    Shapes.fan(x + width/2, y + height/2, Scl.scl(32f), -progress*360, 90f)
  }
}
