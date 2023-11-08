package tmi.recipe.types;

import arc.Core;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.ui.Styles;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;
import tmi.recipe.RecipeType;
import tmi.ui.RecipeNode;
import tmi.ui.RecipeView;

import static tmi.ui.RecipeNode.SIZE;

public class GeneratorRecipe extends FactoryRecipe {
  @Override
  public void buildView(Group view) {
    Label label = new Label(Core.bundle.get("misc.generator"), Styles.outlineLabel);
    label.layout();

    label.setPosition(blockPos.x + SIZE/2 + ITEM_PAD + label.getPrefWidth()/2, blockPos.y, Align.center);
    view.addChild(label);
  }

  @Override
  public Vec2 initial(Recipe recipe) {
    consPos.clear();
    prodPos.clear();
    blockPos.setZero();

    Seq<RecipeItemStack> mats = recipe.materials.values().toSeq();
    Seq<RecipeItemStack> prod = new Seq<>(), powers = new Seq<>();
    for (RecipeItemStack item : recipe.productions.values().toSeq()) {
      if (item.content != PowerMark.INSTANCE && item.content != HeatMark.INSTANCE) prod.add(item);
      else powers.add(item);
    }

    int materialNum = mats.size;
    int productionNum = prod.size;
    doubleInput = materialNum > DOUBLE_LIMIT;
    doubleOutput = productionNum > DOUBLE_LIMIT;

    bound.setZero();

    float wMat = 0, wProd = 0;

    if (materialNum > 0) {
      wMat = handleBound(materialNum, doubleInput);
      bound.y += ROW_PAD;
    }
    bound.y += SIZE;
    if (productionNum > 0) {
      bound.y += ROW_PAD;
      wProd = handleBound(productionNum, doubleOutput);
    }

    float offMatX = (bound.x - wMat)/2, offProdX = (bound.x - wProd)/2;

    float centX = bound.x / 2f;
    float offY = SIZE/2;

    if (materialNum > 0){
      offY = handleNode(mats, consPos, offMatX, offY, false);
      offY += ROW_PAD;
    }
    blockPos.set(centX, offY);
    offY += SIZE;
    if (productionNum > 0){
      offY += ROW_PAD;
      handleNode(prod, prodPos, offProdX, offY, true);
    }

    if (powers.any()) {
      bound.y += SIZE;
      float offX = (bound.x - handleBound(powers.size, false))/2;
      handleNode(powers, prodPos, offX, bound.y - SIZE/2, false);
    }

    return bound;
  }

  @Override
  public RecipeView.LineMeta line(RecipeNode from, RecipeNode to) {
    if (from.stack.content == PowerMark.INSTANCE || from.stack.content == HeatMark.INSTANCE) return new RecipeView.LineMeta();
    else return super.line(from, to);
  }
}
