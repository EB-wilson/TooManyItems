package tmi.ui;

import arc.Core;
import arc.func.Cons2;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import arc.util.Time;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import tmi.recipe.RecipeItemStack;
import tmi.recipe.types.RecipeItem;

import static mindustry.Vars.mobile;
import static tmi.TooManyItems.binds;

/**在{@link tmi.recipe.RecipeType}进行布局时所操作的元素对象，用于显示单个条目信息和提供控制逻辑*/
public class RecipeNode extends Button {
  public static final float SIZE = Scl.scl(80);

  public final RecipeItemStack stack;

  public final NodeType type;

  float progress, alpha;
  boolean activity, touched;
  float time;
  int clicked;

  Cons2<RecipeItem<?>, RecipesDialog.Mode> click;

  public RecipeNode(NodeType type, RecipeItemStack stack, Cons2<RecipeItem<?>, RecipesDialog.Mode> click){
    this.type = type;
    this.click = click;

    setBackground(Tex.button);
    this.stack = stack;

    touchable = Touchable.enabled;

    defaults().padLeft(8).padRight(8);

    setSize(SIZE);

    addListener(new Tooltip(t -> t.add(stack.item().localizedName(), Styles.outlineLabel)){{ allowMobile = true; }});

    hovered(() -> activity = true);
    exited(() -> activity = false);
    tapped(() -> {
      touched = true;
      time = Time.globalTime;
    });
    released(() -> {
      touched = false;

      if (Time.globalTime - time < 12){
        if (!mobile || Core.settings.getBool("keyboard")) {
          click.get(stack.item(), Core.input.keyDown(binds.hotKey) ? type == NodeType.block ? RecipesDialog.Mode.factory : RecipesDialog.Mode.usage : RecipesDialog.Mode.recipe);
        }
        else {
          clicked++;
          if (clicked >= 2){
            click.get(stack.item(), type == NodeType.block ? RecipesDialog.Mode.factory : RecipesDialog.Mode.usage);
            clicked = 0;
          }
        }
      }
      else {
        if (stack.item.hasDetails() && progress >= 0.95f){
          stack.item.displayDetails();
        }
      }
    });

    stack(
        new Table(t -> t.image(stack.item.icon()).size(SIZE/Scl.scl()*0.62f).scaling(Scaling.fit)),

        new Table(t -> {
          t.left().bottom();
          t.add(stack.getAmount(), Styles.outlineLabel);
          t.pack();
        }),

        new Table(t -> {
          if (!stack.item.locked()) return;
          t.right().bottom().defaults().right().bottom().pad(4);
          t.image(Icon.lock).scaling(Scaling.fit).size(10).color(Color.lightGray);
        })
    ).grow().pad(5);
  }

  @Override
  public void act(float delta) {
    super.act(delta);

    alpha = Mathf.lerpDelta(alpha, touched || activity ? 1 : 0, 0.08f);
    progress = Mathf.approachDelta(progress, stack.item.hasDetails() && touched? 1.01f : 0, 1/60f);
    if (Time.globalTime - time > 12 && clicked == 1){
      click.get(stack.item(), RecipesDialog.Mode.recipe);
      clicked = 0;
    }
  }

  @Override
  protected void drawBackground(float x, float y) {
    super.drawBackground(x, y);
    Lines.stroke(Scl.scl(34), Color.lightGray);
    Draw.alpha(0.5f);

    Lines.arc(x + width/2, y + height/2, Scl.scl(18), progress, 90);
  }
}
