package tmi.recipe.types;

import arc.Core;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.ctype.UnlockableContent;
import mindustry.ui.Styles;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;

import static tmi.ui.RecipeNode.SIZE;

public class CollectingRecipe extends FactoryRecipe {
  public static final float ROW_PAD = 60;
  public static final float ITEM_PAD = 10;

  @Override
  public void buildView(Group view) {
    Label label = new Label(Core.bundle.get("misc.collecting"), Styles.outlineLabel);
    label.layout();

    label.setPosition(blockPos.x + SIZE/2 + ITEM_PAD + label.getPrefWidth()/2, blockPos.y, Align.center);
    view.addChild(label);
  }

  @Override
  public Vec2 initial(Recipe recipe) {
    consPos.clear();
    prodPos.clear();
    blockPos.setZero();

    int materialNum = recipe.materials.size;
    int productionNum = recipe.productions.size;

    bound.setZero();

    float wMat = 0, wProd = 0;

    if (materialNum > 0) {
      wMat = handleBound(materialNum);
      bound.y += ROW_PAD;
    }
    bound.y += SIZE;
    if (productionNum > 0) {
      bound.y += ROW_PAD;
      wProd = handleBound(productionNum);
    }

    float offMatX = (bound.x - wMat)/2, offProdX = (bound.x - wProd)/2;

    float centX = bound.x / 2f;
    float offY = SIZE/2;

    if (materialNum > 0){
      Seq<RecipeItemStack> seq = recipe.materials.values().toSeq();
      offY = handleNode(seq, consPos, offMatX, offY);
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

  protected float handleNode(Seq<RecipeItemStack> seq, ObjectMap<UnlockableContent, Vec2> pos, float offX, float offY) {
    float dx = SIZE / 2;
    for (int i = 0; i < seq.size; i++) {
      pos.put(seq.get(i).content(), new Vec2(offX + dx, offY));
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
