package singularity.game;

import arc.struct.OrderedSet;
import arc.util.Nullable;
import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;
import mindustry.world.consumers.Consume;
import universecore.world.producers.BaseProducers;

public class Recipe {
  //type
  public final RecipeType recipeType;
  //meta
  public final OrderedSet<UnlockableContent> productions = new OrderedSet<>();
  public final OrderedSet<UnlockableContent> materials = new OrderedSet<>();

  //infos
  public Block block;
  @Nullable public String description;

  //factory
  @Nullable public BaseProducers production;
  @Nullable public Consume[] vanillaCons;

  public Recipe(RecipeType recipeType) {
    this.recipeType = recipeType;
  }

  public enum RecipeType {
    factory,
    building,
    collecting
  }
}
