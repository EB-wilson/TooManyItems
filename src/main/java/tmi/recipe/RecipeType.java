package tmi.recipe;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.geom.Vec2;
import arc.scene.Group;
import arc.struct.Seq;
import tmi.recipe.types.BuildingRecipe;
import tmi.recipe.types.CollectingRecipe;
import tmi.recipe.types.FactoryRecipe;
import tmi.recipe.types.GeneratorRecipe;
import tmi.ui.RecipeNode;
import tmi.ui.RecipeView;

/**配方表类型，用于描述一个配方如何被显示或者计算等*/
public abstract class RecipeType {
  public static final Seq<RecipeType> all = new Seq<>();

  public static RecipeType factory,
  building,
  collecting,
  generator;

  /**生成{@linkplain RecipeView 配方视图}前对上下文数据进行初始化，并计算布局尺寸
   *
   * @return 表示该布局的长宽尺寸的二元向量*/
  public abstract Vec2 initial(Recipe recipe);
  /**为参数传入的{@link RecipeNode}设置坐标以完成布局*/
  public abstract void layout(RecipeNode recipeNode);
  /**生成从给定起始节点到目标节点的{@linkplain tmi.ui.RecipeView.LineMeta 线条信息}*/
  public abstract RecipeView.LineMeta line(RecipeNode from, RecipeNode to);
  /**向配方显示器内添加显示部件的入口*/
  public void buildView(Group view){}

  public static void init() {
    factory = new FactoryRecipe();
    building = new BuildingRecipe();
    collecting = new CollectingRecipe();
    generator = new GeneratorRecipe();
  }

  public RecipeType(){
    all.add(this);
  }

  public void drawLine(RecipeView recipeView) {
    for (RecipeView.LineMeta line : recipeView.lines) {
      if (line.vertices.size < 2) continue;

      float a = Draw.getColor().a;
      Lines.stroke(5, line.color.get());
      Draw.alpha(Draw.getColor().a*a);

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
