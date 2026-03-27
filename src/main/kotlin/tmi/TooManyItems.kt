package tmi

import arc.Events
import arc.files.Fi
import arc.util.Time
import mindustry.Vars
import mindustry.game.EventType.ClientLoadEvent
import mindustry.mod.Mod
import tmi.recipe.RecipeItemManager
import tmi.recipe.RecipesManager
import tmi.recipe.parser.*
import tmi.ui.Cursor
import tmi.ui.DocumentDialog
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

        TmiUI.document.showDocument(
          "test",
          MarkdownStyles.defaultMD,
          $$"""
          # H1
          ## H2
          ### H3
          #### H4
          ##### H5
          ###### H6
            
          text test
          
          中文测试
          
          link test: [link test](link)
          
          link ref test: [link ref][def]
          
          multi link test: link1 - [link 1](link) link2 - [link 2](link) link3 - [link 3](link) link4 - [link 4](link) link5 - [link 5](link) link6 - [link 6](link) 
          
          thematic break test:
          
          ---
          
          code test: `code test`
          
          mutli code test: `code 1` text 1 `code 2` text 2 `code 3` text 3 `code 4` text 4 `code 5` text 5 `code 6` text 6 `code 7` text 7
         
          emp test: *emphasize*
          
          strong test: **strong**
          
          strong emp test: ***strong emphasize***
          
          curtain test: $curtain 1$ $curtain 2 with `code`$
          
          ins test: ++under line test 1 - under line test 2++
          
          delete line test: ~~delete line test 1 - delete line test 2~~
          
          soft break test: line1 
          line2
          
          hard break test: line1  
          line2
          
          block quote test:
          
          > block quote line1  
          > block quote line2  
          > block quote line3  
          > 
          > block quote line4 (paragraph)
            
          list test:
          
          - item 1
          - item 2
          - item 3
            - sub item 1
            - sub item 2
            - sub item 3
              - sub sub item 1
              - sub sub item 2
              - sub sub item 3
            - sub item 4
          - item 4
          - item 5
          
          ordered list test:
          
          1. item 1
          2. item 2
          3. item 3
             1. sub item 1
             2. sub item 2
             3. sub item 3
                1. sub sub item 1
                2. sub sub item 2
                3. sub sub item 3
             4. sub item 4
          4. item 4
          
          code block test:
          
              code line 1
              code line 2
              code line 3
              
          fenced code block test:
          
          ```
          code line 1
          code line 2
          code line 3
          ```
          
          image test: 
          
          ![atlas test](atlas:item-copper)
          ![resource test](resource://sprites/ui/a_z.png)
          ![base64 test](data:image/png;base64,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)
          ![https test](https://github.com/EB-wilson/TooManyItems/blob/master/assets/git/img/statastic.png?raw=true){width=300 scaling=fillX}
          
          table test:
          
          |  table head 1  | table head 2   | table head 3     | table head 3     | table head 3     |
          |:---------------|:--------------:|-----------------:|-----------------:|-----------------:|
          |  `row1 col1`   |  row1 col2     |  row1 col3       |  row1 col3       |  row1 col3       |
          |  row2 col1     |  *row2 col2*   |  row2 col3       |  row2 col3       |  row2 col3       |
          |  *row3 col1*   |  row3 col2     |  **row3 col3**   |  **row3 col3**   |  **row3 col3**   |
          
          [def]: https://github.com
          """.trimIndent()
        )
      }
    }
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
