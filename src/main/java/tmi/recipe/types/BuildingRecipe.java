package tmi.recipe.types;

import arc.Core;
import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Scl;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Strings;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.ui.Styles;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;
import tmi.recipe.RecipeType;
import tmi.ui.RecipeNode;
import tmi.ui.RecipeView;
import tmi.util.Consts;

import static tmi.ui.RecipeNode.SIZE;

public class BuildingRecipe extends RecipeType {
  public static final float ITEM_PAD = Scl.scl(30);
  public static final float RAND = Scl.scl(65);
  public static final float MIN_RAD = Scl.scl(125);

  final Vec2 bound = new Vec2();
  final Vec2 blockPos = new Vec2();
  final ObjectMap<UnlockableContent, Vec2> materialPos = new ObjectMap<>();

  float time;

  @Override
  public void buildView(Group view) {
    Label label = new Label(Core.bundle.get("misc.building"), Styles.outlineLabel);
    label.getStyle().background = Consts.grayUI;
    label.validate();

    label.setPosition(blockPos.x + SIZE/2 + ITEM_PAD + label.getPrefWidth()/2, blockPos.y, Align.center);
    view.addChild(label);

    if (time > 0){
      Label time = new Label(Stat.buildTime.localized() + ": " + (this.time > 3600? UI.formatTime(this.time): Strings.autoFixed(this.time/60, 2) + StatUnit.seconds.localized()), Styles.outlineLabel);
      time.getStyle().background = Consts.grayUI;
      time.validate();

      time.setPosition(blockPos.x + SIZE/2 + ITEM_PAD + time.getPrefWidth()/2, blockPos.y - label.getHeight() - 4, Align.center);
      view.addChild(time);
    }
  }

  @Override
  public Vec2 initial(Recipe recipe) {
    time = recipe.time;

    bound.setZero();
    blockPos.setZero();
    materialPos.clear();

    Seq<RecipeItemStack> seq = recipe.materials.values().toSeq();
    float radians = 2f*Mathf.pi/seq.size;
    float radius = Math.max(MIN_RAD, (SIZE + ITEM_PAD)/radians);

    bound.set(radius + SIZE, radius + SIZE).scl(2);
    blockPos.set(bound).scl(0.5f);

    float off = Mathf.random(0, 360f);
    for (int i = 0; i < seq.size; i++) {
      float angle = radians*i*Mathf.radDeg + off;
      float r = Mathf.random(0, RAND) + radius;

      materialPos.put(seq.get(i).content(), new Vec2(blockPos.x + Angles.trnsx(angle, r), blockPos.y + Angles.trnsy(angle, r)));
    }

    return bound;
  }

  @Override
  public void layout(RecipeNode recipeNode) {
    if (recipeNode.isMaterial){
      Vec2 pos = materialPos.get(recipeNode.stack.content());
      recipeNode.setPosition(pos.x, pos.y, Align.center);
    }
    else if (recipeNode.isBlock){
      recipeNode.setPosition(blockPos.x, blockPos.y, Align.center);
    }
    else Log.warn("unexpected production in building recipe");
  }

  @Override
  public RecipeView.LineMeta line(RecipeNode from, RecipeNode to) {
    RecipeView.LineMeta res = new RecipeView.LineMeta();
    res.color = () -> Color.gray;

    float offX = from.getWidth()/2;
    float offY = from.getHeight()/2;
    float offX1 = to.getWidth()/2;
    float offY1 = to.getHeight()/2;

    res.addVertex(from.x + offX, from.y + offY);
    res.addVertex(to.x + offX1, to.y + offY1);

    return res;
  }
}
