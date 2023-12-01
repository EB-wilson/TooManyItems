package tmi.recipe;

import arc.util.Nullable;
import arc.util.Strings;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.world.meta.StatUnit;
import tmi.recipe.types.RecipeItem;

/**保存一个材料项目数据的结构类型，在{@link Recipe}中作数据记录对象使用*/
public class RecipeItemStack {
  /**该条目表示的{@link UnlockableContent}*/
  public final RecipeItem<?> item;
  /**条目附加的数量信息，这将被用作生产计算和显示数据的文本格式化*/
  public final float amount;

  /**条目数据显示的文本格式化函数，这里返回的文本将显示在条目上以表示数量信息*/
  public AmountFormatter amountFormat = f -> "";

  /**此条目的效率系数，应当绑定为该条目在生产工作中可用时的最高效率倍率，以参与生产计算*/
  public float efficiency = 1;
  /**该条目是否为可选消耗项，应当与实际情况同步*/
  public boolean optionalCons = false;
  /**该条目是否为属性项目，通常用于计算覆盖/关联的方块提供的属性增益*/
  public boolean isAttribute = false;
  /**该条目是否是增幅项目，若为增幅项目则被单独计算boost倍率，该倍率将影响输入物数量计算并直接乘在最终效率上*/
  public boolean isBooster = false;
  /**条目从属的属性组，一个属性组内的项目在工作效率计算时，会以最高的那一个作为计算结果。
   * <br>
   * 属性组的划分按照提供的对象确定，任意时候当两个条目的属性组对象{@link Object#equals(Object)}为真时就会被视为从属于同一属性组。
   * 该字段默认空，为空时表示该条目不从属于任何属性组*/
  @Nullable public Object attributeGroup = null;
  /**若为真，此消耗项的属性效率计算会按属性组的最大效率计算，否则会对属性效率求和*/
  public boolean maxAttr = false;

  public RecipeItemStack(RecipeItem<?> item, float amount) {
    this.item = item;
    this.amount = amount;
  }

  public RecipeItemStack(RecipeItem<?> item) {
    this(item, 0);
  }

  public RecipeItem<?> item() {
    return item;
  }

  public float amount(){
    return amount;
  }

  /**获取经过格式化的表示数量的文本信息*/
  public String getAmount() {
    return amountFormat.format(amount);
  }

  //属性设置的工具方法

  public RecipeItemStack setEfficiency(float efficiency) {
    this.efficiency = efficiency;
    return this;
  }

  public RecipeItemStack setOptionalCons(boolean optionalCons) {
    this.optionalCons = optionalCons;
    return this;
  }

  public RecipeItemStack setOptionalCons() {
    return setOptionalCons(true);
  }

  public RecipeItemStack setAttribute(){
    this.isAttribute = true;
    return this;
  }

  public RecipeItemStack setBooster() {
    isBooster = true;
    return this;
  }

  public RecipeItemStack setBooster(boolean boost) {
    isBooster = boost;
    return this;
  }

  public RecipeItemStack setMaxAttr(){
    this.maxAttr = true;
    return this;
  }

  public RecipeItemStack setAttribute(Object group){
    this.attributeGroup = group;
    return this;
  }

  public RecipeItemStack setFormat(AmountFormatter format){
    this.amountFormat = format;
    return this;
  }

  public RecipeItemStack setEmptyFormat(){
    this.amountFormat = f -> "";
    return this;
  }

  public RecipeItemStack setFloatFormat(float f) {
    setFormat(_f -> f > 1000? UI.formatAmount((long) f): Strings.autoFixed(f, 1));
    return this;
  }

  public RecipeItemStack setIntegerFormat(int i) {
    setFormat(_f -> i > 1000? UI.formatAmount(i): Integer.toString(i));
    return this;
  }

  public RecipeItemStack setFloatFormat() {
    setFormat(f -> f > 1000? UI.formatAmount((long) f): Strings.autoFixed(f, 1));
    return this;
  }

  public RecipeItemStack setIntegerFormat() {
    setFormat(f -> f > 1000? UI.formatAmount((long) f): Integer.toString((int) f));
    return this;
  }

  public RecipeItemStack setPresecFormat() {
    setFormat(f -> (f*60 > 1000? UI.formatAmount((long) (f*60)): Strings.autoFixed(f*60, 2)) + "/" + StatUnit.seconds.localized());
    return this;
  }

  public interface AmountFormatter {
    String format(float f);
  }
}
