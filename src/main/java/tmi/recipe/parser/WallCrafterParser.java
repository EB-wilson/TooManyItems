package tmi.recipe.parser;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.production.WallCrafter;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class WallCrafterParser extends ConsumerParser<WallCrafter>{
  @Override
  public boolean isTarget(Block content) {
    return content instanceof WallCrafter;
  }

  @Override
  public Seq<Recipe> parse(WallCrafter content) {
    Recipe res = new Recipe(RecipeType.collecting)
        .setBlock(getWrap(content))
        .setTime(content.drillTime);

    res.addProduction(getWrap(content.output), 1);

    registerCons(res, content.consumers);

    for (Block block : Vars.content.blocks()) {
      if (block.attributes.get(content.attribute) <= 0 || (block instanceof Floor f && f.isDeep())) continue;

      float eff = block.attributes.get(content.attribute);
      res.addMaterialRaw(getWrap(block), block.size)
          .setEfficiency(eff)
          .setAttribute()
          .setFormat(f -> "[#98ffa9]" + Mathf.round(eff*100) + "%");
    }

    return Seq.with(res);
  }
}
