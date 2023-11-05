package tmi.recipe.parser;

import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.world.blocks.units.Reconstructor;
import tmi.recipe.Recipe;

public class ReconstructorParser extends ConsumerParser<Reconstructor>{
  @Override
  public boolean isTarget(UnlockableContent content) {
    return false;
  }

  @Override
  public Seq<Recipe> parse(Reconstructor content) {
    return null;
  }
}
