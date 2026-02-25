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

  fun buildItems(
    items: Table,
    list: Seq<RecipeItem<*>>,
    buttonAction: Cons2<RecipeItem<*>, Button>? = null,
    callBack: Cons<RecipeItem<*>>
  ) {
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
        i = (i + 1)%recipesDialog.sortings.size
        rebuild()
      }.margin(2f).update { b -> b.style.imageUp = recipesDialog.sortings[i].icon }.get()
        .addListener(Tooltip{ t ->
          t.table(Tex.paneLeft){
            it.add("").update { l -> l.setText(recipesDialog.sortings[i].localized) }
          }
        }.also { it.allowMobile = true })
      top.button(Icon.none, Styles.clearNonei, 36f) {
        reverse = !reverse
        rebuild()
      }.margin(2f).update { b -> b.style.imageUp = if (reverse) Icon.up else Icon.down }
    }.growX()
    items.row()

    items.table{ pane ->
      pane.left().top().pane(Styles.smallPane) { cont ->
        rebuild = {
          cont.clearChildren()
          var ind = 0

          val sorting = recipesDialog.sortings[i].sort
          val ls: List<RecipeItem<*>> = list.toList()
            .filter { e -> (e.name.contains(search) || e.localizedName.contains(search)) }
            .sortedWith(
              if (reverse) java.util.Comparator { a, b -> sorting.compare(b, a) }
              else sorting
            )

          ls.forEach { item ->
            if (item.locked || item.hidden) return@forEach

            val b = cont.button(TextureRegionDrawable(item.icon), Styles.clearNonei, 32f) {
              callBack[item]
            }.margin(4f).tooltip(item.localizedName).get()
            buttonAction?.invoke(item, b)

            if (ind++%8 == 7) {
              cont.row()
            }
          }
        }
        rebuild()
      }.padTop(6f).padBottom(4f).fill()
    }.height(400f).growX()
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

    if (closeButton) dialog.buttons.button("@cancel", Icon.cancel) { dialog.hide() }
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

