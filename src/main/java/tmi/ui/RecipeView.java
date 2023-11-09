package tmi.ui;

import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.FloatSeq;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;

public class RecipeView extends Group {
  private final Vec2 bound = new Vec2();
  private final Seq<RecipeNode> nodes = new Seq<>();
  public final Seq<LineMeta> lines = new Seq<>();
  public final Recipe recipe;

  final Group childGroup;

  public RecipeView(Recipe recipe) {
    this.recipe = recipe;
    childGroup = new Group() {};
    childGroup.setFillParent(true);

    for (RecipeItemStack content : recipe.materials.values()) {
      nodes.add(new RecipeNode(content) {{
        isMaterial = true;
      }});
    }
    for (RecipeItemStack content : recipe.productions.values()) {
      nodes.add(new RecipeNode(content) {{
        isProduction = true;
      }});
    }

    if (recipe.block != null) nodes.add(new RecipeNode(new RecipeItemStack(recipe.block)) {{
      isBlock = true;
    }});

    nodes.each(this::addChild);

    addChild(childGroup);
  }

  @Override
  public void layout() {
    super.layout();
    lines.clear();
    bound.set(recipe.recipeType.initial(recipe));

    RecipeNode center = nodes.find(e -> e.isBlock);
    for (RecipeNode node : nodes) {
      recipe.recipeType.layout(node);
      LineMeta line = recipe.recipeType.line(node, center);
      if (line != null) lines.add(line);
    }

    recipe.recipeType.buildView(childGroup);
  }

  @Override
  public void draw() {
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
