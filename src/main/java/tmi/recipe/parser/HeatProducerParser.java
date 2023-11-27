package tmi.recipe.parser;

import arc.struct.Seq;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.heat.HeatProducer;
import mindustry.world.blocks.production.HeatCrafter;
import mindustry.world.meta.StatUnit;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;
import tmi.recipe.types.HeatMark;

public class HeatProducerParser extends ConsumerParser<HeatProducer>{
  {excludes.add(GenericCrafterParser.class);}

  @Override
  public boolean isTarget(Block content) {
    return content instanceof HeatProducer;
  }

  @Override
  public Seq<Recipe> parse(HeatProducer crafter) {
    Recipe res = new Recipe(RecipeType.factory);
    res.setBlock(getWrap(crafter));
    res.setTime(crafter.craftTime);

    registerCons(res, crafter.consumers);

    res.addProductionRaw(HeatMark.INSTANCE, crafter.heatOutput).setFloatFormat();

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
