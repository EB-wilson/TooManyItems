package tmi.recipe.parser;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.power.ThermalGenerator;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;
import tmi.recipe.types.PowerMark;

public class ThermalGeneratorParser extends ConsumerParser<ThermalGenerator>{
  {excludes.add(GeneratorParser.class);}

  @Override
  public boolean isTarget(Block content) {
    return content instanceof ThermalGenerator;
  }

  @Override
  public Seq<Recipe> parse(ThermalGenerator content) {
    Recipe res = new Recipe(RecipeType.generator);
    res.block = content;

    registerCons(res, content.nonOptionalConsumers);

    res.addProductionPresec(PowerMark.INSTANCE, content.powerProduction);

    for (Block block : Vars.content.blocks()) {
      if (block.attributes.get(content.attribute) <= 0) continue;
      res.addMaterial(block);
    }
    if (content.outputLiquid != null) res.addProductionPresec(content.outputLiquid.liquid, content.outputLiquid.amount);

    return Seq.with(res);
  }
}
