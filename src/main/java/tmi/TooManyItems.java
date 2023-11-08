package tmi;

import arc.Events;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.type.PayloadStack;
import mindustry.world.consumers.ConsumeItems;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumeLiquids;
import mindustry.world.consumers.ConsumePayloads;
import tmi.recipe.RecipeType;
import tmi.recipe.RecipesManager;
import tmi.recipe.parser.*;
import tmi.ui.RecipesDialog;
import tmi.util.Consts;

import static tmi.recipe.parser.ConsumerParser.registerVanillaConsParser;

public class TooManyItems extends Mod {
  public static RecipesManager recipesManager;
  public static ModAPI api;

  public static RecipesDialog recipesDialog;

  public TooManyItems() {
    api = new ModAPI();
    recipesManager = new RecipesManager();

    registerConsumeParser();
    registerRecipeParser();

    Events.on(EventType.ClientLoadEvent.class, e -> Time.runTask(0, () -> {
      api.afterInit();
      recipesManager.mergeGroup();

      recipesDialog.show();
    }));
  }

  private static void registerConsumeParser() {
    //items
    registerVanillaConsParser(c -> c instanceof ConsumeItems, (recipe, consume) -> {
      for (ItemStack item : ((ConsumeItems) consume).items) {
        recipe.addMaterial(item.item, item.amount);
      }
    });

    //liquids
    registerVanillaConsParser(c -> c instanceof ConsumeLiquids, (recipe, consume) -> {
      for (LiquidStack liquid : ((ConsumeLiquids) consume).liquids) {
        recipe.addMaterialPresec(liquid.liquid, liquid.amount);
      }
    });
    registerVanillaConsParser(c -> c instanceof ConsumeLiquid, (recipe, consume) -> {
      recipe.addMaterialPresec(((ConsumeLiquid) consume).liquid,  ((ConsumeLiquid) consume).amount);
    });

    //payloads
    registerVanillaConsParser(c -> c instanceof ConsumePayloads, (recipe, consume) -> {
      for (PayloadStack stack : ((ConsumePayloads) consume).payloads) {
        if (stack.amount > 1) recipe.addMaterial(stack.item, stack.amount);
        else recipe.addMaterial(stack.item);
      }
    });
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
  }

  @Override
  public void init() {
    Consts.load();
    RecipeType.init();

    recipesDialog = new RecipesDialog();

    api.init();

    recipesManager.init();
  }
}
