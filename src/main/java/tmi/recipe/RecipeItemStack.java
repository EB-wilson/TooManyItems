package tmi.recipe;

import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;

public class RecipeItemStack {
  public final UnlockableContent content;
  public final String amount;

  public float efficiency = 1;
  public boolean optionalCons = false;

  public RecipeItemStack(UnlockableContent content, String amount) {
    this.content = content;
    this.amount = amount;
  }

  public RecipeItemStack(UnlockableContent content) {
    this(content, "");
  }

  public UnlockableContent content() {
    return content;
  }

  public String amount() {
    return amount;
  }

  public RecipeItemStack setEfficiency(float efficiency) {
    this.efficiency = efficiency;
    return this;
  }

  public RecipeItemStack setOptionalCons(boolean optionalCons) {
    this.optionalCons = optionalCons;
    return this;
  }
}
