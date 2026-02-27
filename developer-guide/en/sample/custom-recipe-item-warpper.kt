// TMI uses RecipeItem to wrap all items participating in recipes. By default, TMI implements wrapping methods for most of the vanilla content in the game.
// However, if you have custom material that are not derived from UnlockableContent and need to participate in wrapping, you need to implement their RecipeItem for that type.
// RecipeItem is an abstract class that contains all the information needed for a recipe item. This example demonstrates how to add a wrapper implementation for a custom type.

// Suppose we have a material that does not belong to UnlockableContent, let's call it "Quark"
class Quark(
  val type: Int
)

// Then implement the recipe wrapper for Quark
class RecipeQuark(quark: Quark): RecipeItem<Quark>(quark){
  override val ordinal get() = quark.type // Primary ordinal, used for sorting
  override val typeOrdinal get() = 0      // Type ordinal, used for sorting
  override val typeID = 2938746           // Type ID, used for generating identifiers; should be as unique as possible
  override val name = "quark-${quark.type}"
  override val localizedName: String = Core.bundle["quark.type-${quark.type}.name"]
  override val icon: TextureRegion get() = Core.atlas.find("quark-${quark.type}")
  override val hidden = false             // Whether the item is only used to describe recipe information and not displayed in the item list
  override val hasDetails = false         // Whether the item carries details that can be viewed by long-pressing; if true, the displayDetails() function must also be implemented
  override val locked = false             // Whether the item is currently locked; this only displays a lock icon when shown
}

fun sample1(){
  // Next, you can add a wrapper function as follows
  // This function is a utility function that uses generic parameters to match the input object type. The subsequent function block receives this object and returns its wrapped object.
  // Here we filter the Quark type and directly wrap it using the RecipeQuark defined above.
  TooManyItems.itemsManager.registerWrapper<Quark>{ RecipeQuark(it) }

  // Now, create several different quarks
  val e1 = Quark(1)
  val e2 = Quark(2)
  val e3 = Quark(3)

  // Obtain their recipe wrappers respectively. This operation creates wrapper instances for unwrapped items and adds them to the manager's container.
  // When obtaining the wrapper for the same object again, the existing wrapper will be returned.
  val recE1 = TooManyItems.itemsManager.getItem(e1)
  val recE2 = TooManyItems.itemsManager.getItem(e2)
  val recE3 = TooManyItems.itemsManager.getItem(e3)

  // Additionally, you can also obtain already wrapped items via getByName(name)
  val recE1byName = TooManyItems.itemsManager.getByName("quark-1")
  val recE2byName = TooManyItems.itemsManager.getByName("quark-2")
  val recE3byName = TooManyItems.itemsManager.getByName("quark-3")

  Log.info(recE1 == recE1byName) // -> true
  Log.info(recE2 == recE2byName) // -> true
  Log.info(recE3 == recE3byName) // -> true
}

// Of course, sometimes we encounter recipe items that often exist only as a singleton after wrapping, such as PowerMark and HeatMark for power.
// TMI extends an abstract layer based on RecipeItem called SingleItemMark. It does not need to wrap a specific object; it is itself a valid recipe item.
// Now, suppose there is a material called "Ether". Unlike Quark, it only exists as itself, not as a type of materials. We can describe this recipe item as follows.
// SingleItemMark receives a string as a persistent unique identifier. SingleItemMark contains default values for other properties and automatically adds this singleton to the item manager.
object EtherMark: SingleItemMark("ether-mark"){
  override val ordinal = 0
  override val icon: TextureRegion get() = Core.atlas.find("ether")
}

// You can directly use the EtherMark singleton like any other RecipeItem
fun sample2(recipe: Recipe){
  val ether = EtherMark
  recipe.addMaterialPresec(EtherMark, 1f)
}