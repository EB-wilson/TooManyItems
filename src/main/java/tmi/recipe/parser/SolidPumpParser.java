package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;
import mindustry.world.blocks.production.Pump;
import mindustry.world.blocks.production.SolidPump;
import mindustry.world.consumers.Consume;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class PumpParser extends ConsumerParser<SolidPump>{
  @Override
  public boolean isTarget(Block content) {
    return content instanceof SolidPump;
  }

  @Override
  public Seq<Recipe> parse(SolidPump pump) {
    Recipe res = new Recipe(RecipeType.collecting);
    res.block = pump;
    res.productions.add(pump.result);

    for (Consume consume : pump.nonOptionalConsumers) {
      for (ObjectMap.Entry<Boolf<Consume>, Cons2<Recipe, Consume>> entry : vanillaConsParser) {
        if (entry.key.get(consume)) entry.value.get(res, consume);
      }
    }

    if (pump.baseEfficiency <= 0.0001f) {
      for (Block block : Vars.content.blocks()) {
        if (block.attributes.get(pump.attribute) <= 0) continue;
        res.materials.add(block);
      }
    }

    return res;
  }
}
