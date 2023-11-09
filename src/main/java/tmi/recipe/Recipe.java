package tmi.recipe;

import arc.func.Cons;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedMap;
import arc.util.Nullable;
import arc.util.Strings;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;
import mindustry.world.meta.StatUnit;

public class Recipe {
  //type
  public final RecipeType recipeType;
  //meta
  public float time = -1;
  public OrderedMap<UnlockableContent, RecipeItemStack> productions = new OrderedMap<>();
  public OrderedMap<UnlockableContent, RecipeItemStack> materials = new OrderedMap<>();

  //infos
  public Block block;
  @Nullable public String description;
  @Nullable public Cons<Table> buildInfo;

  public Recipe(RecipeType recipeType) {
    this.recipeType = recipeType;
  }

  public RecipeItemStack addMaterial(UnlockableContent item) {
    RecipeItemStack res = new RecipeItemStack(item);
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterial(UnlockableContent item, int amount){
    RecipeItemStack res = new RecipeItemStack(item, amount).setIntegerFormat();
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterial(UnlockableContent item, float amount){
    RecipeItemStack res = new RecipeItemStack(item, amount).setFloatFormat();
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterialPresec(UnlockableContent item, float preSeq){
    RecipeItemStack res = new RecipeItemStack(item, preSeq).setPresecFormat();
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterial(UnlockableContent item, String amount) {
    RecipeItemStack res = new RecipeItemStack(item, 0).setFormat(f -> amount);
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addProduction(UnlockableContent item) {
    RecipeItemStack res = new RecipeItemStack(item);
    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProduction(UnlockableContent item, int amount){
    RecipeItemStack res = new RecipeItemStack(item, amount).setIntegerFormat();
    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProduction(UnlockableContent item, float amount){
    RecipeItemStack res = new RecipeItemStack(item, amount).setFloatFormat();
    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProductionPresec(UnlockableContent item, float preSeq){
    RecipeItemStack res = new RecipeItemStack(item, preSeq).setPresecFormat();
    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProduction(UnlockableContent item, String amount) {
    RecipeItemStack res = new RecipeItemStack(item, 0).setFormat(f -> amount);
    productions.put(item, res);
    return res;
  }

  public Recipe setBlock(Block block){
    this.block = block;
    return this;
  }

  public Recipe setTime(float time){
    this.time = time;
    return this;
  }

  public boolean containsProduction(UnlockableContent production) {
    return productions.containsKey(production);
  }

  public boolean containsMaterial(UnlockableContent material) {
    return materials.containsKey(material);
  }
}
