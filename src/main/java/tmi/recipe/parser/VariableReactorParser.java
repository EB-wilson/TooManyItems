package tmi.recipe.parser;

import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.world.Block;
import mindustry.world.blocks.power.ConsumeGenerator;
import mindustry.world.blocks.power.VariableReactor;
import mindustry.world.meta.StatUnit;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;
import tmi.recipe.types.HeatMark;
import tmi.recipe.types.PowerMark;

public class VariableReactorParser extends ConsumerParser<VariableReactor>{
  {excludes.add(GeneratorParser.class);}

  @Override
  public boolean isTarget(Block content) {
    return content instanceof VariableReactor;
  }

  @Override
  public Seq<Recipe> parse(VariableReactor content) {
    Recipe res = new Recipe(RecipeType.generator);
    res.setBlock(getWrap(content));

    registerCons(res, content.consumers);

    res.addProductionPresec(PowerMark.INSTANCE, content.powerProduction);

    if (content.maxHeat > 0){
      res.addProduction(HeatMark.INSTANCE, content.maxHeat);
    }

    return Seq.with(res);
  }
}
