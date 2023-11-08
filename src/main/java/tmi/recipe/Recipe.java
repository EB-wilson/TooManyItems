package tmi.recipe;

import arc.func.Cons;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedSet;
import arc.util.Nullable;
import arc.util.Strings;
import mindustry.core.UI;
import mindustry.ctype.UnlockableContent;
import mindustry.world.Block;
import mindustry.world.meta.StatUnit;

public class Recipe {
  //type
  public final RecipeType recipeType;
  //meta
  public OrderedSet<RecipeItemStack> productions = new OrderedSet<>();
  public OrderedSet<RecipeItemStack> materials = new OrderedSet<>();

  //infos
  public Block block;
  @Nullable public String description;
  @Nullable public Cons<Table> buildInfo;

  public Recipe(RecipeType recipeType) {
    this.recipeType = recipeType;
  }

  public void addMaterial(UnlockableContent item, String amount) {
    materials.add(new RecipeItemStack(item, amount));
  }

  public void addMaterial(UnlockableContent item) {
    materials.add(new RecipeItemStack(item));
  }

  public void addMaterial(UnlockableContent item, int amount){
    materials.add(new RecipeItemStack(item, amount > 1000? UI.formatAmount(amount): Integer.toString(amount)));
  }

  public void addMaterial(UnlockableContent item, float amount){
    materials.add(new RecipeItemStack(item, amount > 1000? UI.formatAmount((long) (amount)): Strings.autoFixed(amount, 2)));
  }

  public void addMaterialPresec(UnlockableContent item, float preSeq){
    materials.add(new RecipeItemStack(item, (preSeq*60 > 1000? UI.formatAmount((long) (preSeq*60)): Strings.autoFixed(preSeq*60, 2)) + "/" + StatUnit.seconds.localized()));
  }

  public void addProduction(UnlockableContent item, String amount) {
    productions.add(new RecipeItemStack(item, amount));
  }

  public void addProduction(UnlockableContent item) {
    productions.add(new RecipeItemStack(item));
  }

  public void addProduction(UnlockableContent item, int amount){
    productions.add(new RecipeItemStack(item, amount > 1000? UI.formatAmount(amount): Integer.toString(amount)));
  }

  public void addProduction(UnlockableContent item, float amount){
    productions.add(new RecipeItemStack(item, amount > 1000? UI.formatAmount((long) (amount)): Strings.autoFixed(amount, 2)));
  }

  public void addProductionPresec(UnlockableContent item, float preSeq){
    productions.add(new RecipeItemStack(item, (preSeq*60 > 1000? UI.formatAmount((long) (preSeq*60)): Strings.autoFixed(preSeq*60, 2)) + "/" + StatUnit.seconds.localized()));
  }

  public boolean containsProduction(UnlockableContent production) {
    return productions.orderedItems().contains(e -> e.content() == production);
  }

  public boolean containsMaterial(UnlockableContent material) {
    return materials.orderedItems().contains(e -> e.content() == material);
  }
}
