package tmi.recipe;

import arc.struct.ObjectFloatMap;
import tmi.recipe.types.HeatMark;
import tmi.recipe.types.PowerMark;
import tmi.recipe.types.RecipeItem;

public class EnvParameter {
  public ObjectFloatMap<RecipeItem<?>> inputs = new ObjectFloatMap<>();

  public float getInputs(RecipeItem<?> b) {
    return inputs.get(b, 0f);
  }

  public EnvParameter applyFullRecipe(Recipe recipe, boolean fillOptional){
    for (RecipeItemStack stack : recipe.materials.values()) {
      if (!fillOptional && stack.optionalCons) continue;

      inputs.put(stack.item, stack.amount);
    }

    return this;
  }

  public EnvParameter addPower(float power){
    inputs.put(PowerMark.INSTANCE, power + inputs.get(PowerMark.INSTANCE, 0f));
    return this;
  }

  public EnvParameter addHeat(float heat){
    inputs.put(HeatMark.INSTANCE, heat + inputs.get(HeatMark.INSTANCE, 0f));
    return this;
  }
}
