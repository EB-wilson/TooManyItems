package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.consumers.Consume;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class ReconstructorParser extends ConsumerParser<Reconstructor>{
  @Override
  public boolean isTarget(Block content) {
    return content instanceof Reconstructor;
  }

  @Override
  public Seq<Recipe> parse(Reconstructor reconstructor) {
    Seq<Recipe> res = new Seq<>();
    for (UnitType[] upgrade : reconstructor.upgrades) {
      Recipe recipe = new Recipe(RecipeType.factory);
      recipe.setBlock(getWrap(reconstructor));
      recipe.setTime(reconstructor.constructTime);
      recipe.addMaterial(getWrap(upgrade[0]), 1);
      recipe.addProduction(getWrap(upgrade[1]), 1);

      registerCons(recipe, reconstructor.consumers);

      res.add(recipe);
    }

    return res;
  }
}
