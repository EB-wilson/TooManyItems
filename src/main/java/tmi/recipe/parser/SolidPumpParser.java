package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.production.SolidPump;
import mindustry.world.consumers.Consume;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class SolidPumpParser extends ConsumerParser<SolidPump>{
  @Override
  public boolean isTarget(Block content) {
    return content instanceof SolidPump;
  }

  @Override
  public Seq<Recipe> parse(SolidPump pump) {
    Recipe res = new Recipe(RecipeType.collecting);
    res.setBlock(pump);
    res.addProduction(pump.result);

    registerCons(res, pump.consumers);

    for (Block block : Vars.content.blocks()) {
      if (block.attributes.get(pump.attribute) <= 0 || (block instanceof Floor f && f.isDeep())) continue;

      float eff = pump.size*pump.size*block.attributes.get(pump.attribute);
      res.addMaterial(block)
          .setOptionalCons(pump.baseEfficiency > 0.001f)
          .setEfficiency(pump.baseEfficiency + eff)
          .setFormat(f -> "[#98ffa9]" + (pump.baseEfficiency > 0.001f? "+": "") + Mathf.round(eff*100) + "%");
    }

    return Seq.with(res);
  }
}
