package tmi.recipe;

import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;

public class RecipeItemStack {
  public final UnlockableContent content;
  public final String amount;

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
}
