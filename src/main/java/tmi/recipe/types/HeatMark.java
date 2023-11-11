package tmi.recipe.types;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import mindustry.gen.Icon;
import tmi.TooManyItems;

public class HeatMark extends RecipeItem<String> {
  public static HeatMark INSTANCE = TooManyItems.itemsManager.addItemWrap("heat-mark", new HeatMark());

  private HeatMark() {
    super("heat-mark");
  }

  @Override
  public int ordinal() {
    return -1;
  }

  @Override
  public int typeID() {
    return -1;
  }

  @Override
  public String name() {
    return item;
  }

  @Override
  public String localizedName() {
    return Core.bundle.get("tmi." + item);
  }

  @Override
  public TextureRegion icon() {
    return Icon.waves.getRegion();
  }

  @Override
  public boolean hidden() {
    return false;
  }
}
