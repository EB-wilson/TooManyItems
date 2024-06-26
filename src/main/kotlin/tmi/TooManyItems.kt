package tmi

import arc.Core
import arc.Events
import arc.func.*
import arc.scene.event.ClickListener.clicked
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.layout.Table
import arc.struct.*
import arc.util.Align
import arc.util.Time
import mindustry.Vars
import mindustry.content.Blocks.container
import mindustry.content.Fx.v
import mindustry.content.Items
import mindustry.game.EventType.ClientLoadEvent
import mindustry.gen.Icon
import mindustry.mod.Mod
import mindustry.type.Item
import mindustry.ui.Fonts.icon
import mindustry.ui.Styles
import mindustry.world.Block
import mindustry.world.meta.Env.any
import rhino.ScriptRuntime
import tmi.TooManyItems.Companion.api
import tmi.TooManyItems.Companion.binds
import tmi.TooManyItems.Companion.recipesManager
import tmi.recipe.RecipeItemManager
import tmi.recipe.RecipeType
import tmi.recipe.RecipesManager
import tmi.recipe.parser.*
import tmi.recipe.types.RecipeItem
import tmi.ui.Cursor
import tmi.ui.EntryAssigner
import tmi.ui.RecipesDialog
import tmi.ui.TmiUI
import tmi.ui.TmiUI.recipesDialog
import tmi.ui.TmiUI.schematicDesigner
import tmi.ui.designer.*
import tmi.util.Consts
import tmi.util.KeyBinds
import tmi.util.vec1

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
        Vars.ui.settings.graphics.sliderPref("tmi_gridSize", 150, 50, 300, 10) { i: Int -> i.toString() }
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
operator fun <P, R> Func<P, R>.invoke(p: P): R = get(p)
operator fun <P1, P2, R> Func2<P1, P2, R>.invoke(p1: P1, p2: P2): R = get(p1, p2)
operator fun <P1, P2, P3, R> Func3<P1, P2, P3, R>.invoke(p1: P1, p2: P2, p3: P3): R = get(p1, p2, p3)
operator fun <R> Prov<R>.invoke(): R = get()
fun <P, T: Throwable> ConsT<P, T>.invoke(p: P) = get(p)
