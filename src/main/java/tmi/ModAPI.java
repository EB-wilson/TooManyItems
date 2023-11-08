package tmi;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.mod.Mods;

import java.lang.reflect.InvocationTargetException;

public class ModAPI {
  private final Seq<RecipeEntry> entries = new Seq<>();

  @SuppressWarnings("unchecked")
  public void init() {
    for (Mods.LoadedMod mod : Vars.mods.list()) {
      readJsonAPI(mod);

      Fi modMeta = mod.root.child("mod.json").exists()? mod.root.child("mod.json"):
          mod.root.child("mod.hjson").exists()? mod.root.child("mod.hjson"):
          mod.root.child("plugin.json").exists()? mod.root.child("plugin.json"):
          mod.root.child("plugin.hjson");

      if (!modMeta.exists()) continue;
      Jval meta = Jval.read(modMeta.readString());
      if (!meta.has("recipeEntry")) continue;

      String entryPath = meta.getString("recipeEntry");
      try {
        Class<? extends RecipeEntry> entryClass = (Class<? extends RecipeEntry>) mod.loader.loadClass(entryPath);
        RecipeEntry entry = entryClass.getConstructor().newInstance();
        entries.add(entry);

        entry.init();
      } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | InstantiationException |
               IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void afterInit(){
    for (RecipeEntry entry : entries) {
      entry.afterInit();
    }
  }

  private void readJsonAPI(Mods.LoadedMod mod) {

  }
}
