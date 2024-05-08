package tmi.ui

import arc.Core
import arc.func.Boolp
import arc.graphics.Color
import arc.input.KeyCode
import arc.scene.Element
import arc.scene.event.*
import arc.scene.ui.*
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Stack
import arc.scene.ui.layout.Table
import arc.util.Align
import arc.util.Time
import mindustry.Vars
import mindustry.ctype.UnlockableContent
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.ContentInfoDialog
import tmi.TooManyItems
import tmi.util.Consts

object EntryAssigner {
  private var tmiEntry: ImageButton? = null

  fun assign() {
    run {
      //hot key bind
      val pane = Vars.ui.controls.cont.children.find { e: Element? -> e is ScrollPane } as ScrollPane
      val stack = pane.widget as Stack
      val table = stack.children[0] as Table
      table.removeChild(table.children[table.children.size - 1])

      table.row()
      table.add(Core.bundle["dialog.recipes.title"]).color(Color.gray).colspan(4).pad(10f).padBottom(4f).row()
      table.image().color(Color.gray).fillX().height(3f).pad(6f).colspan(4).padTop(0f).padBottom(10f).row()

      table.add(Core.bundle["keybind.tmi.name"], Color.white).left().padRight(40f).padLeft(8f)
      table.label { TooManyItems.binds.hotKey.toString() }.color(Pal.accent).left().minWidth(90f).padRight(20f)

      table.button("@settings.rebind", Styles.defaultt) { openDialog() }.width(130f)
      table.button("@settings.resetKey", Styles.defaultt) { TooManyItems.binds.reset("hot_key") }.width(130f).pad(2f)
        .padLeft(4f)
      table.row()
      table.button("@settings.reset") {
        Core.keybinds.resetToDefaults()
        TooManyItems.binds.resetAll()
      }.colspan(4).padTop(4f).fill()
    }

    run { //content information entry
      Vars.ui.database.buttons.button(Core.bundle["recipes.open"], Consts.tmi, 38f) {
        TooManyItems.recipesDialog.currentSelect = null
        TooManyItems.recipesDialog.show()
      }
    }

    run { //database entry
      Vars.ui.content = object : ContentInfoDialog() {
        override fun show(content: UnlockableContent) {
          super.show(content)
          val rec = TooManyItems.itemsManager.getItem(content)
          if (!TooManyItems.recipesManager.anyRecipe(rec)) return

          val pane = Vars.ui.content.cont.children[0]
          if (pane is ScrollPane) {
            val ta = pane.widget as Table
            val t = ta.children[0] as Table

            t.button(Consts.tmi, Styles.clearNonei, 38f) {
              TooManyItems.recipesDialog.show(TooManyItems.itemsManager.getItem(content))
              hide()
            }.padLeft(12f).margin(6f)
          }
        }
      }
    }

    run { //HUD entry
      Core.scene.root.addChild(object : ImageButton(Consts.tmi, Styles.cleari) {
        init {
          tmiEntry = this
          setSize(Scl.scl(60f))

          visibility = Boolp { Core.settings.getBool("tmi_button", true) }

          setPosition(
            Core.settings.getFloat("tmi_button_x", 0f),
            Core.settings.getFloat("tmi_button_y", 0f),
            Align.bottomLeft
          )

          addCaptureListener(object : DragListener() {
            init {
              button = KeyCode.mouseLeft.ordinal
            }

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
              return touchDown(event, x, y, pointer, button.ordinal)
            }

            override fun drag(event: InputEvent, mx: Float, my: Float, pointer: Int) {
              if (Core.app.isMobile && pointer != 0) return

              setPosition(x + mx, y + my, Align.center)
            }

            override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
              if (!isDragging) TooManyItems.recipesDialog.show()
              else {
                Core.settings.put("tmi_button_x", tmiEntry!!.x)
                Core.settings.put("tmi_button_y", tmiEntry!!.y)
              }
              super.touchUp(event, x, y, pointer, button.ordinal)
            }
          })

          Vars.ui.hudGroup.addChild(tmiEntry)
        }
      })
    }

    //preview switch
    run {
      Vars.ui.settings.game.checkPref("tmi_enable_preview", false)
    }
  }

  private fun openDialog() {
    val rebindDialog = Dialog(Core.bundle["keybind.press"])

    rebindDialog.titleTable.cells.first().pad(4f)

    rebindDialog.addListener(object : InputListener() {
      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
        if (Core.app.isAndroid) return false
        TooManyItems.binds.hotKey = button
        TooManyItems.binds.save()
        return false
      }

      override fun keyDown(event: InputEvent, keycode: KeyCode): Boolean {
        rebindDialog.hide()
        if (keycode == KeyCode.escape) return false
        TooManyItems.binds.hotKey = keycode
        TooManyItems.binds.save()
        return false
      }
    })

    rebindDialog.show()
    Time.runTask(1f) { Core.scene.setScrollFocus(rebindDialog) }
  }
}
