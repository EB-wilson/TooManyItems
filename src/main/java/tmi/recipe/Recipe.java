package tmi.recipe;

import arc.func.Cons;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedMap;
import arc.struct.OrderedSet;
import arc.struct.Seq;
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
  public OrderedMap<UnlockableContent, RecipeItemStack> productions = new OrderedMap<>();
  public OrderedMap<UnlockableContent, RecipeItemStack> materials = new OrderedMap<>();

  //infos
  public Block block;
  @Nullable public String description;
  @Nullable public Cons<Table> buildInfo;

  public Recipe(RecipeType recipeType) {
    this.recipeType = recipeType;
  }

  public void addMaterial(UnlockableContent item, String amount) {
    materials.put(item, new RecipeItemStack(item, amount));
  }

  public void addMaterial(UnlockableContent item) {
    materials.put(item, new RecipeItemStack(item));
  }

  public void addMaterial(UnlockableContent item, int amount){
    materials.put(item, new RecipeItemStack(item, amount > 1000? UI.formatAmount(amount): Integer.toString(amount)));
  }

  public void addMaterial(UnlockableContent item, float amount){
    materials.put(item, new RecipeItemStack(item, amount > 1000? UI.formatAmount((long) (amount)): Strings.autoFixed(amount, 2)));
  }

  public void addMaterialPresec(UnlockableContent item, float preSeq){
    materials.put(item, new RecipeItemStack(item, (preSeq*60 > 1000? UI.formatAmount((long) (preSeq*60)): Strings.autoFixed(preSeq*60, 2)) + "/" + StatUnit.seconds.localized()));
  }

  public void addProduction(UnlockableContent item, String amount) {
    productions.put(item, new RecipeItemStack(item, amount));
  }

  public void addProduction(UnlockableContent item) {
    productions.put(item, new RecipeItemStack(item));
  }

  public void addProduction(UnlockableContent item, int amount){
    productions.put(item, new RecipeItemStack(item, amount > 1000? UI.formatAmount(amount): Integer.toString(amount)));
  }

  public void addProduction(UnlockableContent item, float amount){
    productions.put(item, new RecipeItemStack(item, amount > 1000? UI.formatAmount((long) (amount)): Strings.autoFixed(amount, 2)));
  }

  public void addProductionPresec(UnlockableContent item, float preSeq){
    productions.put(item, new RecipeItemStack(item, (preSeq*60 > 1000? UI.formatAmount((long) (preSeq*60)): Strings.autoFixed(preSeq*60, 2)) + "/" + StatUnit.seconds.localized()));
  }

  public boolean containsProduction(UnlockableContent production) {
    return productions.containsKey(production);
  }

  public boolean containsMaterial(UnlockableContent material) {
    return materials.containsKey(material);
  }
}
