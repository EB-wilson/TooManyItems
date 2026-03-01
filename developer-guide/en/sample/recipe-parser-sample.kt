// This type is TMI's internal recipe parser for factory blocks of type AttributeCrafter.
// This example demonstrates how to use the Parser to parse a series of blocks and explains the operational details of each step.
open class AttributeCrafterParser : ConsumerParser<AttributeCrafter>() {
  // Since AttributeCrafter extends the GenericCrafter type, and there is already a parser implementation for GenericCrafter.
  // When filtering blocks, we use type checking, so AttributeCrafter would also pass the filter for GenericCrafterParser.
  // For this reason, we need to exclude GenericCrafterParser in `excludes` so that AttributeCrafter is only parsed by this parser.
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(GenericCrafterParser::class.java)

  // This method identifies and returns true when the target is an AttributeCrafter. Only when this method returns true will the block be parsed by this parser.
  override fun isTarget(content: Block): Boolean {
    return content is AttributeCrafter
  }

  override fun parse(content: AttributeCrafter): Seq<Recipe> {
    // Since AttributeCrafter effectively only executes one recipe production, we instantiate a single Recipe as the parsing result.
    val res = Recipe(
      recipeType = RecipeType.factory, // Use factory as the recipe type
      // getWrap() is a convenient extension method in RecipeParser to quickly obtain the wrapper for an object.
      // This is equivalent to TooManyItems.itemsManager.getItem(content).
      ownerBlock = content.getWrap(),
      craftTime = content.craftTime,
    ).setBaseEff(content.baseEfficiency) // AttributeCrafter has its base work efficiency, with additional additive efficiency provided by environment items. Here we set the base efficiency for the recipe.

    // Utility function within ConsumerParser. In most cases, you only need to directly provide the Consume that defines the block's production to this function to register the material items.
    // If you have custom Consume types, please refer to the external documentation for ConsumerParser to learn how to add consumption parsers.
    registerCons(res, *content.consumers)

    // For AttributeCrafter, there are many types of floors that satisfy its environmental attributes, but only the one with the highest efficiency among the covered floor attributes will take effect.
    // Therefore, the available attribute floors are mutually exclusive. In this case, we need to set a recipe item group for these mutually exclusive items, i.e., RecipeItemGroup.
    // Items in the same group occupy the same slot (in layout and balance calculations) and are displayed cyclically.
    val attrGroup = RecipeItemGroup()
    // Need to iterate through all blocks to search for and filter blocks that meet the environmental attribute requirements of this block.
    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      // Calculate the additional efficiency that this floor can provide when placed under the block. For the efficiency calculation formula, please refer to RecipeItemType.
      val eff = min(
        (content.boostScale*content.size*content.size*block.attributes[content.attribute]),
        content.maxBoost
      )

      // Add the floor item to the recipe and set its properties.
      res.addMaterial(block.getWrap(), (content.size*content.size) as Number)
        .setType(RecipeItemType.ATTRIBUTE)
        .setOptional(content.baseEfficiency > 0.001f)
        .setEfficiency(eff)
        .efficiencyFormat(content.baseEfficiency, eff) // This function appends the efficiency increment percentage text after the item's original quantity formatting function.
        .setGroup(attrGroup)                           // Set the item group this item belongs to.
    }

    // Add recipe output items.
    if (content.outputItems == null) {
      if (content.outputItem != null) res.addProductionInteger(content.outputItem.item.getWrap(), content.outputItem.amount)
    }
    else {
      for (item in content.outputItems) {
        res.addProductionInteger(item.item.getWrap(), item.amount)
      }
    }

    if (content.outputLiquids == null) {
      if (content.outputLiquid != null) res.addProductionPersec(
        content.outputLiquid.liquid.getWrap(),
        content.outputLiquid.amount
      )
    }
    else {
      for (liquid in content.outputLiquids) {
        res.addProductionPersec(liquid.liquid.getWrap(), liquid.amount)
      }
    }

    // Parsing returns a list of recipes, considering cases where there might be multiple recipes, such as unit factories.
    // For factories with only one recipe, simply return a single-element Seq.
    return Seq.with(res)
  }
}