package tmi.recipe.parser;

import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.world.Block;
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.blocks.power.HeaterGenerator;
import mindustry.world.meta.StatUnit;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeParser;
import tmi.recipe.RecipeType;
import tmi.recipe.types.HeatMark;
import tmi.recipe.types.PowerMark;

public class HeatGeneratorParser extends ConsumerParser<HeaterGenerator>{
  {
    excludes.add(GeneratorParser.class);
    excludes.add(ConsGeneratorParser.class);
  }

  @Override
  public boolean isTarget(Block content) {
    return content instanceof HeaterGenerator;
  }

  @Override
  public Seq<Recipe> parse(HeaterGenerator content) {
    Recipe res = new Recipe(RecipeType.generator);
    res.setBlock(getWrap(content));
    res.setTime(content.itemDuration);

    registerCons(res, content.consumers);

    res.addProductionPresec(PowerMark.INSTANCE, content.powerProduction);

    if (content.heatOutput > 0){
      res.addProductionRaw(HeatMark.INSTANCE, content.heatOutput).setFloatFormat();
    }

    if (content.outputLiquid != null){
      res.addProductionPresec(getWrap(content.outputLiquid.liquid), content.outputLiquid.amount);
    }

    return Seq.with(res);
  }
}
