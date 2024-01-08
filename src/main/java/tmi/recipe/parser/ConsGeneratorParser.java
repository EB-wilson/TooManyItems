package tmi.recipe.parser;

import arc.struct.Seq;
import mindustry.world.Block;
import mindustry.world.blocks.power.ConsumeGenerator;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeParser;
import tmi.recipe.RecipeType;
import tmi.recipe.types.PowerMark;

public class ConsGeneratorParser extends ConsumerParser<ConsumeGenerator>{
  {excludes.add(GeneratorParser.class);}

  @Override
  public boolean isTarget(Block content) {
    return content instanceof ConsumeGenerator;
  }

  @Override
  public Seq<Recipe> parse(ConsumeGenerator content) {
    Recipe res = new Recipe(RecipeType.generator)
        .setBlock(getWrap(content))
        .setTime(content.itemDuration);

    registerCons(res, content.consumers);

    res.addProductionPresec(PowerMark.INSTANCE, content.powerProduction);

    if (content.outputLiquid != null){
      res.addProductionPresec(getWrap(content.outputLiquid.liquid), content.outputLiquid.amount);
    }

    return Seq.with(res);
  }
}
