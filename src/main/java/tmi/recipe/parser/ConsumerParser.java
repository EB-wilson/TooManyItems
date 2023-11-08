package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import mindustry.content.Blocks;
import mindustry.ctype.UnlockableContent;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.type.PayloadStack;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.consumers.*;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeParser;

public abstract class ConsumerParser<T extends Block> extends RecipeParser<T> {
  protected static ObjectMap<Boolf<Consume>, Cons2<Recipe, Consume>> vanillaConsParser = new ObjectMap<>();

  public static void registerVanillaConsParser(Boolf<Consume> type, Cons2<Recipe, Consume> handle){
    vanillaConsParser.put(type, handle);
  }

  protected void registerCons(Recipe recipe, Consume[] cons){
    for (Consume consume : cons) {
      for (ObjectMap.Entry<Boolf<Consume>, Cons2<Recipe, Consume>> entry : vanillaConsParser) {
        if (entry.key.get(consume)) entry.value.get(recipe, consume);
      }
    }
  }
}
