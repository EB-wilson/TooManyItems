package tmi.recipe.types;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Scl;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.graphics.Pal;
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

public class FactoryRecipe extends RecipeType {
  public static final float ROW_PAD = Scl.scl(60);
  public static final float ITEM_PAD = Scl.scl(10);
  public static final int DOUBLE_LIMIT = 5;

  final Vec2 bound = new Vec2();
  final Vec2 blockPos = new Vec2();
  final ObjectMap<UnlockableContent, Vec2> consPos = new ObjectMap<>(), prodPos = new ObjectMap<>();
  final Vec2 optPos = new Vec2();

  boolean doubleInput, doubleOutput, hasOptionals;
  float time;

  @Override
  public void buildView(Group view) {
    Label label = new Label(Core.bundle.get("misc.factory"), Styles.outlineLabel);
    label.validate();

    label.setPosition(blockPos.x + SIZE/2 + ITEM_PAD + label.getPrefWidth()/2, blockPos.y, Align.center);
    view.addChild(label);

    buildOpts(view);
    buildTime(view, label.getHeight());
  }

  protected void buildTime(Group view, float offY) {
    if (time > 0){
      Label time = new Label(Stat.productionTime.localized() + ": " + (this.time > 3600? UI.formatTime(this.time): Strings.autoFixed(this.time/60, 2) + StatUnit.seconds.localized()), Styles.outlineLabel);
      time.validate();

      time.setPosition(blockPos.x + SIZE/2 + ITEM_PAD + time.getPrefWidth()/2, blockPos.y - offY - 4, Align.center);
      view.addChild(time);
    }
  }

  protected void buildOpts(Group view) {
    if (!hasOptionals) return;

    Label optionals = new Label(Core.bundle.get("misc.optional"), Styles.outlineLabel);
    optionals.setColor(Pal.accent);
    optionals.validate();
    optionals.setPosition(optPos.x, optPos.y - optionals.getHeight(), Align.center);
    view.addChild(optionals);
  }

  @Override
  public Vec2 initial(Recipe recipe) {
    time = recipe.time;

    consPos.clear();
    prodPos.clear();
    optPos.setZero();
    blockPos.setZero();

    Seq<RecipeItemStack> mats = recipe.materials.values().toSeq().select(e -> !e.optionalCons);
    Seq<RecipeItemStack> opts = recipe.materials.values().toSeq().select(e -> e.optionalCons);
    int materialNum = mats.size;
    int productionNum = recipe.productions.size;
    hasOptionals = opts.size > 0;
    doubleInput = materialNum > DOUBLE_LIMIT;
    doubleOutput = productionNum > DOUBLE_LIMIT;

    bound.setZero();

    float wOpt = 0, wMat = 0, wProd = 0;

    if (hasOptionals){
      wOpt = handleBound(opts.size, false);
      bound.y += ROW_PAD;
    }
    if (materialNum > 0) {
      wMat = handleBound(materialNum, doubleInput);
      bound.y += ROW_PAD;
    }
    bound.y += SIZE;
    if (productionNum > 0) {
      bound.y += ROW_PAD;
      wProd = handleBound(productionNum, doubleOutput);
    }

    float offOptX = (bound.x - wOpt)/2, offMatX = (bound.x - wMat)/2, offProdX = (bound.x - wProd)/2;

    float centX = bound.x / 2f;
    float offY = SIZE/2;

    if (hasOptionals){
      offY = handleNode(opts, consPos, offOptX, offY, false, false);
      optPos.set(bound.x/2, offY);
      offY += ROW_PAD;
    }
    if (materialNum > 0){
      offY = handleNode(mats, consPos, offMatX, offY, doubleInput, false);
      offY += ROW_PAD;
    }
    blockPos.set(centX, offY);
    offY += SIZE;
    if (productionNum > 0){
      offY += ROW_PAD;
      Seq<RecipeItemStack> seq = recipe.productions.values().toSeq();
      handleNode(seq, prodPos, offProdX, offY, doubleOutput, true);
    }

    return bound;
  }

  protected float handleNode(Seq<RecipeItemStack> seq, ObjectMap<UnlockableContent, Vec2> pos, float offX, float offY, boolean isDouble, boolean turn) {
    float dx = SIZE / 2;
    if (isDouble) {
      for (int i = 0; i < seq.size; i++) {
        if (turn) {
          if (i % 2 == 0) pos.put(seq.get(i).content(), new Vec2(offX + dx, offY + SIZE + ITEM_PAD));
          else pos.put(seq.get(i).content(), new Vec2(offX + dx, offY));
        }
        if (i % 2 == 0) pos.put(seq.get(i).content(), new Vec2(offX + dx, offY));
        else pos.put(seq.get(i).content(), new Vec2(offX + dx, offY + SIZE + ITEM_PAD));

        dx += SIZE / 2 + ITEM_PAD;
      }
      offY += SIZE * 2 + ITEM_PAD;
    }
    else {
      for (int i = 0; i < seq.size; i++) {
        pos.put(seq.get(i).content(), new Vec2(offX + dx, offY));
        dx += SIZE + ITEM_PAD;
      }
      offY += SIZE;
    }
    return offY;
  }

  protected float handleBound(int num, boolean isDouble) {
    float res;
    if (isDouble) {
      int n = Mathf.ceil(num/2f);
      bound.x = Math.max(bound.x, res = SIZE*n + ITEM_PAD*(n - 1) + (1 - num%2)*(SIZE/2 + ITEM_PAD/2));
      bound.y += SIZE*2 + ITEM_PAD;
    }
    else {
      bound.x = Math.max(bound.x, res = SIZE*num + ITEM_PAD*(num - 1));
      bound.y += SIZE;
    }

    return res;
  }

  @Override
  public void layout(RecipeNode recipeNode) {
    if (recipeNode.isMaterial){
      Vec2 pos = consPos.get(recipeNode.stack.content());
      recipeNode.setPosition(pos.x, pos.y, Align.center);
    }
    else if (recipeNode.isProduction){
      Vec2 pos = prodPos.get(recipeNode.stack.content());
      recipeNode.setPosition(pos.x, pos.y, Align.center);
    }
    else if (recipeNode.isBlock){
      recipeNode.setPosition(blockPos.x, blockPos.y, Align.center);
    }
  }

  @Override
  public RecipeView.LineMeta line(RecipeNode from, RecipeNode to) {
    RecipeView.LineMeta res = new RecipeView.LineMeta();

    if (from.stack.optionalCons) return res;

    res.color = from.isMaterial? () -> Tmp.c1.set(Color.gray).lerp(Pal.accent, Mathf.pow(Mathf.absin(Time.time/8 + Mathf.pi, 1, 1), 3)):
        () -> Tmp.c1.set(Color.gray).lerp(Pal.accent, Mathf.pow(Mathf.absin(Time.time/8, 1, 1), 3));

    float offX = from.getWidth()/2;
    float offY = from.getHeight()/2;
    float offX1 = to.getWidth()/2;
    float offY1 = to.getHeight()/2;

    float off = (to.y - from.y) > 0? -ROW_PAD/2 - SIZE/2: ROW_PAD/2 + SIZE/2;
    if (from.x != to.x){
      res.addVertex(from.x + offX, from.y + offY);
      res.addVertex(from.x + offX, to.y + offY1 + off);
      res.addVertex(to.x + offX1, to.y + offY1 + off);
      res.addVertex(to.x + offX1, to.y + offY1);
    }
    else{
      res.addVertex(from.x + offX, from.y + offY);
      res.addVertex(to.x + offX1, to.y + offY1);
    }

    return res;
  }
}
