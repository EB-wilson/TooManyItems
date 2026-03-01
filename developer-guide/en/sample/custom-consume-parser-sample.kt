// ConsumerParser wraps the parsing work for Consume. You can use registerCons to input a Consume and add recipe items to the given recipe.
// TMI implements parsing functions for most of the vanilla Consume types in Mindustry. However, if you need to parse a custom Consume type, you need to add parsing records to the ConsumerParser.
// This program demonstrates a simple declaration of a custom Consume parsing record. You can refer to this program to implement your own consumption item parsing.

// An assumed consumption class that consumes multiple items and liquids simultaneously.
class ConsumeSample(
  val consItems: List<ItemStack>,
  val consLiquid: LiquidStack
): Consume(){
  //...
}

// You can use the companion object function registerConsumeParser in ConsumerParser to add parsing records.
// A consumption item parsing record essentially consists of a filter and its callback function. Whenever a Consume is input, it attempts to match all records that match the filter and executes their callback blocks.
// The work of adding recipe items is carried out within the callback block. The following is a simple example for parsing the ConsumeSample given above.
fun sample(){
  // This is a utility overload that directly uses the provided generic parameter for type checking as the filter for this entry. For details, please refer to the external API documentation.
  ConsumerParser.registerConsumeParser<ConsumeSample> { recipe, consume, handle ->
    // This callback function body is invoked when a Consume that passes the filter is registered.
    // The three parameters received are:
    // - recipe: The recipe being operated on.
    // - consume: The input consumption item.
    // - handle: An outer callback for the newly created RecipeItemStack.
    // The parsing work for Consume is actually similar to the work in RecipeParser's parse method. We need to extract valid information from the Consume and add it to the input recipe.
    // It is worth noting that we also need to pass the added recipe item to the caller through the `handle` function received from the parameters. This is to facilitate setting properties for the newly added recipe items from the Consume during recipe parsing.
    // You simply need to add the recipe items as usual, as shown below, and then directly call `handle` with the newly added RecipeItemStack as a parameter:
    handle(
      recipe.addMaterialInteger(consume.consLiquid.item.getWrap(), consume.consLiquid.amount)
        .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
        .setOptional(consume.optional)
    )

    // Similarly for the list.
    for (stack in consume.consItems) {
      handle(
        recipe.addMaterialInteger(stack.item.getWrap(), stack.amount)
          .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
          .setOptional(consume.optional)
      )
    }
  }
}