package tmi

import arc.Events
import arc.files.Fi
import arc.util.Time
import mindustry.Vars
import mindustry.entities.bullet.LaserBulletType
import mindustry.game.EventType.ClientLoadEvent
import mindustry.mod.Mod
import mindustry.ui.dialogs.BaseDialog
import mindustry.world.blocks.defense.turrets.LaserTurret
import tmi.recipe.RecipeItemManager
import tmi.recipe.RecipesManager
import tmi.recipe.parser.*
import tmi.ui.Cursor
import tmi.ui.EntryAssigner
import tmi.ui.TmiUI
import tmi.util.KeyBinds
import universe.ui.markdown.Markdown
import universe.ui.markdown.MarkdownStyles

class TooManyItems : Mod() {
  companion object {
    val modFile: Fi by lazy {
      Vars.mods.getMod(TooManyItems::class.java)!!.root
    }

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
    registerDefaultParser()

    Events.on(ClientLoadEvent::class.java) {
      Time.runTask(0f) {
        EntryAssigner.assign()
        Vars.ui.settings.game.checkPref("tmi_button", true)
        Vars.ui.settings.game.checkPref("tmi_items_pane", false)
        Vars.ui.settings.graphics.sliderPref("tmi_gridSize", 150, 50, 300, 10) { i -> i.toString() }
        api.afterInit()

        sample()
      }
    }
  }

  private fun sample(){
    val dialog = BaseDialog("markdown")
    val markdown = Markdown(
$$"""
      一个辅助mod，为您提供快速检索材料，单位，建筑和矿$物等的生产与采$集引导信息，让您不再因为安装了太多mod而不知所措（缩写为 `$`）
      """.trimIndent(),
      MarkdownStyles.defaultMD
    )

    dialog.addCloseButton()
    dialog.cont.add(markdown).grow().pad(20f)
    dialog.show()
  }

  private fun registerDefaultParser() {
    //Parser for the vanilla game factory blocks
    recipesManager.registerParser(GenericCrafterParser())
    recipesManager.registerParser(UnitFactoryParser())
    recipesManager.registerParser(ReconstructorParser())
    recipesManager.registerParser(UnitAssemblerParser())
    recipesManager.registerParser(ConstructorParser())
    recipesManager.registerParser(PumpParser())
    recipesManager.registerParser(SolidPumpParser())
    recipesManager.registerParser(FrackerParser())
    recipesManager.registerParser(DrillParser())
    recipesManager.registerParser(BeamDrillParser())
    recipesManager.registerParser(SeparatorParser())
    recipesManager.registerParser(GeneratorParser())
    recipesManager.registerParser(ConsumeGeneratorParser())
    recipesManager.registerParser(ImpactReactorParser())
    recipesManager.registerParser(HeatGeneratorParser())
    recipesManager.registerParser(ThermalGeneratorParser())
    recipesManager.registerParser(VariableReactorParser())
    recipesManager.registerParser(HeatCrafterParser())
    recipesManager.registerParser(HeatProducerParser())
    recipesManager.registerParser(AttributeCrafterParser())
    recipesManager.registerParser(WallCrafterParser())
    recipesManager.registerParser(ItemTurretParser())
    recipesManager.registerParser(LiquidTurretParser())
    recipesManager.registerParser(PowerTurretParser())
    recipesManager.registerParser(BuildingParser())
  }

  override fun init() {
    Cursor.init()
    binds.load()
    api.init()

    recipesManager.init()

    TmiUI.init()
  }
}
