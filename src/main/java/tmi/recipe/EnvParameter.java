package tmi.recipe;

import arc.struct.ObjectFloatMap;
import tmi.recipe.types.HeatMark;
import tmi.recipe.types.PowerMark;
import tmi.recipe.types.RecipeItem;

public class EnvParameter {
  private final ObjectFloatMap<RecipeItem<?>> inputs = new ObjectFloatMap<>();
  private final ObjectFloatMap<RecipeItem<?>> attributes = new ObjectFloatMap<>();

  public float getInputs(RecipeItem<?> b) {
    return inputs.get(b, 0f);
  }

  public void add(RecipeItemStack item){
    add(item.item, item.amount, item.isAttribute);
  }

  public void add(RecipeItem<?> item, float amount, boolean isAttribute){
    if (isAttribute){
      attributes.increment(item, 0, amount);
    }
    else inputs.increment(item, 0, amount);
  }

  public void clearInputs(){
    inputs.clear();
  }

  public void clearAttr(){
    attributes.clear();
  }

  public EnvParameter applyFullRecipe(Recipe recipe, boolean fillOptional){
    for (RecipeItemStack stack : recipe.materials.values()) {
      if (!fillOptional && stack.optionalCons) continue;

      inputs.put(stack.item, stack.amount);
    }

    return this;
  }

  public EnvParameter addPower(float power){
    add(PowerMark.INSTANCE, power + inputs.get(PowerMark.INSTANCE, 0f), false);
    return this;
  }

  public EnvParameter addHeat(float heat){
    add(HeatMark.INSTANCE, heat + inputs.get(HeatMark.INSTANCE, 0f), false);
    return this;
  }
}
