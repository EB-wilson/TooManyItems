package tmi.recipe

import arc.func.Boolf
import arc.struct.IntMap
import arc.struct.ObjectSet
import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import tmi.TooManyItems
import tmi.recipe.types.RecipeItem

/**全局配方管理器，以单例模式运行，用于管理和查询所有已加载的配方和分组，同时[RecipeParser]也通过添加到该对象以生效 */
class RecipesManager {
  /**所有[配方分析工具][RecipeParser]的存储容器，已注册的分析工具将被存储在这里待加载配方时分析方块使用 */
  protected var parsers = Seq<RecipeParser<*>>(RecipeParser::class.java)

  /**当前存放所有配方的容器，所有已添加的配方都被存储在这里 */
  protected val recipes = Seq<Recipe>()

  private val materials = ObjectSet<RecipeItem<*>>()
  private val productions = ObjectSet<RecipeItem<*>>()
  private val blocks = ObjectSet<RecipeItem<*>>()
  private val idMap = IntMap<Recipe>()

  /**向管理器注册一个[RecipeParser]用于分析方块可用的配方 */
  fun registerParser(parser: RecipeParser<*>) {
    parsers.add(parser)
  }

  /**添加若干个配方 */
  fun addRecipe(recipes: Seq<Recipe>) {
    for (recipe in recipes) {
      addRecipe(recipe)
    }
  }

  /**添加若干个配方 */
  fun addRecipe(vararg recipes: Recipe) {
    for (recipe in recipes) {
      addRecipe(recipe)
    }
  }

  /**添加单个配方 */
  fun addRecipe(recipe: Recipe) {
    idMap.put(recipe.hashCode(), recipe)

    recipes.add(recipe)
    for (stack in recipe.materials.values()) {
      materials.add(stack!!.item)
    }
    for (stack in recipe.productions.values()) {
      productions.add(stack!!.item)
    }
    if (recipe.ownerBlock != null) blocks.add(recipe.ownerBlock)
  }

  /**以配方的产出项筛选配方，若配方的产出物中包含参数给定的项目则添加到返回列表 */
  fun getRecipesByProduction(production: RecipeItem<*>): Seq<Recipe> {
    return recipes.select { e: Recipe -> e.containsProduction(production) || (e.recipeType == RecipeType.building && e.ownerBlock == production) }
  }

  /**以配方的材料筛选配方，若配方的消耗材料中包含参数给定的项目则添加到返回列表 */
  fun getRecipesByMaterial(material: RecipeItem<*>): Seq<Recipe> {
    return recipes.select { e: Recipe -> e.containsMaterial(material) }
  }

  /**以配方的建筑方块筛选配方，若配方的[Recipe.ownerBlock]与给定的参数相同则添加到返回列表 */
  fun getRecipesByFactory(block: RecipeItem<*>): Seq<Recipe> {
    return recipes.select { e: Recipe -> e.recipeType != RecipeType.building && e.ownerBlock == block }
  }

  /**提供一个方块条目，搜索目标方块的建筑配方*/
  fun getRecipesByBuilding(block: RecipeItem<*>): Seq<Recipe> {
    return recipes.select { e: Recipe -> e.recipeType == RecipeType.building && e.ownerBlock == block }
  }

  fun getByID(id: Int): Recipe {
    return idMap[id]?:throw IllegalArgumentException("No recipe with ID $id")
  }

  fun filterRecipe(filter: Boolf<Recipe>): Seq<Recipe>{
    return recipes.select(filter)
  }

  fun init() {
    for (parser in parsers) {
      parser.init()
    }

    parseRecipes()
  }

  /**从当前游戏内已装载的所有方块进行分析，搜索合适的[RecipeParser]解释方块以获取配方信息并添加到列表之中。
   * 另外，将方块的建造成本生成为配方添加到列表。 */
  @Suppress("UNCHECKED_CAST")
  fun parseRecipes() {
    Vars.content.blocks().forEach { block ->
      parsers.forEach t@{ parser ->
        if (!parser.isTarget(block)) return@t

        parsers.forEach a@{ par ->
          if (par == parser) return@a
          if (par.isTarget(block) && parsers.contains { e -> e.isTarget(block) && e.exclude(parser) })
            return@t
        }

        addRecipe((parser as RecipeParser<Block>).parse(block))
      }

      if (block.requirements.isNotEmpty() && block.placeablePlayer) {
        val recipe = Recipe(
          RecipeType.building,
          TooManyItems.itemsManager.getItem(block),
          block.buildCost
        )

        for (stack in block.requirements) {
          recipe.addMaterialInteger(TooManyItems.itemsManager.getItem(stack.item), stack.amount)
        }
        addRecipe(recipe)
      }
    }
  }

  /**参数给定的条目是否以输入材料的位置参与至少一个配方 */
  fun anyMaterial(uc: RecipeItem<*>?): Boolean {
    return materials.contains(uc)
  }

  /**参数给定的条目是否以产出物的位置参与至少一个配方 */
  fun anyProduction(uc: RecipeItem<*>?): Boolean {
    return productions.contains(uc)
  }

  /**是否有任意一个配方的方块项与参数给定的方块一致 */
  fun anyBlock(uc: RecipeItem<*>?): Boolean {
    return blocks.contains(uc)
  }

  /**参数提供的项目是否可以找到任何一个其参与的配方 */
  fun anyRecipe(uc: RecipeItem<*>): Boolean {
    return materials.contains(uc) || productions.contains(uc) || (uc.item is Block && blocks.contains(uc))
  }
}

