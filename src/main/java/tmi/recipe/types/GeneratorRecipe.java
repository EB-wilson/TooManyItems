package tmi.recipe.types;

import arc.Core;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.ui.Styles;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;
import tmi.ui.NodeType;
import tmi.ui.RecipeNode;
import tmi.ui.RecipeView;

import static tmi.ui.RecipeNode.SIZE;

public class GeneratorRecipe extends FactoryRecipe {
  public final ObjectSet<RecipeItem<?>> powers = new ObjectSet<>();

  {
    powers.addAll(
        PowerMark.INSTANCE,
        HeatMark.INSTANCE
    );
  }

  @Override
  public void buildView(Group view) {
    Label label = new Label(Core.bundle.get("misc.generator"), Styles.outlineLabel);
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
    blockPos.setZero();

    Seq<RecipeItemStack> mats = recipe.materials.values().toSeq().select(e -> !e.optionalCons);
    Seq<RecipeItemStack> opts = recipe.materials.values().toSeq().select(e -> e.optionalCons);
    Seq<RecipeItemStack> prod = new Seq<>(), powers = new Seq<>();
    for (RecipeItemStack item : recipe.productions.values().toSeq()) {
      if (isPower(item.item)) powers.add(item);
      else prod.add(item);
    }

    int materialNum = mats.size;
    int productionNum = prod.size;
    hasOptionals = opts.size > 0;
    doubleInput = materialNum > DOUBLE_LIMIT;
    doubleOutput = productionNum > DOUBLE_LIMIT;

    bound.setZero();

    float wOpt = 0, wMat = 0, wProd = 0, wPow = 0;

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
    if (powers.any()){
      bound.y += ROW_PAD;
      wPow = handleBound(powers.size, false);
    }

    float offOptX = (bound.x - wOpt)/2, offMatX = (bound.x - wMat)/2, offProdX = (bound.x - wProd)/2, offPowX = (bound.x - wPow)/2;

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
      offY = handleNode(prod, prodPos, offProdX, offY, doubleOutput, true);
    }

    if (powers.any()) {
      offY += ROW_PAD;
      handleNode(powers, prodPos, offPowX, offY, false, true);
    }

    return bound;
  }

  @Override
  public RecipeView.LineMeta line(RecipeNode from, RecipeNode to) {
    if ((isPower(from.stack.item) && from.type == NodeType.production) || (isPower(to.stack.item) && to.type == NodeType.production)) return new RecipeView.LineMeta();
    else return super.line(from, to);
  }

  protected boolean isPower(RecipeItem<?> item) {
    return powers.contains(item);
  }
}
