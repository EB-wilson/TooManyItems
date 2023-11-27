package tmi.recipe.parser;

import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.production.Pump;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static tmi.util.Consts.markerTile;

public class PumpParser extends ConsumerParser<Pump>{
  private static final Method canPump;

  static {
    try {
      canPump = Pump.class.getDeclaredMethod("canPump", Tile.class);
      canPump.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  protected final ObjectSet<Floor> floorDrops = new ObjectSet<>();

  @Override
  public void init() {
    for (Block block : Vars.content.blocks()) {
      if (block instanceof Floor f && f.liquidDrop != null) floorDrops.add(f);
    }
  }

  @Override
  public boolean isTarget(Block content) {
    return content instanceof Pump;
  }

  @Override
  public Seq<Recipe> parse(Pump content) {
    ObjectMap<Liquid, Recipe> res = new ObjectMap<>();
    for (Floor drop : floorDrops) {
      markerTile.setFloor(drop);
      try {
        if (!((boolean) canPump.invoke(content, markerTile))) continue;
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }

      Recipe recipe = res.get(drop.liquidDrop, () -> {
        Recipe r = new Recipe(RecipeType.collecting);
        r.setBlock(getWrap(content));
        r.setTime(content.consumeTime);
        r.addProductionPresec(getWrap(drop.liquidDrop), content.pumpAmount);
        registerCons(r, content.consumers);

        return r;
      });

      recipe.addMaterial(getWrap(drop), content.size*content.size)
          .setAttribute()
          .setEmptyFormat();
    }
    return res.values().toSeq();
  }
}
