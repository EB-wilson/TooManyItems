package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Cons3;
import arc.struct.ObjectMap;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.consumers.*;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeItemStack;
import tmi.recipe.RecipeParser;
import tmi.recipe.types.PowerMark;

import static mindustry.Vars.content;

public abstract class ConsumerParser<T extends Block> extends RecipeParser<T> {
  protected static ObjectMap<Boolf<Consume>, Cons3<Recipe, Consume, Cons<RecipeItemStack>>> vanillaConsParser = new ObjectMap<>();

  public static void registerVanillaConsParser(Boolf<Consume> type, Cons3<Recipe, Consume, Cons<RecipeItemStack>> handle){
    vanillaConsParser.put(type, handle);
  }

  public static void registerVanillaConsumeParser() {
    //items
    registerVanillaConsParser(c -> c instanceof ConsumeItems, (recipe, consume, handle) -> {
      for (ItemStack item : ((ConsumeItems) consume).items) {
        handle.get(recipe.addMaterial(getWrap(item.item), item.amount).setOptionalCons(consume.optional));
      }
    });
    registerVanillaConsParser(c -> c instanceof ConsumeItemFilter, (recipe, consume, handle) -> {
      ConsumeItemFilter cf = ((ConsumeItemFilter) consume);
      for (Item item : content.items().select(i -> cf.filter.get(i))) {
        handle.get(recipe.addMaterial(getWrap(item), 1)
            .setOptionalCons(consume.optional)
            .setAttribute(cf)
            .setMaxAttr()
        );
      }
    });

    //liquids
    registerVanillaConsParser(c -> c instanceof ConsumeLiquids, (recipe, consume, handle) -> {
      for (LiquidStack liquid : ((ConsumeLiquids) consume).liquids) {
        handle.get(recipe.addMaterialPresec(getWrap(liquid.liquid), liquid.amount).setOptionalCons(consume.optional));
      }
    });
    registerVanillaConsParser(c -> c instanceof ConsumeLiquid, (recipe, consume, handle) -> {
      handle.get(recipe.addMaterialPresec(getWrap(((ConsumeLiquid) consume).liquid),  ((ConsumeLiquid) consume).amount).setOptionalCons(consume.optional));
    });
    registerVanillaConsParser(c -> c instanceof ConsumeLiquidFilter, (recipe, consume, handle) -> {
      ConsumeLiquidFilter cf = ((ConsumeLiquidFilter) consume);
      for (Liquid liquid : content.liquids().select(i -> cf.filter.get(i))) {
        handle.get(recipe.addMaterialPresec(getWrap(liquid), cf.amount)
            .setOptionalCons(consume.optional)
            .setAttribute(cf)
            .setMaxAttr()
        );
      }
    });

    //payloads
    registerVanillaConsParser(c -> c instanceof ConsumePayloads, (recipe, consume, handle) -> {
      for (PayloadStack stack : ((ConsumePayloads) consume).payloads) {
        if (stack.amount > 1) handle.get(recipe.addMaterial(getWrap(stack.item), stack.amount));
        else handle.get(recipe.addMaterial(getWrap(stack.item)).setOptionalCons(consume.optional));
      }
    });

    //power
    registerVanillaConsParser(c -> c instanceof ConsumePower,
        (recipe, consume, handle) -> handle.get(recipe.addMaterialPresec(PowerMark.INSTANCE, ((ConsumePower) consume).usage).setOptionalCons(consume.optional)));
  }

  protected void registerCons(Recipe recipe, Consume... cons){
    registerCons(recipe, s -> {}, cons);
  }

  protected void registerCons(Recipe recipe, Cons<RecipeItemStack> handle, Consume... cons){
    for (Consume consume : cons) {
      for (ObjectMap.Entry<Boolf<Consume>, Cons3<Recipe, Consume, Cons<RecipeItemStack>>> entry : vanillaConsParser) {
        if (entry.key.get(consume)) entry.value.get(recipe, consume, handle);
      }
    }
  }
}
