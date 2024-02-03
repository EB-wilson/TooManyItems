package tmi.recipe.parser;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.production.SolidPump;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class SolidPumpParser extends ConsumerParser<SolidPump>{
  {excludes.add(PumpParser.class);}

  @Override
  public boolean isTarget(Block content) {
    return content instanceof SolidPump;
  }

  @Override
  public Seq<Recipe> parse(SolidPump pump) {
    Recipe res = new Recipe(RecipeType.collecting)
        .setEfficiency(Recipe.getDefaultEff(pump.baseEfficiency))
        .setBlock(getWrap(pump))
        .setTime(pump.consumeTime);

    res.addProductionPresec(getWrap(pump.result), pump.pumpAmount);

    registerCons(res, pump.consumers);

    for (Block block : Vars.content.blocks()) {
      if (block.attributes.get(pump.attribute) <= 0 || (block instanceof Floor f && f.isDeep())) continue;

      float eff = block.attributes.get(pump.attribute);
      res.addMaterialRaw(getWrap(block), pump.size*pump.size)
          .setOptionalCons(pump.baseEfficiency > 0.001f)
          .setEfficiency(eff)
          .setAttribute()
          .setFormat(f -> "[#98ffa9]" + (pump.baseEfficiency > 0.001f? "+": "") + Mathf.round(eff*100) + "%");
    }

    return Seq.with(res);
  }
}
