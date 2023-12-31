package tmi.recipe;

import arc.func.Cons;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectFloatMap;
import arc.struct.OrderedMap;
import arc.util.Nullable;
import tmi.recipe.types.RecipeItem;

import java.util.Objects;

/**配方信息的存储类，该类的每一个实例都表示为一个单独的配方，用于显示配方或者计算生产数据*/
public class Recipe {
  private static final EffFunc ONE_BASE = getDefaultEff(1);
  private static final EffFunc ZERO_BASE = getDefaultEff(0);

  /**该配方的类型，请参阅{@link RecipeType}*/
  public final RecipeType recipeType;
  /**配方的标准耗时，具体来说即该配方在100%的工作效率下执行一次生产的耗时，任意小于0的数字都被认为生产过程是连续的*/
  //meta
  public float time = -1;

  /**配方的产出物列表
   * @see RecipeItemStack*/
  public final OrderedMap<RecipeItem<?>, RecipeItemStack> productions = new OrderedMap<>();
  /**配方的输入材料列表
   * @see RecipeItemStack*/
  public final OrderedMap<RecipeItem<?>, RecipeItemStack> materials = new OrderedMap<>();

  /**配方的效率计算函数，用于给定一个输入环境参数和配方数据，计算出该配方在这个输入环境下的工作效率*/
  public EffFunc efficiency = getOneEff();

  //infos
  /**执行该配方的建筑/方块*/
  public RecipeItem<?> block;
  /**该配方在显示时的附加显示内容构建函数，若不设置则认为不添加任何附加信息*/
  @Nullable public Cons<Table> subInfoBuilder;

  public Recipe(RecipeType recipeType) {
    this.recipeType = recipeType;
  }

  /**用配方当前使用的效率计算器计算该配方在给定的环境参数下的运行效率*/
  public float calculateEfficiency(EnvParameter parameter) {
    return efficiency.calculateEff(this, parameter, calculateMultiple(parameter));
  }

  public float calculateEfficiency(EnvParameter parameter, float multiplier) {
    return efficiency.calculateEff(this, parameter, multiplier);
  }

  public float calculateMultiple(EnvParameter parameter) {
    return efficiency.calculateMultiple(this, parameter);
  }

  //utils

  public RecipeItemStack addMaterial(RecipeItem<?> item, int amount){
    RecipeItemStack res = time > 0?
        new RecipeItemStack(item, amount/time).setIntegerFormat(time):
        new RecipeItemStack(item, amount).setIntegerFormat();

    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterial(RecipeItem<?> item, float amount){
    RecipeItemStack res = time > 0?
        new RecipeItemStack(item, amount/time).setFloatFormat(time):
        new RecipeItemStack(item, amount).setFloatFormat();

    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterialPresec(RecipeItem<?> item, float preSeq){
    RecipeItemStack res = new RecipeItemStack(item, preSeq).setPresecFormat();
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterialRaw(RecipeItem<?> item, float amount){
    RecipeItemStack res = new RecipeItemStack(item, amount);
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addProduction(RecipeItem<?> item, int amount){
    RecipeItemStack res = time > 0?
        new RecipeItemStack(item, amount/time).setIntegerFormat(time):
        new RecipeItemStack(item, amount).setIntegerFormat();

    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProduction(RecipeItem<?> item, float amount){
    RecipeItemStack res = time > 0?
        new RecipeItemStack(item, amount/time).setFloatFormat(time):
        new RecipeItemStack(item, amount).setFloatFormat();

    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProductionPresec(RecipeItem<?> item, float preSeq){
    RecipeItemStack res = new RecipeItemStack(item, preSeq).setPresecFormat();
    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProductionRaw(RecipeItem<?> item, float amount){
    RecipeItemStack res = new RecipeItemStack(item, amount);
    productions.put(item, res);
    return res;
  }

  public Recipe setBlock(RecipeItem<?> block){
    this.block = block;
    return this;
  }

  public Recipe setTime(float time){
    this.time = time;
    return this;
  }

  public Recipe setEfficiency(EffFunc func){
    efficiency = func;
    return this;
  }

  public boolean containsProduction(RecipeItem<?> production) {
    return productions.containsKey(production);
  }

  public boolean containsMaterial(RecipeItem<?> material) {
    return materials.containsKey(material);
  }

  /**@see Recipe#getDefaultEff(float) */
  public static EffFunc getOneEff(){
    return ONE_BASE;
  }

  /**@see Recipe#getDefaultEff(float) */
  public static EffFunc getZeroEff(){
    return ZERO_BASE;
  }

  /**生成一个适用于vanilla绝大多数工厂与设备的效率计算器，若{@linkplain RecipeParser 配方解析器}正确的解释了方块，这个函数应当能够正确计算方块的实际工作效率*/
  public static EffFunc getDefaultEff(float baseEff){
    ObjectFloatMap<Object> attrGroups = new ObjectFloatMap<>();

    return new EffFunc() {
      @Override
      public float calculateEff(Recipe recipe, EnvParameter env, float mul) {
        float eff = 1;

        attrGroups.clear();

        for (RecipeItemStack stack : recipe.materials.values()) {
          if (stack.isBooster || stack.isAttribute) continue;

          if (stack.attributeGroup != null){
            float e = attrGroups.get(stack.attributeGroup, 1)*stack.efficiency*Mathf.clamp(env.getInputs(stack.item)/(stack.amount*mul));
            if (stack.maxAttr) {
              attrGroups.put(stack.attributeGroup, Math.max(attrGroups.get(stack.attributeGroup, 0), e));
            }
            else attrGroups.increment(stack.attributeGroup, 0, e);
          }
          else eff *= Math.max(stack.efficiency*Mathf.clamp(env.getInputs(stack.item)/(stack.amount*mul)), stack.optionalCons? 1: 0);
        }

        ObjectFloatMap.Values v = attrGroups.values();
        while (v.hasNext()) {
          eff *= v.next();
        }

        return eff*mul;
      }

      @Override
      public float calculateMultiple(Recipe recipe, EnvParameter env) {
        attrGroups.clear();

        float attr = 0;
        float boost = 1;

        for (RecipeItemStack stack : recipe.materials.values()) {
          if (!stack.isBooster && !stack.isAttribute) continue;

          if (stack.isAttribute){
            float a = stack.efficiency*Mathf.clamp(env.getAttribute(stack.item)/stack.amount);
            if (stack.maxAttr) attr = Math.max(attr, a);
            else attr += a;
          }
          else {
            if (stack.attributeGroup != null){
              float e = attrGroups.get(stack.attributeGroup, 1)*stack.efficiency*Mathf.clamp(env.getInputs(stack.item)/stack.amount);
              if (stack.maxAttr) {
                attrGroups.put(stack.attributeGroup, Math.max(attrGroups.get(stack.attributeGroup, 0), e));
              }
              else attrGroups.increment(stack.attributeGroup, 0, e);
            }
            else boost *= Math.max(stack.efficiency*Mathf.clamp(env.getInputs(stack.item)/stack.amount), stack.optionalCons? 1: 0);
          }
        }

        ObjectFloatMap.Values v = attrGroups.values();
        while (v.hasNext()) {
          boost *= v.next();
        }
        return boost*(baseEff + attr);
      }
    };
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (!(object instanceof Recipe r)) return false;
    if (r.recipeType != recipeType || r.block != block) return false;

    if (r.materials.size != materials.size || r.productions.size != productions.size) return false;

    return r.materials.equals(materials) && r.productions.equals(productions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipeType, productions.orderedKeys(), materials.orderedKeys(), block);
  }

  public interface EffFunc{
    float calculateEff(Recipe recipe, EnvParameter env, float mul);
    float calculateMultiple(Recipe recipe, EnvParameter env);
  }
}
