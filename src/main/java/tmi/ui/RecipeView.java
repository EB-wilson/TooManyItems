package tmi.ui;

import arc.func.Cons2;
import arc.func.Cons3;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.FloatSeq;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Tex;
import tmi.TooManyItems;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;
import tmi.recipe.RecipeType;
import tmi.recipe.types.RecipeItem;

/**配方表显示的布局元素，用于为添加的{@link RecipeNode}设置正确的位置并将他们显示到界面容器当中*/
public class RecipeView extends Group {
  private final Vec2 bound = new Vec2();
  private final Seq<RecipeNode> nodes = new Seq<>();
  public final Seq<LineMeta> lines = new Seq<>();
  public final Recipe recipe;

  final Group childGroup;

  public RecipeView(Recipe recipe, Cons3<RecipeItemStack, NodeType, RecipesDialog.Mode> nodeClicked) {
    this.recipe = recipe;
    childGroup = new Group() {};

    for (RecipeItemStack content : recipe.materials.values()) {
      nodes.add(new RecipeNode(NodeType.material, content, nodeClicked));
    }
    for (RecipeItemStack content : recipe.productions.values()) {
      nodes.add(new RecipeNode(NodeType.production, content, nodeClicked));
    }

    if (recipe.block != null) nodes.add(new RecipeNode(NodeType.block, new RecipeItemStack(recipe.block), nodeClicked));

    nodes.each(this::addChild);

    addChild(childGroup);
  }

  @Override
  public void layout() {
    super.layout();
    childGroup.clear();
    childGroup.invalidate();

    lines.clear();
    bound.set(recipe.recipeType.initial(recipe));

    RecipeNode center = nodes.find(e -> e.type == NodeType.block);
    for (RecipeNode node : nodes) {
      recipe.recipeType.layout(node);
      LineMeta line = recipe.recipeType.line(node, center);
      if (line != null) lines.add(line);
    }

    recipe.recipeType.buildView(childGroup);
  }

  @Override
  public void draw() {
    validate();

    Draw.alpha(parentAlpha);
    recipe.recipeType.drawLine(this);
    super.draw();
  }

  @Override
  public float getPrefWidth() {
    return bound.x;
  }

  @Override
  public float getPrefHeight() {
    return bound.y;
  }

  public static class LineMeta{
    public final FloatSeq vertices = new FloatSeq();
    public Prov<Color> color = () -> Color.white;

    public void setVertices(float... vert){
      vertices.clear();
      vertices.addAll(vert);
    }

    public void addVertex(float x, float y){
      vertices.add(x, y);
    }
  }
}
