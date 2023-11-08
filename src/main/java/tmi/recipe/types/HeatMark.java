package tmi.recipe.types;

import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Icon;

public class HeatMark extends UnlockableContent {
  public static HeatMark INSTANCE;

  public HeatMark() {
    super("heat-mark");
  }

  @Override
  public void loadIcon() {}

  @Override
  public void init() {
    super.init();
    fullIcon = Icon.waves.getRegion();
    uiIcon = fullIcon;
  }

  @Override
  public ContentType getContentType() {
    return ContentType.typeid_UNUSED;
  }
}
