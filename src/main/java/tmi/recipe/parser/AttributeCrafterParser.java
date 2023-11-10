package tmi.recipe.parser;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.production.AttributeCrafter;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class AttributeCrafterParser extends ConsumerParser<AttributeCrafter>{
  {excludes.add(GenericCrafterParser.class);}

  @Override
  public boolean isTarget(Block content) {
    return content instanceof AttributeCrafter;
  }

  @Override
  public Seq<Recipe> parse(AttributeCrafter crafter) {
    Recipe res = new Recipe(RecipeType.factory);
    res.setBlock(crafter);
    res.setTime(crafter.craftTime);

    registerCons(res, crafter.consumers);

    for (Block block : Vars.content.blocks()) {
      if (block.attributes.get(crafter.attribute) <= 0 || (block instanceof Floor f && f.isDeep())) continue;

      float eff = Math.min(crafter.boostScale*crafter.size*crafter.size*block.attributes.get(crafter.attribute), crafter.maxBoost);
      res.addMaterial(block, crafter.size*crafter.size)
          .setAttribute()
          .setOptionalCons(crafter.baseEfficiency < 0.001f)
          .setEfficiency(eff)
          .setFormat(f -> "[#98ffa9]" + (crafter.baseEfficiency > 0.001f? "+": "") + Mathf.round(eff*100) + "%");
    }

    res.efficiency = Recipe.getDefaultEff(crafter.baseEfficiency);

    if (crafter.outputItems == null) {
      if (crafter.outputItem != null) res.addProduction(crafter.outputItem.item, crafter.outputItem.amount);
    }
    else {
      for (ItemStack item : crafter.outputItems) {
        res.addProduction(item.item, item.amount);
      }
    }

    if (crafter.outputLiquids == null) {
      if (crafter.outputLiquid != null) res.addProductionPresec(crafter.outputLiquid.liquid, crafter.outputLiquid.amount);
    }
    else {
      for (LiquidStack liquid : crafter.outputLiquids) {
        res.addProductionPresec(liquid.liquid, liquid.amount);
      }
    }

    return Seq.with(res);
  }
}
