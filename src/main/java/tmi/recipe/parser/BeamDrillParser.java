package tmi.recipe.parser;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.production.BeamDrill;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

import static tmi.util.Consts.markerTile;

public class BeamDrillParser extends ConsumerParser<BeamDrill>{
  protected ObjectSet<Floor> itemDrops = new ObjectSet<>();

  @Override
  public void init() {
    for (Block block : Vars.content.blocks()) {
      if (block instanceof Floor f && f.wallOre && f.itemDrop != null) itemDrops.add(f);
    }
  }

  @Override
  public boolean isTarget(Block content) {
    return content instanceof BeamDrill;
  }

  @Override
  public Seq<Recipe> parse(BeamDrill content) {
    Seq<Recipe> res = new Seq<>();

    for (Floor drop : itemDrops) {
      if (drop instanceof OreBlock) markerTile.setOverlay(drop);
      else markerTile.setFloor(drop);

      if (drop.itemDrop.hardness > content.tier) continue;

      Recipe recipe = new Recipe(RecipeType.collecting);
      recipe.block = content;
      recipe.addMaterial(drop);
      recipe.addProduction(drop.itemDrop);

      registerCons(recipe, content.consumers);

      res.add(recipe);
    }

    return res;
  }
}
