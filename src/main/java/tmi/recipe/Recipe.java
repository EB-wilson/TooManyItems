package tmi.recipe;

import arc.func.Cons;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectFloatMap;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.util.Nullable;
import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;

public class Recipe {
  //type
  public final RecipeType recipeType;
  //meta
  public float time = -1;
  public OrderedMap<UnlockableContent, RecipeItemStack> productions = new OrderedMap<>();
  public OrderedMap<UnlockableContent, RecipeItemStack> materials = new OrderedMap<>();

  public EffFunc efficiency = getDefaultEff();

  //infos
  public Block block;
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


  public static EffFunc getDefaultEff(){
    return getDefaultEff(0);
  }

  public static EffFunc getDefaultEff(float baseEff){
    ObjectFloatMap<Object> attrGroups = new ObjectFloatMap<>();

    return (recipe, env) -> {
      attrGroups.clear();

      float attr = 0;
      float eff = 1;

      for (RecipeItemStack stack : recipe.materials.values()) {
        if (stack.isAttribute){
          attr = Math.max(attr, stack.efficiency*Mathf.clamp(env.getInputs(stack.content)/stack.amount));
        }
        else {
          if (stack.attributeGroup != null){
            float e = attrGroups.get(stack.attributeGroup, 1)*Math.max(stack.efficiency*Mathf.clamp(env.getInputs(stack.content)/stack.amount), stack.optionalCons? 1: 0);
            attrGroups.put(stack.attributeGroup, e);
          }
          else eff *= Math.max(stack.efficiency*Mathf.clamp(env.getInputs(stack.content)/stack.amount), stack.optionalCons? 1: 0);
        }
      }

      ObjectFloatMap.Values v = attrGroups.values();
      while (v.hasNext()) {
        eff *= v.next();
      }

      return (baseEff + attr)*eff;
    };
  }

  @FunctionalInterface
  public interface EffFunc{
    float get(Recipe recipe, EnvParameter env);
  }
}
