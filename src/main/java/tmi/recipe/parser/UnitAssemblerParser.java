package tmi.recipe.parser;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.type.PayloadStack;
import mindustry.world.Block;
import mindustry.world.blocks.units.UnitAssembler;
import mindustry.world.consumers.Consume;
import tmi.recipe.Recipe;
import tmi.recipe.RecipeType;

public class UnitAssemblerParser extends ConsumerParser<UnitAssembler>{

  @Override
  public boolean isTarget(Block content) {
    return content instanceof UnitAssembler;
  }

  @Override
  public Seq<Recipe> parse(UnitAssembler assem) {
    Seq<Recipe> res = new Seq<>();

    for (UnitAssembler.AssemblerUnitPlan plan : assem.plans) {
      Recipe recipe = new Recipe(RecipeType.factory);
      recipe.setBlock(assem);
      recipe.setTime(plan.time);
      recipe.addProduction(plan.unit);

      for (PayloadStack stack : plan.requirements) {
        recipe.addMaterial(stack.item, stack.amount);
      }

      registerCons(recipe, assem.consumers);

      res.add(recipe);
    }

    return res;
  }
}
