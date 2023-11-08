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
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Tex;
import mindustry.ui.ItemDisplay;
import mindustry.ui.ItemImage;
import mindustry.ui.Styles;
import tmi.TooManyItems;
import tmi.recipe.RecipeItemStack;

public class RecipeNode extends Button {
  public static final float SIZE = 80;

  public final RecipeItemStack stack;

  public boolean isMaterial, isProduction, isBlock;

  float progress, alpha;
  boolean activity, touched;
  float time;

  @SuppressWarnings("StringOperationCanBeSimplified")
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
        TooManyItems.recipesDialog.setCurrSelecting(stack.content(), Core.input.ctrl()? isBlock? RecipesDialog.Mode.factory: RecipesDialog.Mode.usage : RecipesDialog.Mode.recipe);
      }
      else {
        if (progress >= 0.92f) Vars.ui.content.show(stack.content());
      }
    });

    update(() -> {
      alpha = Mathf.lerpDelta(alpha, touched || activity ? 1 : 0, 0.08f);
      progress = Mathf.approachDelta(progress, touched? 1 : 0, 1/60f);
    });

    stack(
      new Table(t -> t.add(new Table(o -> {
        o.add(new Image(stack.content.uiIcon)).size(SIZE/2).scaling(Scaling.fit);
      })).grow()),

     new Table(t -> t.add(new Table(ta -> {
       ta.left().bottom();
       ta.add(stack.amount, Styles.outlineLabel);
       ta.pack();
     })).grow())
    ).grow().pad(5);
  }

  @Override
  protected void drawBackground(float x, float y) {
    super.drawBackground(x, y);
    Lines.stroke(30, Color.lightGray);
    Draw.alpha(0.5f);

    Lines.arc(x + width/2, y + height/2, 18, progress, 90);
  }
}
