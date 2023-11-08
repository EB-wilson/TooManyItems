package tmi.recipe;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;

@SuppressWarnings("rawtypes")
public abstract class RecipeParser<T extends Block>{
  public Seq<Class<? extends RecipeParser>> excludes = new Seq<>();

  public void init(){}

  public boolean exclude(RecipeParser<?> parser){
    return excludes.contains(e -> e.isAssignableFrom(parser.getClass()));
  }

  public abstract boolean isTarget(Block content);

  public abstract Seq<Recipe> parse(T content);
}
