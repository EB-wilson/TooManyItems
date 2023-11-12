package tmi.recipe.types;

import arc.graphics.g2d.TextureRegion;

public abstract class RecipeItem<T> implements Comparable<RecipeItem<?>>{
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

  @Override
  public int compareTo(RecipeItem<?> o) {
    int n = Integer.compare(typeID(), o.typeID());

    if (n == 0){
      return ordinal() - o.ordinal();
    }

    return n;
  }
}
