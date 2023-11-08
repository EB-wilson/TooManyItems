package tmi.recipe;

import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Nulls;
import mindustry.type.*;
import mindustry.world.Block;
import tmi.util.Consts;

public class RecipesManager{
  protected Seq<RecipeParser<?>> parsers = new Seq<>();
  protected ObjectMap<Recipe, Seq<Recipe>> sameGroups = new ObjectMap<>();

  private final Seq<Recipe> recipes = new Seq<>();
  private final ObjectSet<UnlockableContent> materials = new ObjectSet<>(), productions = new ObjectSet<>();
  private final ObjectSet<Block> blocks = new ObjectSet<>();

  public void registerParser(RecipeParser<?> parser){
    parsers.add(parser);
  }

  public void addRecipe(Seq<Recipe> recipes){
    for (Recipe recipe : recipes) {
      addRecipe(recipe);
    }
  }

  public void addRecipe(Recipe... recipes){
    for (Recipe recipe : recipes) {
      addRecipe(recipe);
    }
  }

  public void addRecipe(Recipe recipe) {
    recipes.add(recipe);
    for (RecipeItemStack stack : recipe.materials) {
      materials.add(stack.content);
    }
    for (RecipeItemStack stack : recipe.productions) {
      productions.add(stack.content);
    }
    if (recipe.block != null) blocks.add(recipe.block);
  }

  public Seq<Recipe> getRecipesByProduction(UnlockableContent production){
    return recipes.select(e -> e.containsProduction(production) || (e.recipeType == RecipeType.building && e.block == production));
  }

  public Seq<Recipe> getRecipesByMaterial(UnlockableContent material){
    return recipes.select(e -> e.containsMaterial(material));
  }

  public Seq<Recipe> getRecipesByFactory(Block block){
    return recipes.select(e -> e.recipeType != RecipeType.building && e.block == block);
  }

  public Seq<Recipe> getRecipeGroup(Recipe recipe){
    return sameGroups.get(recipe);
  }

  public void init() {
    for (RecipeParser<?> parser : parsers) {
      parser.init();
    }

    parseRecipes();
  }

  @SuppressWarnings("unchecked")
  public void parseRecipes(){
    for (Block block : Vars.content.blocks()) {
      for (RecipeParser<?> parser : parsers) {
        if (parser.isTarget(block)) addRecipe(((RecipeParser<Block>)parser).parse(block));
      }

      if (block.requirements.length > 0 && block.placeablePlayer){
        Recipe recipe = new Recipe(RecipeType.building);
        recipe.block = block;
        for (ItemStack stack : recipe.block.requirements) {
          recipe.addMaterial(stack.item, stack.amount);
        }
        addRecipe(recipe);
      }
    }
  }

  public boolean anyMaterial(UnlockableContent uc){
    return materials.contains(uc);
  }

  public boolean anyProduction(UnlockableContent uc){
    return productions.contains(uc);
  }

  public boolean anyBlock(Block uc){
    return blocks.contains(uc);
  }

  public boolean anyRecipe(UnlockableContent uc) {
    return materials.contains(uc) || productions.contains(uc) || (uc instanceof Block b && blocks.contains(b));
  }

  public void mergeGroup() {
    //TODO: 对相同配方不同方块进行分组
  }
}

