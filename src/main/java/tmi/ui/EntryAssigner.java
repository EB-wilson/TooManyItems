package tmi.ui;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.DragListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.scene.utils.Elem;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.ui.fragments.HudFragment;
import mindustry.ui.fragments.PlacementFragment;
import tmi.TooManyItems;
import tmi.recipe.types.RecipeItem;
import tmi.util.Consts;

import java.lang.reflect.Field;

import static arc.Core.*;
import static tmi.TooManyItems.recipesDialog;

public class EntryAssigner {
  private static ImageButton tmiEntry;

  public static void assign(){
    {//hot key bind
      ScrollPane pane = (ScrollPane) Vars.ui.controls.cont.getChildren().find(e -> e instanceof ScrollPane);
      Stack stack = (Stack) pane.getWidget();
      Table table = (Table) stack.getChildren().get(0);
      table.removeChild(table.getChildren().get(table.getChildren().size - 1));

      table.row();
      table.add(bundle.get("dialog.recipes.title")).color(Color.gray).colspan(4).pad(10).padBottom(4).row();
      table.image().color(Color.gray).fillX().height(3).pad(6).colspan(4).padTop(0).padBottom(10).row();

      table.add(bundle.get("keybind.tmi.name"), Color.white).left().padRight(40).padLeft(8);
      table.label(() -> TooManyItems.binds.hotKey.toString()).color(Pal.accent).left().minWidth(90).padRight(20);

      table.button("@settings.rebind", Styles.defaultt, EntryAssigner::openDialog).width(130f);
      table.button("@settings.resetKey", Styles.defaultt, () -> TooManyItems.binds.reset("hot_key")).width(130f).pad(2f).padLeft(4f);
      table.row();

      table.button("@settings.reset", () -> {
        keybinds.resetToDefaults();
        TooManyItems.binds.resetAll();
      }).colspan(4).padTop(4).fill();
    }

    {//content information entry
      Vars.ui.database.buttons.button(Core.bundle.get("recipes.open"), Consts.tmi, 38, () -> {
        recipesDialog.setCurrSelecting(null);
        recipesDialog.show();
      });
    }

    {//database entry
      Vars.ui.content = new ContentInfoDialog() {
        @Override
        public void show(UnlockableContent content) {
          super.show(content);
          RecipeItem<UnlockableContent> rec = TooManyItems.itemsManager.getItem(content);
          if (!TooManyItems.recipesManager.anyRecipe(rec)) return;

          Element pane = Vars.ui.content.cont.getChildren().get(0);
          if (pane instanceof ScrollPane p) {
            Table ta = (Table) p.getWidget();
            Table t = (Table) ta.getChildren().get(0);

            t.button(Consts.tmi, Styles.clearNonei, 38, () -> {
              recipesDialog.show(TooManyItems.itemsManager.getItem(content));
              hide();
            }).padLeft(12).margin(6);
          }
        }
      };
    }

    {//HUD entry
      scene.root.addChild(new ImageButton(Consts.tmi, Styles.cleari){{
        tmiEntry = this;
        tmiEntry.setSize(Scl.scl(60));

        visibility = () -> settings.getBool("tmi_button", true);

        setPosition(settings.getFloat("tmi_button_x", 0), settings.getFloat("tmi_button_y", 0), Align.bottomLeft);

        addCaptureListener(new DragListener(){
          {setButton(KeyCode.mouseLeft.ordinal());}

          @Override
          public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
            return touchDown(event, x, y, pointer, button.ordinal());
          }

          @Override
          public void drag(InputEvent event, float mx, float my, int pointer) {
            if(Core.app.isMobile() && pointer != 0) return;

            setPosition(x + mx, y + my, Align.center);
          }

          @Override
          public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
            if (!isDragging()) recipesDialog.show();
            else {
              settings.put("tmi_button_x", tmiEntry.x);
              settings.put("tmi_button_y", tmiEntry.y);
            }
            super.touchUp(event, x, y, pointer, button.ordinal());
          }
        });

        Vars.ui.hudGroup.addChild(tmiEntry);
      }});
    }

    //preview switch
    {
      Vars.ui.settings.game.checkPref("tmi_enable_preview", false);
    }
  }

  private static void openDialog(){
    Dialog rebindDialog = new Dialog(bundle.get("keybind.press"));

    rebindDialog.titleTable.getCells().first().pad(4);

    rebindDialog.addListener(new InputListener(){
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
        if(Core.app.isAndroid()) return false;
        TooManyItems.binds.hotKey = button;
        TooManyItems.binds.save();
        return false;
      }

      @Override
      public boolean keyDown(InputEvent event, KeyCode keycode){
        rebindDialog.hide();
        if(keycode == KeyCode.escape) return false;
        TooManyItems.binds.hotKey = keycode;
        TooManyItems.binds.save();
        return false;
      }
    });

    rebindDialog.show();
    Time.runTask(1f, () -> scene.setScrollFocus(rebindDialog));
  }
}
