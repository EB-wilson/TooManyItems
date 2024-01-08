package tmi.recipe.parser;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.production.Fracker;
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
    Recipe res = new Recipe(RecipeType.collecting)
        .setEfficiency(Recipe.getDefaultEff(fracker.baseEfficiency))
        .setBlock(getWrap(fracker))
        .setTime(fracker.consumeTime);

    res.addProductionPresec(getWrap(fracker.result), fracker.pumpAmount);

    registerCons(res, fracker.consumers);

    for (Block block : Vars.content.blocks()) {
      if (block.attributes.get(fracker.attribute) <= 0 || (block instanceof Floor f && f.isDeep())) continue;

      float eff = block.attributes.get(fracker.attribute);
      res.addMaterialRaw(getWrap(block), fracker.size*fracker.size)
          .setOptionalCons(fracker.baseEfficiency > 0.001f)
          .setEfficiency(eff)
          .setAttribute()
          .setFormat(f -> "[#98ffa9]" + (fracker.baseEfficiency > 0.001f? "+": "") + Mathf.round(eff*100) + "%");
    }

    return Seq.with(res);
  }
}
