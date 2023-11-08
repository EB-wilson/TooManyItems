package tmi.recipe;

import arc.graphics.Color;
import arc.graphics.g2d.Lines;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.struct.Seq;
import tmi.recipe.types.BuildingRecipe;
import tmi.recipe.types.CollectingRecipe;
import tmi.recipe.types.FactoryRecipe;
import tmi.ui.RecipeNode;
import tmi.ui.RecipeView;

public abstract class RecipeType {
  public static final Seq<RecipeType> all = new Seq<>();

  public static RecipeType factory,
  building,
  collecting;

  public void buildView(RecipeView view){}
  public abstract Vec2 initial(Recipe recipe);
  public abstract void layout(RecipeNode recipeNode);
  public abstract RecipeView.LineMeta line(RecipeNode from, RecipeNode to);

  public static void init() {
    factory = new FactoryRecipe();
    building = new BuildingRecipe();
    collecting = new CollectingRecipe();
  }

  public RecipeType(){
    all.add(this);
  }

  public void drawLine(RecipeView recipeView) {
    for (RecipeView.LineMeta line : recipeView.lines) {
      if (line.vertices.size < 2) continue;

      Lines.stroke(5, line.color.get());

      if (line.vertices.size <= 4){
        Lines.line(
            recipeView.x + line.vertices.items[0], recipeView.y + line.vertices.items[1],
            recipeView.x + line.vertices.items[2], recipeView.y + line.vertices.items[3]
        );
        continue;
      }

      Lines.beginLine();
      for (int i = 0; i < line.vertices.size; i += 2) {
        float x1 = line.vertices.items[i];
        float y1 = line.vertices.items[i + 1];

        Lines.linePoint(recipeView.x + x1, recipeView.y + y1);
      }
      Lines.endLine();
    }
  }
}
