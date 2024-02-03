package tmi.recipe;

import arc.Core;
import arc.func.Boolf;
import arc.func.Func;
import arc.graphics.g2d.TextureRegion;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import tmi.recipe.types.RecipeItem;

@SuppressWarnings({"unchecked", "rawtypes"})
public class RecipeItemManager {
  protected static final RecipeItem ERROR = new RecipeItem<>("error") {
    @Override public int ordinal() {return -1;}
    @Override public int typeID() {return -1;}
    @Override public String name() {return "<error>";}
    @Override public String localizedName() {return "<error>";}
    @Override public TextureRegion icon() {return Core.atlas.find("error");}
    @Override public boolean hidden() {return true;}
  };

  protected static final OrderedMap<Boolf<Object>, Func<?, RecipeItem<?>>> wrapper = new OrderedMap<>();

  protected final ObjectMap<Object, RecipeItem<?>> recipeItems = new ObjectMap<>();
  protected final ObjectMap<String, RecipeItem<?>> itemNameMap = new ObjectMap<>();

  public static <T> void registerWrapper(Boolf<Object> matcher, Func<T, RecipeItem<T>> factory){
    wrapper.put(matcher, (Func) factory);
  }

  static {
    registerWrapper(e -> e instanceof UnlockableContent, RecipeUnlockableContent::new);
  }

  public <T, R extends RecipeItem<T>> R addItemWrap(T item, R recipeItem){
    recipeItems.put(item, recipeItem);
    itemNameMap.put(recipeItem.name(), recipeItem);

    return recipeItem;
  }

  public <T> RecipeItem<T> getItem(T item) {
    return (RecipeItem<T>) recipeItems.get(item, () -> {
      for (ObjectMap.Entry<Boolf<Object>, Func<?, RecipeItem<?>>> entry : wrapper) {
        if (entry.key.get(item)) {
          RecipeItem<?> res = (RecipeItem<?>) ((Func) entry.value).get(item);
          itemNameMap.put(res.name(), res);
          return res;
        }
      }
      return ERROR;
    });
  }

  public <T> RecipeItem<T> getByName(String name){
    return (RecipeItem<T>) itemNameMap.get(name, ERROR);
  }

  public Seq<RecipeItem<?>> getList(){
    return recipeItems.values().toSeq().sort();
  }

  protected static class RecipeUnlockableContent extends RecipeItem<UnlockableContent>{
    private static final ObjectIntMap<ContentType> mirror = new ObjectIntMap<>();

    static {
      mirror.put(ContentType.item, 0);
      mirror.put(ContentType.liquid, 1);
      mirror.put(ContentType.block, 2);
      mirror.put(ContentType.unit, 3);
    }

    public RecipeUnlockableContent(UnlockableContent item) {
      super(item);
    }

    @Override public int ordinal() {return item.id;}
    @Override public int typeID() {return mirror.get(item.getContentType(), item.getContentType().ordinal());}
    @Override public String name() {return item.name;}
    @Override public String localizedName() {return item.localizedName;}
    @Override public TextureRegion icon() {return item.uiIcon;}
    @Override public boolean hidden() {return false;}
    @Override public boolean locked() {return !item.unlockedNow();}
    @Override public boolean hasDetails() {return true;}
    @Override public void displayDetails() {Vars.ui.content.show(item);}
  }
}
