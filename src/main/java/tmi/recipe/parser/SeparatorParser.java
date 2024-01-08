package tmi.recipe.parser;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.Separator;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class SeparatorParser extends ConsumerParser<Separator>{
  @Override
  public boolean isTarget(Block content) {
    return content instanceof Separator;
  }

  @Override
  public Seq<Recipe> parse(Separator content) {
    Recipe res = new Recipe(RecipeType.factory)
        .setBlock(getWrap(content))
        .setTime(content.craftTime);

    registerCons(res, content.consumers);

    float n = 0;
    for (ItemStack stack : content.results) {
      n += stack.amount;
    }
    float fn = n;
    for (ItemStack item : content.results) {
      res.addProduction(getWrap(item.item), item.amount/n).setFormat(f -> Mathf.round(item.amount/fn*100) + "%");
    }

    return Seq.with(res);
  }
}
