package tmi.recipe.types;

import arc.graphics.g2d.TextureRegion;

import java.util.Objects;

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

  public boolean hasDetails(){ return false; }
  public void displayDetails(){}

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

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    RecipeItem<?> that = (RecipeItem<?>) object;
    return Objects.equals(item, that.item);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name());
  }
}
