package tmi;

import arc.Events;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import tmi.recipe.RecipeType;
import tmi.recipe.RecipesManager;
import tmi.recipe.parser.*;
import tmi.recipe.types.HeatMark;
import tmi.recipe.types.PowerMark;
import tmi.ui.RecipesDialog;
import tmi.util.Consts;

public class TooManyItems extends Mod {
  public static RecipesManager recipesManager;
  public static ModAPI api;

  public static RecipesDialog recipesDialog;

  public TooManyItems() {
    api = new ModAPI();
    recipesManager = new RecipesManager();

    ConsumerParser.registerVanillaConsumeParser();
    registerRecipeParser();

    Events.on(EventType.ClientLoadEvent.class, e -> Time.runTask(0, () -> {
      api.afterInit();
      recipesManager.mergeGroup();

      recipesDialog.show();
    }));
  }

  private void registerRecipeParser() {
    recipesManager.registerParser(new GenericCrafterParser());
    recipesManager.registerParser(new UnitFactoryParser());
    recipesManager.registerParser(new ReconstructorParser());
    recipesManager.registerParser(new UnitAssemblerParser());
    recipesManager.registerParser(new PumpParser());
    recipesManager.registerParser(new SolidPumpParser());
    recipesManager.registerParser(new DrillParser());
    recipesManager.registerParser(new BeamDrillParser());
    recipesManager.registerParser(new SeparatorParser());
    recipesManager.registerParser(new GeneratorParser());
    recipesManager.registerParser(new ConsGeneratorParser());
    recipesManager.registerParser(new HeatGeneratorParser());
    recipesManager.registerParser(new ThermalGeneratorParser());
    recipesManager.registerParser(new VariableReactorParser());
    recipesManager.registerParser(new HeatCrafterParser());
    recipesManager.registerParser(new HeatProducerParser());
    recipesManager.registerParser(new AttributeCrafterParser());
  }

  @Override
  public void init() {
    Consts.load();
    RecipeType.init();

    recipesDialog = new RecipesDialog();

    api.init();

    recipesManager.init();
  }

  @Override
  public void loadContent() {
    super.loadContent();

    PowerMark.INSTANCE = new PowerMark();
    HeatMark.INSTANCE = new HeatMark();
  }
}
