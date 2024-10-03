package tmi

import arc.Events
import arc.func.*
import arc.graphics.Gl.lines
import arc.struct.*
import arc.util.Time
import arc.util.io.Writes
import mindustry.Vars
import mindustry.game.EventType.ClientLoadEvent
import mindustry.mod.Mod
import tmi.recipe.RecipeItemManager
import tmi.recipe.RecipesManager
import tmi.recipe.parser.*
import tmi.ui.Cursor
import tmi.ui.EntryAssigner
import tmi.ui.TmiUI
import tmi.ui.designer.*
import tmi.util.KeyBinds
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class TooManyItems : Mod() {
  companion object {
    @JvmField
    var recipesManager: RecipesManager = RecipesManager()
    @JvmField
    var itemsManager: RecipeItemManager = RecipeItemManager()
    @JvmField
    var api: ModAPI = ModAPI()
    @JvmField
    val binds = KeyBinds()
  }

  init {
    ConsumerParser.registerVanillaConsumeParser()
    registerRecipeParser()

    Events.on(ClientLoadEvent::class.java) {
      Time.runTask(0f) {
        EntryAssigner.assign()
        Vars.ui.settings.game.checkPref("tmi_button", true)
        Vars.ui.settings.game.checkPref("tmi_items_pane", false)
        Vars.ui.settings.graphics.sliderPref("tmi_gridSize", 150, 50, 300, 10) { i -> i.toString() }
        api.afterInit()
      }
    }
  }

  private fun registerRecipeParser() {
    //几乎所有的原版游戏工厂方块的分析工具
    recipesManager.registerParser(GenericCrafterParser())
    recipesManager.registerParser(UnitFactoryParser())
    recipesManager.registerParser(ReconstructorParser())
    recipesManager.registerParser(UnitAssemblerParser())
    recipesManager.registerParser(PumpParser())
    recipesManager.registerParser(SolidPumpParser())
    recipesManager.registerParser(FrackerParser())
    recipesManager.registerParser(DrillParser())
    recipesManager.registerParser(BeamDrillParser())
    recipesManager.registerParser(SeparatorParser())
    recipesManager.registerParser(GeneratorParser())
    recipesManager.registerParser(ConsGeneratorParser())
    recipesManager.registerParser(HeatGeneratorParser())
    recipesManager.registerParser(ThermalGeneratorParser())
    recipesManager.registerParser(VariableReactorParser())
    recipesManager.registerParser(HeatCrafterParser())
    recipesManager.registerParser(HeatProducerParser())
    recipesManager.registerParser(AttributeCrafterParser())
    recipesManager.registerParser(WallCrafterParser())
  }

  override fun init() {
    Cursor.init()

    binds.load()
    api.init()

    recipesManager.init()

    TmiUI.init()
  }
}

operator fun <K> ObjectIntMap<K>.set(key: K, value: Int) = put(key, value)
operator fun <K> ObjectFloatMap<K>.set(key: K, value: Float) = put(key, value)

operator fun <V> IntMap<V>.set(key: Int, value: V): V = put(key, value)

operator fun <K, V> ObjectMap<K, V>.set(key: K, value: V): V = put(key, value)

operator fun <P> Cons<P>.invoke(p: P) = get(p)
operator fun <P1, P2> Cons2<P1, P2>.invoke(p1: P1, p2: P2) = get(p1, p2)
operator fun <P1, P2, P3> Cons3<P1, P2, P3>.invoke(p1: P1, p2: P2, p3: P3) = get(p1, p2, p3)
operator fun <P1, P2, P3, P4> Cons4<P1, P2, P3, P4>.invoke(p1: P1, p2: P2, p3: P3, p4: P4) = get(p1, p2, p3, p4)
operator fun <P, R> Func<P, R>.invoke(p: P): R = get(p)
operator fun <P1, P2, R> Func2<P1, P2, R>.invoke(p1: P1, p2: P2): R = get(p1, p2)
operator fun <P1, P2, P3, R> Func3<P1, P2, P3, R>.invoke(p1: P1, p2: P2, p3: P3): R = get(p1, p2, p3)
operator fun <R> Prov<R>.invoke(): R = get()
operator fun <P, T: Throwable> ConsT<P, T>.invoke(p: P) = get(p)

fun Writes.b(vararg bytes: Int) = bytes.forEach { b(it) }
fun Writes.s(vararg shorts: Int) = shorts.forEach { s(it) }
fun Writes.i(vararg ints: Int) = ints.forEach { i(it) }
fun Writes.l(vararg longs: Long) = longs.forEach { l(it) }
fun Writes.f(vararg floats: Float) = floats.forEach { f(it) }
fun Writes.d(vararg bytes: Double) = bytes.forEach { d(it) }
fun Writes.str(vararg strings: String) = strings.forEach { str(it) }
fun Writes.bool(vararg bools: Boolean) = bools.forEach { bool(it) }

inline fun <K, V> ObjectMap<K, V>.forEach(block: (K, V) -> Unit){
  for (e in this){
    block(e.key, e.value)
  }
}
