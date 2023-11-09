package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.production.Drill;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeLiquidBase;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

import static tmi.util.Consts.markerTile;

public class DrillParser extends ConsumerParser<Drill>{
  protected ObjectSet<Floor> itemDrops = new ObjectSet<>();

  @Override
  public void init() {
    for (Block block : Vars.content.blocks()) {
      if (block instanceof Floor f && f.itemDrop != null && !f.wallOre) itemDrops.add(f);
    }
  }

  @Override
  public boolean isTarget(Block content) {
    return content instanceof Drill;
  }

  @Override
  public Seq<Recipe> parse(Drill content) {
    ObjectMap<Item, Recipe> res = new ObjectMap<>();

    for (Floor drop : itemDrops) {
      if (drop instanceof OreBlock) markerTile.setOverlay(drop);
      else markerTile.setFloor(drop);

      if (!content.canMine(markerTile)) continue;

      Recipe recipe = res.get(drop.itemDrop, () -> {
        Recipe r = new Recipe(RecipeType.collecting);
        r.block = content;
        r.addProduction(drop.itemDrop);

        if(content.liquidBoostIntensity != 1){
          registerCons(r, Seq.with(content.consumers).select(e -> !(e.optional && e instanceof ConsumeLiquidBase)).toArray(Consume.class));
          if(content.findConsumer(f -> f instanceof ConsumeLiquidBase) instanceof ConsumeLiquidBase consBase) {
            registerCons(r, content.liquidBoostIntensity, consBase);
          }
        }
        else{
          registerCons(r, content.consumers);
        }

        return r;
      });

      recipe.addMaterial(drop);
    }

    return res.values().toSeq();
  }
}
