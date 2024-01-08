package tmi.recipe.parser;

import arc.struct.Seq;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class GenericCrafterParser extends ConsumerParser<GenericCrafter>{
  @Override
  public boolean isTarget(Block content) {
    return content instanceof GenericCrafter;
  }

  @Override
  public Seq<Recipe> parse(GenericCrafter crafter) {
    Recipe res = new Recipe(RecipeType.factory)
        .setBlock(getWrap(crafter))
        .setTime(crafter.craftTime);

    registerCons(res, crafter.consumers);

    if (crafter.outputItems == null) {
      if (crafter.outputItem != null) res.addProduction(getWrap(crafter.outputItem.item), crafter.outputItem.amount);
    }
    else {
      for (ItemStack item : crafter.outputItems) {
        res.addProduction(getWrap(item.item), item.amount);
      }
    }

    if (crafter.outputLiquids == null) {
      if (crafter.outputLiquid != null) res.addProductionPresec(getWrap(crafter.outputLiquid.liquid), crafter.outputLiquid.amount);
    }
    else {
      for (LiquidStack liquid : crafter.outputLiquids) {
        res.addProductionPresec(getWrap(liquid.liquid), liquid.amount);
      }
    }

    return Seq.with(res);
  }
}
