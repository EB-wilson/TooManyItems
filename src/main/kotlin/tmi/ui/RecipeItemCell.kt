package tmi.ui

import arc.Core
import arc.func.Cons
import arc.func.Floatp
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
import arc.util.Scaling
import arc.util.Time
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Sounds.click
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.TooManyItems
import tmi.recipe.AmountFormatter
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.util.Consts
import tmi.util.Shapes

/**在[tmi.recipe.RecipeType]进行布局时所操作的元素对象，用于显示单个条目信息和提供控制逻辑 */
class RecipeItemCell(
  val type: CellType,
  vararg val groupItems: RecipeItemStack<*>,
  val clickListener: (RecipeItemCell.(RecipeItemStack<*>, CellType, RecipesDialog.Mode) -> Unit)? = null,
) : Button() {
  private var lastTouchedTime = 0f
  private var progress: Float = 0f
  private var alpha: Float = 0f
  private var clicked = 0
  private var itemIndex = 0
  private var timer = 0f
  private var fontScl = 1f

  private var normalFormat: AmountFormatter? = null
  private var updateText = false

  var amountMultiplier: Floatp = Floatp { 1f }
    private set
  var chosenItem: RecipeItem<*>? = null
    private set

  var activity: Boolean = false
  var touched: Boolean = false

  init {
    touchable = if (clickListener == null) Touchable.disabled else Touchable.enabled
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
            if (Core.input.keyDown(TooManyItems.binds.hotKey) || chosenItem != null) clickListener(
              this@RecipeItemCell,
              currentItem(),
              type,
              if (button == KeyCode.mouseRight)
                if (type == CellType.BLOCK) RecipesDialog.Mode.FACTORY
                else RecipesDialog.Mode.USAGE
              else RecipesDialog.Mode.RECIPE
            )
            else showSelector { stack ->
              clickListener(
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
              if (Core.input.keyDown(TooManyItems.binds.hotKey) || chosenItem != null) clickListener(
                this@RecipeItemCell,
                currentItem(),
                type,
                RecipesDialog.Mode.USAGE
              )
              else showSelector { stack ->
                clickListener(
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
    buildButton(this, stack, fontScl)

    fill {
      it.top().left().add("*", Styles.outlineLabel).size(10f).fontScale(1.2f)
        .pad(12f).padTop(18f)
      it.visible { groupItems.size > 1 }
    }
  }

  private fun buildButton(table: Table, stack: RecipeItemStack<*>, fontScale: Float) {
    table.stack(
      Table {
        it.image(stack.item.icon).grow().scaling(Scaling.fit)
      },
      Table {
        it.left().bottom()
        it.add((normalFormat?:stack.amountFormat).format(stack.amount*amountMultiplier.get()), Styles.outlineLabel)
          .apply {
            var last = false
            update { l ->
              val isDown = Core.input.keyDown(TooManyItems.binds.hotKey)
              if (last != isDown || updateText) {
                l.setText(
                  if (isDown && stack.alternativeFormat != null) stack.alternativeFormat!!.format(stack.amount*amountMultiplier.get())
                  else (normalFormat?:stack.amountFormat).format(stack.amount*amountMultiplier.get())
                )
                updateText = false
                last = isDown
              }
            }
            fontScale(fontScale)
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

              pane.button({ button -> buildButton(button, stack, 1.0f)}){
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

  fun setMultiplier(multiplier: Floatp) = also{
    amountMultiplier = multiplier
  }

  fun setFontScl(fontScl: Float) = also{
    this.fontScl = fontScl
    rebuild()
  }

  fun updateText() = also{
    updateText = true
  }

  fun setFormatter(formatter: AmountFormatter) = also{
    normalFormat = formatter
    updateText = true
  }

  fun setChosenItem(item: RecipeItem<*>) = also {
    val index = groupItems.indexOfFirst { it.item == item }
    if (itemIndex < 0) return this
    itemIndex = index
    chosenItem = item

    rebuild()
  }

  fun resetLockedItem() = also{
    chosenItem = null
    itemIndex = 0

    rebuild()
  }

  fun currentItem() = groupItems[itemIndex]
  fun getItems() = groupItems.toList()

  override fun act(delta: Float) {
    super.act(delta)

    if (groupItems.size > 1 && chosenItem == null && !Core.input.keyDown(TooManyItems.binds.hotKey)){
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
      if (Core.input.keyDown(TooManyItems.binds.hotKey) || chosenItem != null) clickListener(
        this,
        currentItem(),
        type,
        RecipesDialog.Mode.RECIPE
      )
      else showSelector { stack ->
        clickListener(
          this,
          stack,
          type,
          RecipesDialog.Mode.RECIPE
        )
      }
      clicked = 0
    }
  }

  override fun invalidateHierarchy() {
    invalidate()
  }

  override fun drawBackground(x: Float, y: Float) {
    super.drawBackground(x, y)
    Draw.color(Color.lightGray)
    Draw.alpha(0.5f)

    Shapes.fan(x + width/2, y + height/2, Scl.scl(32f), -progress*360, 90f)
  }
}
