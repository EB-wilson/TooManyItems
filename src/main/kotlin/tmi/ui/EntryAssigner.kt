package tmi.ui

import arc.Core
import arc.func.Boolp
import arc.func.Cons
import arc.func.Prov
import arc.graphics.Color
import arc.input.KeyCode
import arc.scene.event.DragListener
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.ui.Dialog
import arc.scene.ui.ImageButton
import arc.scene.ui.ScrollPane
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.util.Align
import arc.util.Time
import mindustry.Vars
import mindustry.ctype.UnlockableContent
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.ContentInfoDialog
import tmi.TooManyItems
import tmi.util.invoke
import tmi.util.CombinedKeys
import tmi.util.Consts

object EntryAssigner {
  lateinit var tmiEntry: ImageButton
    private set

  fun assign() {
    run { //content information entry
      Vars.ui.database.buttons.button(Core.bundle["recipes.open"], Consts.tmi, 38f) {
        TmiUI.recipesDialog.currentSelect = null
        TmiUI.recipesDialog.show()
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
              TmiUI.recipesDialog.showWith {
                setCurrSelecting(TooManyItems.itemsManager.getItem(content))
              }
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

          visibility = Boolp {
            !Core.scene.hasDialog() && Core.settings.getBool("tmi_button", true)
          }

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
              if (!isDragging) TmiUI.recipesDialog.show()
              else {
                Core.settings.put("tmi_button_x", tmiEntry.x)
                Core.settings.put("tmi_button_y", tmiEntry.y)
              }
              super.touchUp(event, x, y, pointer, button.ordinal)
            }
          })
        }
      })
    }

    //preview switch
    run {
      Vars.ui.settings.game.checkPref("tmi_enable_preview", false)
    }
  }

  private fun createKeybindTable(table: Table, name: String, key: Prov<CharSequence>, hotKeyMethod: Cons<Array<KeyCode>>, resetMethod: Runnable, isCombine: Boolean = true) {
    table.add(name, Color.white).left().padRight(40f).padLeft(8f)
    table.label{ key().ifBlank { Core.bundle["misc.requireInput"] } }.color(Pal.accent).left().minWidth(90f).padRight(20f)

    table.button("@settings.rebind", Styles.defaultt) { openDialog(isCombine){ hotKeyMethod(it) } }.width(130f)
    table.button("@settings.resetKey", Styles.defaultt) { resetMethod.run() }.width(130f).pad(2f).padLeft(4f)
    table.row()
  }

  private fun openDialog(isCombine: Boolean, callBack: Cons<Array<KeyCode>>) {
    val rebindDialog = Dialog()
    val res = linkedSetOf<KeyCode>()
    var show = ""

    rebindDialog.cont.table{
      it.add(Core.bundle["misc.pressAnyKeys".takeIf { isCombine }?:"keybind.press"])
        .color(Pal.accent)
      if (!isCombine) return@table

      it.row()
      it.label{ show.ifBlank { Core.bundle["misc.requireInput"] } }
        .padTop(8f)
    }

    rebindDialog.titleTable.cells.first().pad(4f)

    rebindDialog.addListener(object : InputListener() {
      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode): Boolean {
        if (Core.app.isAndroid) {
          rebindDialog.hide()
          return false
        }

        keySet(button)
        return true
      }

      override fun keyDown(event: InputEvent, keycode: KeyCode): Boolean {
        if (keycode == KeyCode.escape) {
          rebindDialog.hide()
          return false
        }

        keySet(keycode)
        return true
      }

      private fun keySet(button: KeyCode) {
        if (!isCombine) {
          callBack(arrayOf(button))
          rebindDialog.hide()
          return
        }

        if (button != KeyCode.enter) {
          res.add(button)
          show = CombinedKeys.toString(res)
        }
        else {
          callBack(res.toTypedArray())
          rebindDialog.hide()
        }
      }

      override fun keyUp(event: InputEvent?, keycode: KeyCode?): Boolean {
        res.remove(keycode)
        show = CombinedKeys.toString(res)

        return true
      }
    })

    rebindDialog.show()
    Time.runTask(1f) { Core.scene.setScrollFocus(rebindDialog) }
  }
}
