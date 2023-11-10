package tmi.recipe;

import arc.util.Strings;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.world.meta.StatUnit;

public class RecipeItemStack {
  public final UnlockableContent content;
  public final float amount;

  public AmountFormatter amountFormat = f -> "";

  public float efficiency = 1;
  public boolean optionalCons = false;
  public boolean isAttribute = false;
  public Object attributeGroup = null;

  public RecipeItemStack(UnlockableContent content, float amount) {
    this.content = content;
    this.amount = amount;
  }

  public RecipeItemStack(UnlockableContent content) {
    this(content, 0);
  }

  public UnlockableContent content() {
    return content;
  }

  public float amount(){
    return amount;
  }

  public String getAmount() {
    return amountFormat.format(amount);
  }

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

  public RecipeItemStack setAttribute(Object group){
    this.attributeGroup = group;
    return this;
  }

  public RecipeItemStack setFormat(AmountFormatter format){
    this.amountFormat = format;
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
