package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.consumers.Consume;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class UnitFactoryParser extends ConsumerParser<UnitFactory>{
  @Override
  public boolean isTarget(Block content) {
    return content instanceof UnitFactory;
  }

  @Override
  public Seq<Recipe> parse(UnitFactory factory) {
    Seq<Recipe> res = new Seq<>();

    for (UnitFactory.UnitPlan plan : factory.plans) {
      Recipe recipe = new Recipe(RecipeType.factory);
      recipe.setBlock(getWrap(factory));
      recipe.setTime(plan.time);

      recipe.addProduction(getWrap(plan.unit), 1);

      for (ItemStack stack : plan.requirements) {
        recipe.addMaterial(getWrap(stack.item), stack.amount);
      }

      registerCons(recipe, factory.consumers);

      res.add(recipe);
    }

    return res;
  }
}
