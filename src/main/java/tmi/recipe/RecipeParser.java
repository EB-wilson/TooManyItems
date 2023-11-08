package tmi.recipe;

import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;

public abstract class RecipeParser<T extends Block>{
  public void init(){}

  public abstract boolean isTarget(Block content);

  public abstract Seq<Recipe> parse(T content);
}
