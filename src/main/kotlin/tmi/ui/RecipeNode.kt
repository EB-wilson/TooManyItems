package tmi.ui

import arc.Core
import arc.func.Cons
import arc.func.Cons3
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.math.Mathf
import arc.scene.event.Touchable
import arc.scene.ui.Button
import arc.scene.ui.Tooltip
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Scaling
import arc.util.Time
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.ui.Styles
import tmi.TooManyItems
import tmi.recipe.RecipeItemStack

val NODE_SIZE: Float = Scl.scl(80f)

/**在[tmi.recipe.RecipeType]进行布局时所操作的元素对象，用于显示单个条目信息和提供控制逻辑 */
class RecipeNode(
  val type: NodeType,
  val stack: RecipeItemStack,
  private var click: Cons3<RecipeItemStack, NodeType, RecipesDialog.Mode>?
) : Button() {

  private var progress: Float = 0f
  private var alpha: Float = 0f
  private var activity: Boolean = false
  private var touched: Boolean = false
  private var clicked: Int = 0
  private var time: Float = 0f

  init {
    background = Tex.button
    touchable = Touchable.enabled

    defaults().padLeft(8f).padRight(8f)

    setSize(NODE_SIZE)

    addListener(object : Tooltip(Cons { t: Table -> t.add(stack.item().localizedName(), Styles.outlineLabel) }) {
      init {
        allowMobile = true
      }
    })

    hovered { activity = true }
    exited { activity = false }
    tapped {
      touched = true
      time = Time.globalTime
    }
    released {
      touched = false
      if (click != null && Time.globalTime - time < 12) {
        if (!Vars.mobile || Core.settings.getBool("keyboard")) {
          click!![stack, type, if (Core.input.keyDown(TooManyItems.binds.hotKey)) if (type == NodeType.BLOCK) RecipesDialog.Mode.FACTORY else RecipesDialog.Mode.USAGE else RecipesDialog.Mode.RECIPE]
        }
        else {
          clicked++
          if (clicked >= 2) {
            click!![stack, type, if (type == NodeType.BLOCK) RecipesDialog.Mode.FACTORY else RecipesDialog.Mode.USAGE]
            clicked = 0
          }
        }
      }
      else {
        if (stack.item.hasDetails() && (progress >= 0.95f || click == null)) {
          stack.item.displayDetails()
        }
      }
    }

    stack(
      Table { t: Table -> t.image(stack.item.icon()).size(NODE_SIZE/Scl.scl()*0.62f).scaling(Scaling.fit) },

      Table { t: Table ->
        t.left().bottom()
        t.add(stack.getAmount(), Styles.outlineLabel)
        t.pack()
      },

      Table(Cons { t: Table ->
        if (!stack.item.locked()) return@Cons
        t.right().bottom().defaults().right().bottom().pad(4f)
        t.image(Icon.lock).scaling(Scaling.fit).size(10f).color(Color.lightGray)
      })
    ).grow().pad(5f)
  }

  override fun act(delta: Float) {
    super.act(delta)

    alpha = Mathf.lerpDelta(alpha, (if (touched || activity) 1 else 0).toFloat(), 0.08f)
    progress =
      Mathf.approachDelta(progress, if (stack.item.hasDetails() && click != null && touched) 1.01f else 0f, 1/60f)
    if (click != null && Time.globalTime - time > 12 && clicked == 1) {
      click!![stack, type, RecipesDialog.Mode.RECIPE]
      clicked = 0
    }
  }

  override fun drawBackground(x: Float, y: Float) {
    super.drawBackground(x, y)
    Lines.stroke(Scl.scl(34f), Color.lightGray)
    Draw.alpha(0.5f)

    Lines.arc(x + width/2, y + height/2, Scl.scl(18f), progress, 90f)
  }
}
