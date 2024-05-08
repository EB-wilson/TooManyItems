package tmi

import arc.struct.Seq
import arc.util.serialization.Jval
import mindustry.Vars
import mindustry.mod.Mods.LoadedMod
import java.lang.reflect.InvocationTargetException

class ModAPI {
  private val entries = Seq<RecipeEntry>()

  fun init() {
    for (mod in Vars.mods.list()) {
      readJsonAPI(mod)
      loadModJavaEntries(mod)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun loadModJavaEntries(mod: LoadedMod) {
    val modMeta = if (mod.root.child("mod.json").exists()) mod.root.child("mod.json")
    else if (mod.root.child("mod.hjson").exists()) mod.root.child("mod.hjson")
    else if (mod.root.child("plugin.json").exists()) mod.root.child("plugin.json") else mod.root.child("plugin.hjson")

    if (!modMeta.exists()) return
    val meta = Jval.read(modMeta.readString())
    if (!meta.has("recipeEntry")) return

    val entryPath = meta.getString("recipeEntry")
    try {
      val entryClass = mod.loader.loadClass(entryPath) as Class<out RecipeEntry>
      val entry = entryClass.getConstructor().newInstance()
      entries.add(entry)

      entry.init()
    } catch (e: ClassNotFoundException) {
      throw RuntimeException(e)
    } catch (e: NoSuchMethodException) {
      throw RuntimeException(e)
    } catch (e: InvocationTargetException) {
      throw RuntimeException(e)
    } catch (e: InstantiationException) {
      throw RuntimeException(e)
    } catch (e: IllegalAccessException) {
      throw RuntimeException(e)
    }
  }

  private fun loadModScriptEntries(mod: LoadedMod) {
    //TODO: JavaScript API
  }

  private fun readJsonAPI(mod: LoadedMod) {
    val modMeta = mod.root.child("recipes.json")
    if (!modMeta.exists()) return

    //TODO: JSON API
  }

  fun afterInit() {
    for (entry in entries) {
      entry.afterInit()
    }
  }
}
