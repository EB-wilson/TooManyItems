package tmi

import arc.Core
import arc.Events
import arc.struct.IntMap
import arc.struct.ObjectFloatMap
import arc.struct.ObjectIntMap
import arc.util.Time
import mindustry.Vars
import mindustry.game.EventType.ClientLoadEvent
import mindustry.mod.Mod
import tmi.recipe.RecipeItemManager
import tmi.recipe.RecipeType
import tmi.recipe.RecipesManager
import tmi.recipe.parser.*
import tmi.ui.BatchBalanceDialog
import tmi.ui.Cursor
import tmi.ui.EntryAssigner
import tmi.ui.RecipesDialog
import tmi.ui.designer.SchematicDesignerDialog
import tmi.util.KeyBinds

class TooManyItems : Mod() {
  companion object {
    var recipesManager: RecipesManager = RecipesManager()
    var itemsManager: RecipeItemManager = RecipeItemManager()
    var api: ModAPI = ModAPI()

    val recipesDialog by lazy { RecipesDialog() }
    val schematicDesigner by lazy { SchematicDesignerDialog() }
    val batchBalance by lazy { BatchBalanceDialog() }
    val binds by lazy { KeyBinds() }
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

    recipesDialog.build()
    schematicDesigner.build()
  }
}

operator fun <K> ObjectIntMap<K>.set(key: K, value: Int) = put(key, value)
operator fun <K> ObjectFloatMap<K>.set(key: K, value: Float) = put(key, value)

operator fun <V> IntMap<V>.set(key: Int, value: V): V = put(key, value)


