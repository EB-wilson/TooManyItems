package tmi.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.ItemDisplay;
import mindustry.ui.ItemImage;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;
import tmi.TooManyItems;
import tmi.recipe.RecipeItemStack;

import static mindustry.Vars.mobile;
import static tmi.TooManyItems.binds;

/**在{@link tmi.recipe.RecipeType}进行布局时所操作的元素对象，用于显示单个条目信息和提供控制逻辑*/
public class RecipeNode extends Button {
  public static final float SIZE = Scl.scl(80);

  public final RecipeItemStack stack;

  public boolean isMaterial, isProduction, isBlock;

  float progress, alpha;
  boolean activity, touched;
  float time;
  int clicked;

  public RecipeNode(RecipeItemStack stack){
    setBackground(Tex.button);
    this.stack = stack;

    touchable = Touchable.enabled;

    defaults().padLeft(8).padRight(8);

    setSize(SIZE);

    addListener(new Tooltip(t -> t.add(stack.content().localizedName, Styles.outlineLabel)){{ allowMobile = true; }});
    hovered(() -> activity = true);
    exited(() -> activity = false);
    tapped(() -> {
      touched = true;
      time = Time.time;
    });
    released(() -> {
      touched = false;

      if (Time.time - time < 12){
        if (!mobile || Core.settings.getBool("keyboard")) {
          TooManyItems.recipesDialog.setCurrSelecting(stack.content(), Core.input.keyDown(binds.hotKey) ? isBlock ? RecipesDialog.Mode.factory : RecipesDialog.Mode.usage : RecipesDialog.Mode.recipe);
        }
        else {
          clicked++;
          if (clicked >= 2){
            TooManyItems.recipesDialog.setCurrSelecting(stack.content(), isBlock ? RecipesDialog.Mode.factory : RecipesDialog.Mode.usage);
            clicked = 0;
          }
        }
      }
      else {
        if (progress >= 0.95f){
          Vars.ui.content.show(stack.content());
        }
      }
    });

    update(() -> {
      alpha = Mathf.lerpDelta(alpha, touched || activity ? 1 : 0, 0.08f);
      progress = Mathf.approachDelta(progress, touched? 1 : 0, 1/60f);
      if (Time.time - time > 12 && clicked == 1){
        TooManyItems.recipesDialog.setCurrSelecting(stack.content(), RecipesDialog.Mode.recipe);
        clicked = 0;
      }
    });

    stack(
        new Table(t -> t.add(new Table(o -> {
          o.add(new Image(stack.content.uiIcon)).size(SIZE/2).scaling(Scaling.fit);
        })).grow()),

        new Table(t -> t.add(new Table(ta -> {
          ta.left().bottom();
          ta.add(stack.getAmount(), Styles.outlineLabel);
          ta.pack();
        })).grow()),

        new Table(t -> {
          if (stack.content.unlockedNow()) return;
          t.right().bottom().defaults().right().bottom().pad(4);
          t.image(Icon.lock).scaling(Scaling.fit).size(10).color(Color.lightGray);
        })
    ).grow().pad(5);
  }

  @Override
  protected void drawBackground(float x, float y) {
    super.drawBackground(x, y);
    Lines.stroke(Scl.scl(32), Color.lightGray);
    Draw.alpha(0.5f);

    Lines.arc(x + width/2, y + height/2, Scl.scl(18), progress, 90);
  }
}
