package tmi.ui

import arc.func.Boolf
import arc.func.Cons
import arc.func.Cons2
import arc.input.KeyCode
import arc.scene.Element
import arc.scene.event.SceneEvent
import arc.scene.style.Drawable
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Button
import arc.scene.ui.Tooltip
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Align
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.recipe.types.RecipeItem
import tmi.ui.calculator.CalculatorDialog
import tmi.util.invoke

object TmiUI {
  @JvmStatic
  val recipesDialog by lazy { RecipesDialog() }
  @JvmStatic
  val recipeGraph by lazy { CalculatorDialog() }
  @JvmStatic
  val document by lazy { DocumentDialog() }

  fun init() {
    recipesDialog.build()
    recipeGraph.build()
  }

  @JvmStatic
  fun showChoice(title: String, text: String, closeButton: Boolean = true, vararg options: Pair<String, Runnable>) {
    showChoiceIcons(title, text, closeButton, *options.map { Pair(it.first, null) to it.second  }.toTypedArray() )
  }

  @JvmStatic
  fun showChoiceIcons(title: String, text: String, closeButton: Boolean = true, vararg options: Pair<Pair<String, Drawable?>, Runnable>) {
    val dialog = BaseDialog(title)
    dialog.cont.add(text).width(if (Vars.mobile) 400f else 500f).wrap().pad(4f).get()
      .setAlignment(Align.center, Align.center)
    dialog.buttons.defaults().size(200f, 54f).pad(2f)
    dialog.setFillParent(false)

    options.forEach {
      if (it.first.second == null){
        dialog.buttons.button(it.first.first) {
          dialog.hide()
          it.second.run()
        }
      }
      else {
        dialog.buttons.button(it.first.first, it.first.second) {
          dialog.hide()
          it.second.run()
        }
      }
    }
    if (closeButton) dialog.buttons.button("@cancel", Icon.cancel) { dialog.hide() }

    dialog.keyDown(KeyCode.escape) { dialog.hide() }
    dialog.keyDown(KeyCode.back) { dialog.hide() }
    dialog.show()
  }
}

fun Element.addEventBlocker(
  capture: Boolean = false,
  isCancel: Boolean = false,
  filter: Boolf<SceneEvent> = Boolf{ true }
){
  (this::addCaptureListener.takeIf{ capture }?: this::addListener){ event ->
    if (event != null && filter.get(event)) {
      if (isCancel) event.cancel()
      else event.stop()
    }
    false
  }
}

