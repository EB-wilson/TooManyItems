package tmi;

import arc.files.Fi;
import arc.struct.Seq;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.mod.Mods;
import mindustry.mod.Scripts;
import rhino.ScriptRuntime;

import java.lang.reflect.InvocationTargetException;

public class ModAPI {
  private final Seq<RecipeEntry> entries = new Seq<>();

  public void init() {
    for (Mods.LoadedMod mod : Vars.mods.list()) {
      readJsonAPI(mod);
      loadModJavaEntries(mod);
    }
  }

  @SuppressWarnings("unchecked")
  private void loadModJavaEntries(Mods.LoadedMod mod) {
    Fi modMeta = mod.root.child("mod.json").exists()? mod.root.child("mod.json"):
        mod.root.child("mod.hjson").exists()? mod.root.child("mod.hjson"):
        mod.root.child("plugin.json").exists()? mod.root.child("plugin.json"):
        mod.root.child("plugin.hjson");

    if (!modMeta.exists()) return;
    Jval meta = Jval.read(modMeta.readString());
    if (!meta.has("recipeEntry")) return;

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

  private void loadModScriptEntries(Mods.LoadedMod mod){
    Vars.mods.getScripts()
  }

  private void readJsonAPI(Mods.LoadedMod mod) {
    Fi modMeta = mod.root.child("recipes.json");
    if (!modMeta.exists()) return;

    //TODO: JSON API
  }

  public void afterInit(){
    for (RecipeEntry entry : entries) {
      entry.afterInit();
    }
  }
}
