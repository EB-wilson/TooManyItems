package tmi.ui

import arc.Core
import arc.func.Cons
import arc.func.Cons3
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.input.KeyCode
import arc.math.Mathf
import arc.scene.Element
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
import mindustry.gen.Tex
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.recipe.RecipeItemStack
import tmi.util.Shapes

val NODE_SIZE: Float = Scl.scl(80f)

/**在[tmi.recipe.RecipeType]进行布局时所操作的元素对象，用于显示单个条目信息和提供控制逻辑 */
class RecipeNode(
  val type: NodeType,
  val stack: RecipeItemStack<*>,
  val clickListener: Cons3<RecipeItemStack<*>, NodeType, RecipesDialog.Mode>? = null
) : Button() {
  private var lastTouchedTime = 0f
  private var progress: Float = 0f
  private var alpha: Float = 0f
  private var clicked = 0
  var activity: Boolean = false
  var touched: Boolean = false

  init {
    background = Tex.button
    touchable = Touchable.enabled

    defaults().padLeft(8f).padRight(8f)

    setSize(NODE_SIZE)

    addListener(object : Tooltip(Cons { t: Table -> t.add(stack.item.localizedName, Styles.outlineLabel) }) {
      init {
        allowMobile = true
      }
    })

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

        if (stack.item.hasDetails && (progress >= 0.95f || click == null)) {
          stack.item.displayDetails()
          progress = 0f

          return
        }

        if (clickListener != null && Time.globalTime - lastTouchedTime < 12) {
          if (!Vars.mobile || Core.settings.getBool("keyboard")) {
            clickListener.get(
              stack,
              type,
              if (button == KeyCode.mouseRight)
                if (type == NodeType.BLOCK) RecipesDialog.Mode.FACTORY
                else RecipesDialog.Mode.USAGE
              else RecipesDialog.Mode.RECIPE
            )
          }
          else {
            clicked++
            if (clicked >= 2) {
              clickListener.get(
                stack,
                type,
                if (type == NodeType.BLOCK) RecipesDialog.Mode.FACTORY
                else RecipesDialog.Mode.USAGE
              )
              clicked = 0
            }
          }
        }
      }
    })

    stack(
      Table { it.image(stack.item.icon).size(NODE_SIZE/Scl.scl()*0.62f).scaling(Scaling.fit) },

      Table {
        var last = false

        it.left().bottom()
        it.add(stack.getAmount(), Styles.outlineLabel)
          .apply {
            if (stack.alternativeFormat != null) update{ l ->
              val isDown = Core.input.keyDown(TooManyItems.binds.hotKey)
              if (last != isDown){
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
        it.right().bottom().defaults().right().bottom().pad(4f)
        it.image(Icon.lock).scaling(Scaling.fit).size(10f).color(Color.lightGray)
      }
    ).grow().pad(5f)
  }

  override fun act(delta: Float) {
    super.act(delta)
    alpha = Mathf.lerpDelta(alpha, (if (touched || activity) 1 else 0).toFloat(), 0.08f)
    progress = Mathf.approachDelta(progress, if (stack.item.hasDetails && click != null && touched) 1f else 0f, 1/60f)

    if (clickListener != null && Time.globalTime - lastTouchedTime > 12 && clicked == 1) {
      clickListener.get(stack, type, RecipesDialog.Mode.RECIPE)
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
