package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.core.UI;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.production.Drill;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeLiquidBase;
import mindustry.world.meta.StatUnit;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;
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
        r.setBlock(getWrap(content));
        r.setTime(content.getDrillTime(drop.itemDrop));
        r.addProduction(getWrap(drop.itemDrop));

        if(content.liquidBoostIntensity != 1){
          registerCons(r, Seq.with(content.consumers).select(e -> !(e.optional && e instanceof ConsumeLiquidBase && e.booster)).toArray(Consume.class));
          if(content.findConsumer(f -> f instanceof ConsumeLiquidBase) instanceof ConsumeLiquidBase consBase) {
            registerCons(r, s -> s.setEfficiency(content.liquidBoostIntensity)
                .setOptionalCons()
                .setFormat(f -> (f*60 > 1000? UI.formatAmount((long) (f*60)): Strings.autoFixed(f*60, 2)) + "/" + StatUnit.seconds.localized() + "\n[#98ffa9]+" + Mathf.round(content.liquidBoostIntensity*100) + "%"), consBase);
          }
        }
        else{
          registerCons(r, content.consumers);
        }

        return r;
      });

      float realDrillTime = content.getDrillTime(drop.itemDrop);
      recipe.addMaterial(getWrap(drop), content.size*content.size)
          .setEfficiency(content.drillTime / realDrillTime)
          .setAttribute();
    }

    return res.values().toSeq();
  }
}
