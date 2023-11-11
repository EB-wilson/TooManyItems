package tmi.recipe.types;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;
import tmi.TooManyItems;

public class PowerMark extends RecipeItem<String> {
  public static PowerMark INSTANCE = TooManyItems.itemsManager.addItemWrap("power-mark", new PowerMark());

  private PowerMark() {
    super("power-mark");
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
    return Icon.power.getRegion();
  }

  @Override
  public boolean hidden() {
    return false;
  }
}
