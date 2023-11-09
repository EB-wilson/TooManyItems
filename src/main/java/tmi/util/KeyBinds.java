package tmi.util;

import arc.Core;
import arc.input.KeyCode;

public class KeyBinds {
  public KeyCode hotKey = KeyCode.controlLeft;

  public void load(){
    hotKey = KeyCode.byOrdinal(Core.settings.getInt("tmi_hotkey", KeyCode.controlLeft.ordinal()));
  }

  public void reset(String name) {
    switch (name) {
      case "hot_key" -> hotKey = KeyCode.controlLeft;
    }

    save();
  }

  public void save() {
    Core.settings.put("tmi_hotkey", hotKey.ordinal());
  }

  public void resetAll() {
    hotKey = KeyCode.controlLeft;
    save();
  }
}
