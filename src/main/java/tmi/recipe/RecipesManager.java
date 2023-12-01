package tmi.recipe;

import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.type.*;
import mindustry.world.Block;
import tmi.TooManyItems;
import tmi.recipe.types.RecipeItem;

import static tmi.TooManyItems.itemsManager;

/**全局配方管理器，以单例模式运行，用于管理和查询所有已加载的配方和分组，同时{@link RecipeParser}也通过添加到该对象以生效*/
public class RecipesManager{
  /**所有{@linkplain RecipeParser 配方分析工具}的存储容器，已注册的分析工具将被存储在这里待加载配方时分析方块使用*/
  protected Seq<RecipeParser<?>> parsers = new Seq<>(RecipeParser.class);

  /**当前存放所有配方的容器，所有已添加的配方都被存储在这里*/
  protected final Seq<Recipe> recipes = new Seq<>();

  private final ObjectSet<RecipeItem<?>> materials = new ObjectSet<>(), productions = new ObjectSet<>(), blocks = new ObjectSet<>();

  /**向管理器注册一个{@linkplain RecipeParser}用于分析方块可用的配方*/
  public void registerParser(RecipeParser<?> parser){
    parsers.add(parser);
  }

  /**添加若干个配方*/
  public void addRecipe(Seq<Recipe> recipes){
    for (Recipe recipe : recipes) {
      addRecipe(recipe);
    }
  }

  /**添加若干个配方*/
  public void addRecipe(Recipe... recipes){
    for (Recipe recipe : recipes) {
      addRecipe(recipe);
    }
  }

  /**添加单个配方*/
  public void addRecipe(Recipe recipe) {
    recipes.add(recipe);
    for (RecipeItemStack stack : recipe.materials.values()) {
      materials.add(stack.item);
    }
    for (RecipeItemStack stack : recipe.productions.values()) {
      productions.add(stack.item);
    }
    if (recipe.block != null) blocks.add(recipe.block);
  }

  /**以配方的产出项筛选配方，若配方的产出物中包含参数给定的项目则添加到返回列表*/
  public Seq<Recipe> getRecipesByProduction(RecipeItem<?> production){
    return recipes.select(e -> e.containsProduction(production) || (e.recipeType == RecipeType.building && e.block == production));
  }

  /**以配方的材料筛选配方，若配方的消耗材料中包含参数给定的项目则添加到返回列表*/
  public Seq<Recipe> getRecipesByMaterial(RecipeItem<?> material){
    return recipes.select(e -> e.containsMaterial(material));
  }

  /**以配方的建筑方块筛选配方，若配方的{@link Recipe#block}与给定的参数相同则添加到返回列表*/
  public Seq<Recipe> getRecipesByFactory(RecipeItem<?> block){
    return recipes.select(e -> e.recipeType != RecipeType.building && e.block == block);
  }

  public void init() {
    for (RecipeParser<?> parser : parsers) {
      parser.init();
    }

    parseRecipes();
  }

  /**从当前游戏内已装载的所有方块进行分析，搜索合适的{@link RecipeParser}解释方块以获取配方信息并添加到列表之中。
   * 另外，将方块的建造成本生成为配方添加到列表。*/
  @SuppressWarnings("unchecked")
  public void parseRecipes(){
    for (Block block : Vars.content.blocks()) {
      t: for (int i = 0; i < parsers.size; i++) {
        RecipeParser<?> parser = parsers.get(i);

        if (parser.isTarget(block)){
          for (int l = 0; l < parsers.size; l++) {
            if (parsers.get(l) == parser) continue;
            if (parsers.get(l).isTarget(block) && parsers.contains(e -> e.exclude(parser))) continue t;
          }

          addRecipe(((RecipeParser<Block>)parser).parse(block));
        }
      }

      if (block.requirements.length > 0 && block.placeablePlayer){
        Recipe recipe = new Recipe(RecipeType.building);
        recipe.setBlock(itemsManager.getItem(block));
        recipe.setTime(block.buildCost);
        for (ItemStack stack : block.requirements) {
          recipe.addMaterial(itemsManager.getItem(stack.item), stack.amount);
        }
        addRecipe(recipe);
      }
    }
  }

  /**参数给定的条目是否以输入材料的位置参与至少一个配方*/
  public boolean anyMaterial(RecipeItem<?> uc){
    return materials.contains(uc);
  }

  /**参数给定的条目是否以产出物的位置参与至少一个配方*/
  public boolean anyProduction(RecipeItem<?> uc){
    return productions.contains(uc);
  }

  /**是否有任意一个配方的方块项与参数给定的方块一致*/
  public boolean anyBlock(RecipeItem<?> uc){
    return blocks.contains(uc);
  }

  /**参数提供的项目是否可以找到任何一个其参与的配方*/
  public boolean anyRecipe(RecipeItem<?> uc) {
    return materials.contains(uc) || productions.contains(uc) || (uc.item instanceof Block b && blocks.contains(uc));
  }

  /**该方法用于合并所有配方中的相似配方，以折叠他们。
   * <p>具体来说，对于两个{@link Recipe}，如果它们的{@link Recipe#materials}和{@link Recipe#productions}均相似，那么就可以被视为同一个分组的成员。
   * <p>关于{@link RecipeItemStack}的相似，这要求两个对象的
   * {@link RecipeItemStack#item}，
   * {@link RecipeItemStack#optionalCons}，
   * {@link RecipeItemStack#isAttribute}，
   * {@link RecipeItemStack#attributeGroup}
   * 均是一致的才可被视为相似，剩余字段均可忽略*/
  public void mergeGroup() {
    //TODO: 对相同配方不同方块进行分组
  }
}

