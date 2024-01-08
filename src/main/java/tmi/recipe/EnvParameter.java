package tmi.recipe;

import arc.func.Cons2;
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

  public float getAttribute(RecipeItem<?> b) {
    return attributes.get(b, 0f);
  }

  public EnvParameter add(RecipeItemStack item){
    return add(item.item, item.amount, item.isAttribute);
  }

  public EnvParameter add(RecipeItem<?> item, float amount, boolean isAttribute){
    if (isAttribute){
      attributes.increment(item, 0, amount);
    }
    else inputs.increment(item, 0, amount);

    return this;
  }

  public EnvParameter set(EnvParameter other){
    other.attributes.each(e -> add(e.key, e.value, true));
    other.inputs.each(e -> add(e.key, e.value, false));
    return this;
  }

  public EnvParameter setInputs(EnvParameter other){
    other.inputs.each(e -> add(e.key, e.value, false));
    return this;
  }

  public EnvParameter setAttributes(EnvParameter other){
    other.attributes.each(e -> add(e.key, e.value, true));
    return this;
  }

  public EnvParameter resetInput(RecipeItem<?> item){
    inputs.remove(item, 0);
    return this;
  }

  public EnvParameter resetAttr(RecipeItem<?> item){
    attributes.remove(item, 0);
    return this;
  }

  public EnvParameter clearInputs(){
    inputs.clear();
    return this;
  }

  public EnvParameter clearAttr(){
    attributes.clear();
    return this;
  }

  public EnvParameter clear() {
    clearInputs();
    clearAttr();
    return this;
  }

  public EnvParameter applyFullRecipe(Recipe recipe, boolean fillOptional, boolean applyAttribute, float multiplier){
    for (RecipeItemStack stack : recipe.materials.values()) {
      if (!fillOptional && stack.optionalCons) continue;
      if (!applyAttribute && stack.isAttribute) continue;

      inputs.put(stack.item, stack.amount*multiplier);
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

  public boolean hasInput() {
    return !inputs.isEmpty();
  }

  public boolean hasAttrs() {
    return !attributes.isEmpty();
  }

  public void eachInputs(Cons2<RecipeItem<?>, Float> cons){
    inputs.each(e -> cons.get(e.key, e.value));
  }

  public void eachAttribute(Cons2<RecipeItem<?>, Float> cons){
    attributes.each(e -> cons.get(e.key, e.value));
  }
}
