package tmi.recipe.types;

import arc.Core;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;

public class PowerMark extends UnlockableContent {
  public static PowerMark INSTANCE;

  public PowerMark() {
    super("power-mark");
  }

  @Override
  public void loadIcon() {}

  @Override
  public void init() {
    super.init();
    fullIcon = Icon.power.getRegion();
    uiIcon = fullIcon;
  }

  @Override
  public boolean isHidden() {
    return true;
  }

  @Override
  public ContentType getContentType() {
    return ContentType.typeid_UNUSED;
  }
}
