package tmi.recipe.types;

import arc.graphics.g2d.TextureRegion;

public abstract class RecipeItem<T> {
  public final T item;

  protected RecipeItem(T item) {
    this.item = item;
  }

  public abstract int ordinal();
  public abstract int typeID();
  public abstract String name();
  public abstract String localizedName();
  public abstract TextureRegion icon();
  public abstract boolean hidden();

  public boolean locked() {
    return false;
  }
}
