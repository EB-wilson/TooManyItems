## Quick Start

This document aims to help developers quickly understand the third-party API structure and usage of *TooManyItems*, providing examples to assist developers in quickly creating recipe adapters for their mods.

For convenience, *TooManyItems* will be referred to as *TMI* below. To avoid confusion, the word "item" is referred to a reference to an object inside TMI , and it is explicitly noted when it is referred as the game content such as copper or lead.

> **Note**: If all the content in your mod uses default content types from Mindustry (such as `GenericCrafter`, etc.) and you have not used custom production methods to override default production behavior, TMI already provides compatibility for these contents, so no additional compatibility work is required.

### Recipe Entry

TMI provides standardized wrappers and abstractions for recipes and the items involved in recipes, making it compatible with almost all possible production and consumption forms. It also offers independent submodule entry points for third-party mods, allowing TMI-related programs to exist in a separate submodule and be called only when the user has TMI installed.

To write a TMI module entry for your mod, you only need to create a class that implements the `tmi.RecipeEntry` interface:

```kotlin
class MyEntry: RecipeEntry{
  override fun init(){
    // Main initialization work
    Log.info("recipe entry init")
  }

  override fun afterInit(){
    // Post-initialization work
    Log.info("recipe entry after init")
  }
}

```
Then, add the following pair to your mod's metadata file (mod.json/mod.hjson): "recipeEntry": "MyEntry".

If you have correctly set up the metadata and the class mentioned above, when you start the game with both TMI and your mod installed, you will see two log messages: `recipe entry init` and `recipe entry after init`.

**Alternatively**, if your mod is written in Java or Kotlin (rather than JavaScript), you can also register your recipe entry by adding the @RecipeEntryPoint annotation to your main mod class:

```kotlin
@RecipeEntryPoint(MyEntry::class)
class MyMod: Mod(){
  override fun init() {
    //...
  }
  
  override fun loadContents(){
    //...
  }
}
```

This annotation registration has the same effect as adding the mod metadata.

> You can place this entry class anywhere in your mod; however, it is recommended to put it in a Gradle submodule. As long as the classpath is correctly defined in the metadata, it will be loaded properly.

### Recipe Item Wrapping

In TMI, all concepts of materials or outputs such as "items"(game content) and "liquids" are wrapped into a standard recipe item, defined in the program as an abstract generic class `tmi.recipe.RecipeItem<I>`. What is stored in the recipe is these wrapped recipe items.

All recipe items are stored in a recipe item manager singleton, which is `TooManyItems.itemsManager`. When you need to obtain a recipe item wrapper for an object, you should retrieve it from the manager.

You can use `TooManyItems.itemsManager.getItem(object)` to get the recipe item wrapper for any object. The recipe item manager already contains implementations for most default material types in Mindustry, so you can directly obtain wrappers for most vanilla game content:

```kotlin
// Copper
val wrappedCopper = TooManyItems.itemsManager.getItem(Items.copper)
// Water
val wrappedWater = TooManyItems.itemsManager.getItem(Liquids.water)
// Duo
val wrappedDuo = TooManyItems.itemsManager.getItem(Blocks.duo)
// Eclipse
val wrappedEclipse = TooManyItems.itemsManager.getItem(UnitTypes.eclipse)
```

In the following sections, the recipe item objects will be obtained in this way.

> If you attempt to retrieve an item that does not have a corresponded constrctor of `RecipeItem<>` registered in the `itemsManager`, an `error` item will be received, of which all the information is marked as `error`. For custom implementations, please refer to the external API documentation for `RecipeItemManager`.

### Creating and Adding Recipes

Recipes are defined as the type `tmi.recipe.Recipe`. All recipes are stored in a recipe manager singleton, which is  `TooManyItems.recipesManager`. The recipes you create should be added to this manager.

To create a recipe, simply instantiate a `Recipe` object and add it to the recipe manager during the init phase:

```kotlin
class MyEntry: RecipeEntry{
  override fun init(){
    val newRecipe = Recipe(
      recipeType = RecipeType.factory,
      ownerBlock = TooManyItems.itemsManager.getItem(Blocks.siliconSmelter),
      craftTime = 40f,
    )
    
    //...

    TooManyItems.recipeManager.addRecipe(newRecipe)
  }
}
```

The Recipe constructor takes three parameters:

- `recipeType`: The type of the recipe, which determines how the recipe will be visualized and how balance and efficiency calculations are handled.
- `ownerBlock`: The block to which this recipe belongs. It can be null, indicating that the recipe is not owned by any block.
- `craftTime`: The duration of the recipe in ticks. Set to a negative number to indicate continuous production.

`recipeType` is an abstract class `tmi.recipe.RecipeType`, which contains four default implementations:

- `RecipeType.factory`: Factory production, generally used for factory recipes that receive materials and produce outputs.
- `RecipeType.building`: Building construction, used to record the construction cost of a block.
- `RecipeType.collecting`: Resource collection, generally used for blocks that obtain resources from the environment, such as drills and pumps.
- `RecipeType.generator`: Energy production, generally used for various types of generators.

These default types are usually enough to cover most recipe forms.

### Describing Recipe Contents

A recipe in TMI is a table consisting of several recipe item objects and their amount. More specifically, the `Recipe` class stores a series of recipe item stacks for materials and outputs, i.e., instances of the type tmi.recipe.RecipeItemStack.

`RecipeItemStack` records a recipe item along with information such as amount. Use `addMaterial` or `addProduction` to add an item stack to the recipe. Here, we assume the input items have already been wrapped:

```kotlin
// Add an item stack directly to the recipe
recipe.addMaterial(RecipeItemStack(/*...*/)) // Material
recipe.addProduction(RecipeItemStack(/*...*/)) // Output

// Add a recipe item directly without any additional settings. This method returns the created item stack.
recipe.addMaterial(copper, 1f) // Material
recipe.addProduction(titanium, 1f) // Output
```

> The Recipe class contains a series of utility methods to set certain item stack properties.

An item stack records various information about an item, including several chainable functions. You can configure the information of a target item in the following form:

```kotlin
recipe.addMaterial(copper, 1f)
  // Set the formatting function for the amount, receives the per-tick unit amount of this item, returns the formatted number text. Default is not to display number text.
  .setFormat { amount -> "amount: $amount" }
  // Set an alternative amount formatting function, roughly the same as above, used for displaying alternative formatted text when a hotkey is pressed.
  .setAltFormat { amount -> /*...*/ }
  // Set the type of this item, which determines its behavior in layout and calculations. Default is NORMAL.
  .setType(RecipeItemType.BOOSTER)
  // Set the full-load efficiency of this item, used for efficiency calculations during recipe balancing. Default is 1.0f.
  .setEfficiency(1.1f)
  // Set whether the item is optional. Default is false.
  .setOptional(true)
  // Set the group this item belongs to. Items in the same group are treated as several optional targets in the same position during layout and calculation.
  .setGroup(group)
```

Or use the Kotlin-style declaration:

```kotlin
recipe.addMaterial(copper, 1f).apply {
  setFormat { amount -> "amount: $amount" }
  setAltFormat { amount -> /*...*/ }
  setType(RecipeItemType.BOOSTER)
  setEfficiency(1.1f)
  setOptional(true)
  setGroup(group)
}
```

### Example Illustration

Taking the vanilla Silicon Crucible as an example, we can define its recipe as follows:

```kotlin
class MyEntry: RecipeEntry{
  override fun init(){
    val items = TooManyItems.itemsManager
    
    val recipe = Recipe(
      recipeType = RecipeType.factory,
      ownerBlock = items.getItem(Blocks.siliconCrucible),
      craftTime = 90f,
    )

    // addMaterialInteger is a utility method to quickly add items with integer amount.
    recipe.addMaterialInteger(items.getItem(Items.coal), 4)
    recipe.addMaterialInteger(items.getItem(Items.sand), 6)
    recipe.addMaterialInteger(items.getItem(Items.pyratite), 1)
   
    // addMaterialPresec is also a utility method for quickly setting the display format to consumption per unit time.
    // Here we add a power consumption item. Power has no instance, so its wrapper is provided by a singleton object PowerMark. Heat belongs to the same category as HeatMark.
    recipe.addMaterialPresec(PowerMark, 4f)
    
    val attrGroup = RecipeItemGroup()
    // Add efficiency-boosting environment items from hot rock.
    recipe.addMaterial(items.getItem(Blocks.hotrock), 9/*size*size*/)
      // Indicates this item is an environment item.
      .setType(RecipeItemType.ATTRIBUTE)
      // This item is optional.
      .setOptional(true)
      // The working efficiency when this optional item is active. The efficiency in the ATTRIBUTE area is added to the base efficiency.
      .setEfficiency(0.43f)
      // This utility function appends the efficiency percentage text after the existing quantity formatting function.
      .efficiencyFormat(content.baseEfficiency, 0.43f)
      // Set the group for this item.
      .setGroup(attrGroup)
    
    // Add efficiency-boosting environment items from magma rock.
    recipe.addMaterial(items.getItem(Blocks.magmarock), 9)
      .setType(RecipeItemType.ATTRIBUTE)
      .setOptional(true)
      .setEfficiency(1f)
      .efficiencyFormat(content.baseEfficiency, 1f)
      // Same as above, this places the item in the same position as the previous one, and only the selected one will take effect.
      .setGroup(attrGroup)
    
    recipe.addProductionInteger(items.getItem(Items.silicon), 8)

    TooManyItems.recipeManager.addRecipe(newRecipe)
  }
}
```

After running it, you can find this custom Silicon Crucible recipe in TMI's recipe browser, coexisting with another existing Silicon Crucible recipe.

### Recipe Parsers

In practice, the number of recipes that need to be added can be very large. We certainly cannot write their recipe declarations one by one. Therefore, we need an automated method to batch analyze and generate recipes for factories and other blocks.

TMI provides recipe parsers to traverse and analyze all blocks in the game. This tool is described by an abstract class `tmi.recipe.RecipeParser<T> `, which contains several abstract APIs for filtering target blocks and parsing target blocks to return a list of recipes that work on that block.

TMI's recipe parsing for default game content in Mindustry relies on this method to define parsers for vanilla content. An implementation of `RecipeParser` looks like this:

```kotlin
class MyParser : RecipeParser<BlockType>() {
  // Mutex parsers to exclude
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(OtherParser::class.java)

  // Called during initialization
  override fun init() {
    Log.info("parser initializing")
  }

  // Target filter; only targets that return true will be parsed
  override fun isTarget(content: Block): Boolean {
    return content is BlockType
  }

  // Parse the received target block and return the parsed recipe list
  override fun parse(content: BlockType): Seq<Recipe> {
    Log.info("parsing: $content")
  }
}
```

The purpose of excludes is to resolve conflicts. When using the block type to filter parsing targets, if two parsers filter on two different levels in a class hierarchy (one higher, one lower), the subtypes in the class hierarchy will also pass the filter of the parent type. If we want the subtype to be parsed only by its corresponding parser, we should provide the mutex parser types that need to be excluded in the excludes of the parser that handles the subtype.

You can follow the method of creating recipes mentioned earlier to analyze the input content in the parse(content) function. Usually, we have the recipe content declared within the target. We only need to declare their efficiency and other information into the Recipe, add it to the list, and return it. A block may also have multiple recipes.If so, simply add multiple recipes to the list.

After implementing your recipe parser, you only need to instantiate it during the init phase of the recipe entry and register it with the recipe manager using the `TooManyItems.recipesManager.registerParser(parser) `function for it to take effect:

```kotlin
class MyEntry: RecipeEntry{
  override fun init(){
    TooManyItems.recipesManager.registerParser(MyParser())
  }
}
```

### ConsumerParser

Additionally, `RecipeParser` has an extended abstract class `ConsumerParser`, which defines tools for matching and parsing `Consume` in Mindustry. It provides default implementations for parsing most default consumption types in the game.

When using this class as a parent, you only need to use the function registerCons(recipe, consumes) to parse and add the input `Consume` objects to the recipe. This method also has two overloads that include a callback function for setting properties on the parsed item stacks:

```kotlin
class MyParser: ConsumerParser<BlockType>() {
  //...
  
  override fun parse(content: BlockType): Seq<Recipe> {
    val recipe = Recipe(/*...*/)
    
    // Directly parse the nonOptionalConsumes of the target block and write to the recipe
    registerCons(recipe, content.nonOptionalConsumes)
    
    // Parse the optionalConsumes of the block; every added item stack will invoke the callback once.
    // In the callback, all parsed items are set as optional.
    registerCons(recipe, { stack ->
      stack.setOptional(true)
    }, content.optionalConsumes)
    
    // Same as above, but the callback also receives the target Consume being parsed.
    registerCons(recipe, { cons, stack ->
      stack.setOptional(true)
    }, content.optionalConsumes)
  }
}
```

You can define parsing methods for specific types of Consume using the `ConsumerParser.registerConsumeParser(...)` function. Please refer to the external API documentation for ConsumerParser.