package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.func.Cons3;
import arc.struct.ObjectMap;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.consumers.*;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeParser;
import tmi.recipe.types.PowerMark;

import static mindustry.Vars.content;

public abstract class ConsumerParser<T extends Block> extends RecipeParser<T> {
  protected static ObjectMap<Boolf<Consume>, Cons3<Recipe, Consume, Float>> vanillaConsParser = new ObjectMap<>();


  public static void registerVanillaConsParser(Boolf<Consume> type, Cons3<Recipe, Consume, Float> handle){
    vanillaConsParser.put(type, handle);
  }

  public static void registerVanillaConsumeParser() {
    //items
    registerVanillaConsParser(c -> c instanceof ConsumeItems, (recipe, consume, eff) -> {
      for (ItemStack item : ((ConsumeItems) consume).items) {
        recipe.addMaterial(item.item, item.amount).setOptionalCons(consume.optional).setEfficiency(eff);
      }
    });
    registerVanillaConsParser(c -> c instanceof ConsumeItemFilter, (recipe, consume, eff) -> {
      ConsumeItemFilter cf = ((ConsumeItemFilter) consume);
      for (Item item : content.items().select(i -> cf.filter.get(i) && i.unlockedNow() && !i.isHidden())) {
        recipe.addMaterial(item, 1).setOptionalCons(consume.optional).setEfficiency(eff);
      }
    });

    //liquids
    registerVanillaConsParser(c -> c instanceof ConsumeLiquids, (recipe, consume, eff) -> {
      for (LiquidStack liquid : ((ConsumeLiquids) consume).liquids) {
        recipe.addMaterialPresec(liquid.liquid, liquid.amount).setOptionalCons(consume.optional).setEfficiency(eff);
      }
    });
    registerVanillaConsParser(c -> c instanceof ConsumeLiquid, (recipe, consume, eff) -> {
      recipe.addMaterialPresec(((ConsumeLiquid) consume).liquid,  ((ConsumeLiquid) consume).amount).setOptionalCons(consume.optional).setEfficiency(eff);
    });
    registerVanillaConsParser(c -> c instanceof ConsumeLiquidFilter, (recipe, consume, eff) -> {
      ConsumeLiquidFilter cf = ((ConsumeLiquidFilter) consume);
      for (Liquid liquid : content.liquids().select(i -> cf.filter.get(i) && i.unlockedNow() && !i.isHidden())) {
        recipe.addMaterialPresec(liquid, cf.amount).setOptionalCons(consume.optional).setEfficiency(eff);
      }
    });

    //payloads
    registerVanillaConsParser(c -> c instanceof ConsumePayloads, (recipe, consume, eff) -> {
      for (PayloadStack stack : ((ConsumePayloads) consume).payloads) {
        if (stack.amount > 1) recipe.addMaterial(stack.item, stack.amount).setEfficiency(eff);
        else recipe.addMaterial(stack.item).setOptionalCons(consume.optional).setEfficiency(eff);
      }
    });

    //power
    registerVanillaConsParser(c -> c instanceof ConsumePower,
        (recipe, consume, eff) -> recipe.addMaterialPresec(PowerMark.INSTANCE, ((ConsumePower) consume).usage).setOptionalCons(consume.optional).setEfficiency(eff));
  }

  protected void registerCons(Recipe recipe, Consume... cons){
    registerCons(recipe, 1f, cons);
  }

  protected void registerCons(Recipe recipe, float efficiency, Consume... cons){
    for (Consume consume : cons) {
      for (ObjectMap.Entry<Boolf<Consume>, Cons3<Recipe, Consume, Float>> entry : vanillaConsParser) {
        if (entry.key.get(consume)) entry.value.get(recipe, consume, efficiency);
      }
    }
  }
}
