package tmi.recipe.types;

import arc.Core;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Scl;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.ctype.UnlockableContent;
import mindustry.ui.Styles;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;

import static tmi.ui.RecipeNode.SIZE;

public class CollectingRecipe extends FactoryRecipe {
  public static final float ROW_PAD = Scl.scl(60);
  public static final float ITEM_PAD = Scl.scl(10);

  @Override
  public void buildView(Group view) {
    Label label = new Label(Core.bundle.get("misc.collecting"), Styles.outlineLabel);
    label.layout();

    label.setPosition(blockPos.x + SIZE/2 + ITEM_PAD + label.getPrefWidth()/2, blockPos.y, Align.center);
    view.addChild(label);

    buildOpts(view);
    buildTime(view, label.getHeight());
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
    hasOptionals = opts.size > 0;
    int materialNum = mats.size;
    int productionNum = recipe.productions.size;

    bound.setZero();

    float wOpt = 0, wMat = 0, wProd = 0;

    if (hasOptionals){
      wOpt = handleBound(opts.size);
      bound.y += ROW_PAD;
    }
    if (materialNum > 0) {
      wMat = handleBound(materialNum);
      bound.y += ROW_PAD;
    }
    bound.y += SIZE;
    if (productionNum > 0) {
      bound.y += ROW_PAD;
      wProd = handleBound(productionNum);
    }

    float offOptX = (bound.x - wOpt)/2, offMatX = (bound.x - wMat)/2, offProdX = (bound.x - wProd)/2;

    float centX = bound.x / 2f;
    float offY = SIZE/2;

    if (hasOptionals){
      offY = handleNode(opts, consPos, offOptX, offY);
      optPos.set(bound.x/2, offY);
      offY += ROW_PAD;
    }
    if (materialNum > 0){
      offY = handleNode(mats, consPos, offMatX, offY);
      offY += ROW_PAD;
    }
    blockPos.set(centX, offY);
    offY += SIZE;
    if (productionNum > 0){
      offY += ROW_PAD;
      Seq<RecipeItemStack> seq = recipe.productions.values().toSeq();
      handleNode(seq, prodPos, offProdX, offY);
    }

    return bound;
  }

  protected float handleNode(Seq<RecipeItemStack> seq, ObjectMap<RecipeItem<?>, Vec2> pos, float offX, float offY) {
    float dx = SIZE / 2;
    for (int i = 0; i < seq.size; i++) {
      pos.put(seq.get(i).item(), new Vec2(offX + dx, offY));
      dx += SIZE + ITEM_PAD;
    }
    offY += SIZE;
    return offY;
  }

  protected float handleBound(int num) {
    float res;

    bound.x = Math.max(bound.x, res = SIZE*num + ITEM_PAD*(num - 1));
    bound.y += SIZE;

    return res;
  }
}
