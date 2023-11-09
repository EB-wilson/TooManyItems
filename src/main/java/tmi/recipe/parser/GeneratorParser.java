package tmi.recipe.parser;

import arc.struct.Seq;
import mindustry.world.Block;
import mindustry.world.blocks.power.PowerGenerator;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;
import tmi.recipe.types.PowerMark;

public class GeneratorParser extends ConsumerParser<PowerGenerator>{
  @Override
  public boolean isTarget(Block content) {
    return content instanceof PowerGenerator;
  }

  @Override
  public Seq<Recipe> parse(PowerGenerator content) {
    Recipe res = new Recipe(RecipeType.generator);
    res.block = content;

    registerCons(res, content.consumers);

    res.addProductionPresec(PowerMark.INSTANCE, content.powerProduction);

    return Seq.with(res);
  }
}
