package tmi.recipe.parser;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.production.Fracker;
import mindustry.world.blocks.production.SolidPump;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class FrackerParser extends ConsumerParser<Fracker>{
  {
    excludes.add(PumpParser.class);
    excludes.add(SolidPumpParser.class);
  }

  @Override
  public boolean isTarget(Block content) {
    return content instanceof Fracker;
  }

  @Override
  public Seq<Recipe> parse(Fracker fracker) {
    Recipe res = new Recipe(RecipeType.collecting);
    res.setBlock(fracker);
    res.setTime(fracker.consumeTime);
    res.addProduction(fracker.result);

    registerCons(res, fracker.consumers);

    for (Block block : Vars.content.blocks()) {
      if (block.attributes.get(fracker.attribute) <= 0 || (block instanceof Floor f && f.isDeep())) continue;

      float eff = block.attributes.get(fracker.attribute);
      res.addMaterial(block)
          .setOptionalCons(fracker.baseEfficiency > 0.001f)
          .setEfficiency(fracker.baseEfficiency + eff)
          .setFormat(f -> "[#98ffa9]" + (fracker.baseEfficiency > 0.001f? "+": "") + Mathf.round(eff*100) + "%");
    }

    return Seq.with(res);
  }
}
