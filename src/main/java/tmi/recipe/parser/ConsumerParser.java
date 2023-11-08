package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.consumers.*;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeParser;
import tmi.recipe.types.PowerMark;

import static mindustry.Vars.content;

public abstract class ConsumerParser<T extends Block> extends RecipeParser<T> {
  protected static ObjectMap<Boolf<Consume>, Cons2<Recipe, Consume>> vanillaConsParser = new ObjectMap<>();

  public static void registerVanillaConsParser(Boolf<Consume> type, Cons2<Recipe, Consume> handle){
    vanillaConsParser.put(type, handle);
  }

  public static void registerVanillaConsumeParser() {
    //items
    registerVanillaConsParser(c -> c instanceof ConsumeItems, (recipe, consume) -> {
      for (ItemStack item : ((ConsumeItems) consume).items) {
        recipe.addMaterial(item.item, item.amount);
      }
    });
    registerVanillaConsParser(c -> c instanceof ConsumeItemFilter, (recipe, consume) -> {
      ConsumeItemFilter cf = ((ConsumeItemFilter) consume);
      for (Item item : content.items().select(i -> cf.filter.get(i) && i.unlockedNow() && !i.isHidden())) {
        recipe.addMaterial(item, 1);
      }
    });

    //liquids
    registerVanillaConsParser(c -> c instanceof ConsumeLiquids, (recipe, consume) -> {
      for (LiquidStack liquid : ((ConsumeLiquids) consume).liquids) {
        recipe.addMaterialPresec(liquid.liquid, liquid.amount);
      }
    });
    registerVanillaConsParser(c -> c instanceof ConsumeLiquid, (recipe, consume) -> {
      recipe.addMaterialPresec(((ConsumeLiquid) consume).liquid,  ((ConsumeLiquid) consume).amount);
    });
    registerVanillaConsParser(c -> c instanceof ConsumeLiquidFilter, (recipe, consume) -> {
      ConsumeLiquidFilter cf = ((ConsumeLiquidFilter) consume);
      for (Liquid liquid : content.liquids().select(i -> cf.filter.get(i) && i.unlockedNow() && !i.isHidden())) {
        recipe.addMaterialPresec(liquid, cf.amount);
      }
    });

    //payloads
    registerVanillaConsParser(c -> c instanceof ConsumePayloads, (recipe, consume) -> {
      for (PayloadStack stack : ((ConsumePayloads) consume).payloads) {
        if (stack.amount > 1) recipe.addMaterial(stack.item, stack.amount);
        else recipe.addMaterial(stack.item);
      }
    });

    //power
    registerVanillaConsParser(c -> c instanceof ConsumePower,
        (recipe, consume) -> recipe.addMaterialPresec(PowerMark.INSTANCE, ((ConsumePower) consume).usage));
  }

  protected void registerCons(Recipe recipe, Consume[] cons){
    for (Consume consume : cons) {
      for (ObjectMap.Entry<Boolf<Consume>, Cons2<Recipe, Consume>> entry : vanillaConsParser) {
        if (entry.key.get(consume)) entry.value.get(recipe, consume);
      }
    }
  }
}
