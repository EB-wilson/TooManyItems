package tmi.recipe;

import arc.func.Cons;
import arc.scene.ui.layout.Table;
import arc.struct.OrderedMap;
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

  public RecipeItemStack addMaterial(UnlockableContent item, String amount) {
    RecipeItemStack res = new RecipeItemStack(item, amount);
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterial(UnlockableContent item) {
    RecipeItemStack res = new RecipeItemStack(item);
    materials.put(item, res);
    return res;
  }

  public RecipeItemStack addMaterial(UnlockableContent item, int amount){
    return addMaterial(item, amount > 1000? UI.formatAmount(amount): Integer.toString(amount));
  }

  public RecipeItemStack addMaterial(UnlockableContent item, float amount){
    return addMaterial(item, amount > 1000? UI.formatAmount((long) (amount)): Strings.autoFixed(amount, 2));
  }

  public RecipeItemStack addMaterialPresec(UnlockableContent item, float preSeq){
    return addMaterial(item, (preSeq*60 > 1000? UI.formatAmount((long) (preSeq*60)): Strings.autoFixed(preSeq*60, 2)) + "/" + StatUnit.seconds.localized());
  }

  public RecipeItemStack addProduction(UnlockableContent item, String amount) {
    RecipeItemStack res = new RecipeItemStack(item, amount);
    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProduction(UnlockableContent item) {
    RecipeItemStack res = new RecipeItemStack(item);
    productions.put(item, res);
    return res;
  }

  public RecipeItemStack addProduction(UnlockableContent item, int amount){
    return addProduction(item, amount > 1000? UI.formatAmount(amount): Integer.toString(amount));
  }

  public RecipeItemStack addProduction(UnlockableContent item, float amount){
    return addProduction(item, amount > 1000? UI.formatAmount((long) (amount)): Strings.autoFixed(amount, 2));
  }

  public RecipeItemStack addProductionPresec(UnlockableContent item, float preSeq){
    return addProduction(item, (preSeq*60 > 1000? UI.formatAmount((long) (preSeq*60)): Strings.autoFixed(preSeq*60, 2)) + "/" + StatUnit.seconds.localized());
  }

  public boolean containsProduction(UnlockableContent production) {
    return productions.containsKey(production);
  }

  public boolean containsMaterial(UnlockableContent material) {
    return materials.containsKey(material);
  }
}
