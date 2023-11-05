package singularity.core;

import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.content.Liquids;
import mindustry.ctype.UnlockableContent;
import mindustry.mod.Mods;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.liquid.LiquidBlock;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.SolidPump;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitAssembler;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.consumers.*;
import singularity.Sgl;
import singularity.game.Recipe;
import singularity.world.blocks.product.NormalCrafter;
import singularity.world.blocks.product.PayloadCrafter;
import universecore.components.blockcomp.ConsumerBuildComp;
import universecore.components.blockcomp.ProducerBlockComp;
import universecore.world.consumers.BaseConsume;
import universecore.world.consumers.ConsumePayload;
import universecore.world.consumers.ConsumeType;
import universecore.world.producers.*;

public class RecipesManager{
  protected static ObjectMap<Boolf<Consume>, Cons2<Recipe, Consume>> vanillaConsParser = new ObjectMap<>();
  protected static ObjectMap<ProduceType<?>, Cons2<Recipe, BaseProduce<?>>> produceParsers = new ObjectMap<>();
  protected static ObjectMap<ConsumeType<?>, Cons2<Recipe, BaseConsume<?>>> consumeParsers = new ObjectMap<>();

  static {
    registerAPIModel();
    registerRawParsers();
  }

  /**注册自定义配方显示的mod交互API，用于其他mod注册其特殊加工工序的生产过程，
   * 通常在非{@link GenericCrafter}，{@link UnitFactory}，{@link Reconstructor}且不是{@link ProducerBlockComp}实例的方块，
   * 若其有独特的生产形式都无法正确以默认的形式注册材料的合成方式，此时就需要采用mod交互API来进行配方声明
   * <p>
   * 该API的一般格式：
   * <pre>{@code
   * ...
   * "recipes": [
   *   {
   *     "type": "factory", //声明该配方项的类型，可选参数有"factory"，"building"，"collecting"，这会影响该配方在UI中的显示模式
   *                        //工厂模式表示该配方是在工厂方块当中进行的工序
   *     "productions": ["$itemName", "$itemName", ...], //该配方的产出物列表，包含所有主产物和副产物
   *     "materials": ["$itemName", "$itemName", ...], //可选，该配方的输入材料列表，通常不包含可选项
   *     "block": "$blockName", //进行该配方加工流程的工厂方块
   *     "description": "@bundleName" //可选，配方的独有描述
   *   },
   *   {
   *     "type": "building", //建筑模式表示该配方是一个建筑的建造成本，通常不会在交互API中使用，该项productions应当留空
   *     "materials": [...],
   *     "block": $blockName, //建造的建筑方块
   *   }，
   *   {
   *     "type": "collecting", //采集模式表示从方块开采矿物或者液体中采集液体之类的操作
   *     "productions": ["$itemName"],
   *     "materials": ["$blockName", ...], //被采集可产出该材料的方块
   *     "block": $blockName, //可选，采集该方块所需的建筑方块
   *     "description": "@bundleName"
   *   },
   *   ...
   * ]
   * ...
   * }</pre>*/
  private static void registerAPIModel() {
    Sgl.interopAPI.addModel(new ModsInteropAPI.ConfigModel("recipes") {
      @Override
      public void parse(Mods.LoadedMod mod, Jval declaring) {

      }

      @Override
      public void disable(Mods.LoadedMod mod) {

      }
    }, true);
  }

  public static void registerVanillaConsParser(Boolf<Consume> type, Cons2<Recipe, Consume> handle){
    vanillaConsParser.put(type, handle);
  }

  @SuppressWarnings("unchecked")
  public static <T extends BaseProduce<?>> void registerProdParser(ProduceType<T> type, Cons2<Recipe, T> handle){
    produceParsers.put(type, (Cons2<Recipe, BaseProduce<?>>) handle);
  }

  @SuppressWarnings("unchecked")
  public static <T extends BaseConsume<?>> void registerConsParser(ConsumeType<T> type, Cons2<Recipe, T> handle){
    consumeParsers.put(type, (Cons2<Recipe, BaseConsume<?>>) handle);
  }

  public static void registerRawParsers(){
    //items
    registerVanillaConsParser(c -> c instanceof ConsumeItems, (recipe, consume) -> {
      for (ItemStack item : ((ConsumeItems) consume).items) {
        recipe.materials.add(item.item);
      }
    });
    registerProdParser(ProduceType.item, (recipe, prod) -> {
      for (ItemStack item : prod.items) {
        recipe.productions.add(item.item);
      }
    });
    registerConsParser(ConsumeType.item, (recipe, cons) -> {
      for (ItemStack item : cons.consItems) {
        recipe.materials.add(item.item);
      }
    });

    //liquids
    registerVanillaConsParser(c -> c instanceof ConsumeLiquids, (recipe, consume) -> {
      for (LiquidStack liquid : ((ConsumeLiquids) consume).liquids) {
        recipe.materials.add(liquid.liquid);
      }
    });
    registerVanillaConsParser(c -> c instanceof ConsumeLiquid, (recipe, consume) -> {
      recipe.materials.add(((ConsumeLiquid) consume).liquid);
    });
    registerProdParser(ProduceType.liquid, (recipe, prod) -> {
      for (LiquidStack liquid : prod.liquids) {
        recipe.productions.add(liquid.liquid);
      }
    });
    registerConsParser(ConsumeType.liquid, (recipe, cons) -> {
      for (LiquidStack liquid : cons.consLiquids) {
        recipe.materials.add(liquid.liquid);
      }
    });

    //payloads
    registerVanillaConsParser(c -> c instanceof ConsumePayloads, (recipe, consume) -> {
      for (PayloadStack stack : ((ConsumePayloads) consume).payloads) {
        recipe.materials.add(stack.item);
      }
    });
    registerProdParser(ProduceType.payload, (recipe, prod) -> {
      for (PayloadStack stack : prod.payloads) {
        recipe.productions.add(stack.item);
      }
    });
    registerConsParser(ConsumeType.payload, (recipe, cons) -> {
      for (PayloadStack stack : cons.payloads) {
        recipe.materials.add(stack.item);
      }
    });
  }

  private final Seq<Recipe> recipes = new Seq<>();
  private final ObjectSet<UnlockableContent> materials = new ObjectSet<>(), productions = new ObjectSet<>();
  private final ObjectSet<Block> blocks = new ObjectSet<>();

  public void addRecipe(Seq<Recipe> recipes){
    for (Recipe recipe : recipes) {
      if (!recipe.productions.isEmpty()) addRecipe(recipe);
    }
  }

  public void addRecipe(Recipe... recipes){
    for (Recipe recipe : recipes) {
      if (!recipe.productions.isEmpty()) addRecipe(recipe);
    }
  }

  public void addRecipe(Recipe recipe) {
    recipes.add(recipe);
    materials.addAll(recipe.materials);
    productions.addAll(recipe.productions);
    if (recipe.block != null) blocks.add(recipe.block);
  }

  public Seq<Recipe> getRecipesByProduction(UnlockableContent production){
    return recipes.select(e -> e.productions.contains(production));
  }

  public Seq<Recipe> getRecipesByMaterial(UnlockableContent material){
    return recipes.select(e -> e.materials.contains(material));
  }

  public Seq<Recipe> getRecipesByFactory(Block block){
    return recipes.select(e -> e.recipeType != Recipe.RecipeType.building && e.block == block);
  }

  public void parseAllDefs(){
    ObjectMap<UnlockableContent, Seq<Floor>> floorCollects = new ObjectMap<>();

    for (Block block : Vars.content.blocks()) {
      if (block instanceof ProducerBlockComp prod) addRecipe(parseFactory(prod));
      else if (block instanceof GenericCrafter crafter) addRecipe(parseVanillaCrafter(crafter));
      else if (block instanceof UnitFactory fac) addRecipe(parseUnitFactory(fac));
      else if (block instanceof UnitAssembler assem) addRecipe(parseUnitAssembler(assem));
      else if (block instanceof Reconstructor rec) addRecipe(parseUnitReconstructor(rec));
      else if (block instanceof SolidPump pump) addRecipe(parsePump(pump));
      else if (block instanceof Floor floor){
        if (floor.itemDrop != null) floorCollects.get(floor.itemDrop, Seq::new).add(floor);
        if (floor.liquidDrop != null) floorCollects.get(floor.liquidDrop, Seq::new).add(floor);
      }

      if (block.requirements.length > 0 && block.placeablePlayer){
        Recipe recipe = new Recipe(Recipe.RecipeType.building);
        recipe.block = block;
        for (ItemStack stack : recipe.block.requirements) {
          recipe.materials.add(stack.item);
        }
        addRecipe(recipe);
      }
    }

    for (ObjectMap.Entry<UnlockableContent, Seq<Floor>> entry : floorCollects) {
      Recipe recipe = new Recipe(Recipe.RecipeType.collecting);
      recipe.productions.add(entry.key);
      recipe.materials.addAll(entry.value);
      addRecipe(recipe);
    }
  }

  public Seq<Recipe> parseFactory(ProducerBlockComp comp){
    Seq<Recipe> res = new Seq<>();
    for (BaseProducers producer : comp.producers()) {
      res.addAll(parseProduction(producer));
    }

    for (Recipe re : res) {
      if (comp instanceof Block) re.block = (Block) comp;
    }

    return res;
  }

  public Recipe parseProduction(BaseProducers producer){
    if (producer.cons == null)
      throw new NullPointerException("producer must bind a consume, but get null");

    Recipe recipe = new Recipe(Recipe.RecipeType.factory);
    recipe.production = producer;
    for (BaseProduce<?> produce : producer.all()) {
      Cons2<Recipe, BaseProduce<?>> cons = produceParsers.get(produce.type());
      if (cons == null) continue;
      cons.get(recipe, produce);
    }

    for (BaseConsume<? extends ConsumerBuildComp> consume : producer.cons.all()) {
      Cons2<Recipe, BaseConsume<?>> cons = consumeParsers.get(consume.type());
      if (cons == null) continue;
      cons.get(recipe, consume);
    }

    return recipe;
  }

  public Recipe parseVanillaCrafter(GenericCrafter crafter){
    Recipe res = new Recipe(Recipe.RecipeType.factory);
    res.block = crafter;
    res.vanillaCons = crafter.nonOptionalConsumers;

    for (Consume consume : crafter.nonOptionalConsumers) {
      for (ObjectMap.Entry<Boolf<Consume>, Cons2<Recipe, Consume>> entry : vanillaConsParser) {
        if (entry.key.get(consume)) entry.value.get(res, consume);
      }
    }

    if (crafter.outputItems == null) {
      if (crafter.outputItem != null) res.productions.add(crafter.outputItem.item);
    }
    else {
      for (ItemStack item : crafter.outputItems) {
        res.productions.add(item.item);
      }
    }

    if (crafter.outputLiquids == null) {
      if (crafter.outputLiquid != null) res.productions.add(crafter.outputLiquid.liquid);
    }
    else {
      for (LiquidStack liquid : crafter.outputLiquids) {
        res.productions.add(liquid.liquid);
      }
    }

    return res;
  }

  public Seq<Recipe> parseUnitReconstructor(Reconstructor reconstructor){
    Seq<Recipe> res = new Seq<>();
    for (UnitType[] upgrade : reconstructor.upgrades) {
      Recipe recipe = new Recipe(Recipe.RecipeType.factory);
      recipe.block = reconstructor;
      recipe.materials.add(upgrade[0]);
      recipe.productions.add(upgrade[1]);
      recipe.vanillaCons = reconstructor.nonOptionalConsumers;

      for (Consume consume : reconstructor.nonOptionalConsumers) {
        for (ObjectMap.Entry<Boolf<Consume>, Cons2<Recipe, Consume>> entry : vanillaConsParser) {
          if (entry.key.get(consume)) entry.value.get(recipe, consume);
        }
      }

      res.add(recipe);
    }

    return res;
  }

  public Seq<Recipe> parseUnitFactory(UnitFactory factory){
    Seq<Recipe> res = new Seq<>();

    for (UnitFactory.UnitPlan plan : factory.plans) {
      Recipe recipe = new Recipe(Recipe.RecipeType.factory);
      recipe.block = factory;
      recipe.vanillaCons = factory.nonOptionalConsumers;

      recipe.productions.add(plan.unit);

      for (ItemStack stack : plan.requirements) {
        recipe.materials.add(stack.item);
      }

      for (Consume consume : factory.nonOptionalConsumers) {
        for (ObjectMap.Entry<Boolf<Consume>, Cons2<Recipe, Consume>> entry : vanillaConsParser) {
          if (entry.key.get(consume)) entry.value.get(recipe, consume);
        }
      }
    }

    return res;
  }

  public Seq<Recipe> parseUnitAssembler(UnitAssembler assem) {
    Seq<Recipe> res = new Seq<>();

    for (UnitAssembler.AssemblerUnitPlan plan : assem.plans) {
      Recipe recipe = new Recipe(Recipe.RecipeType.factory);
      recipe.block = assem;
      recipe.productions.add(plan.unit);
      recipe.vanillaCons = assem.nonOptionalConsumers;

      for (PayloadStack stack : plan.requirements) {
        recipe.materials.add(stack.item);
      }

      for (Consume consume : assem.nonOptionalConsumers) {
        for (ObjectMap.Entry<Boolf<Consume>, Cons2<Recipe, Consume>> entry : vanillaConsParser) {
          if (entry.key.get(consume)) entry.value.get(recipe, consume);
        }
      }

      res.add(recipe);
    }

    return res;
  }

  public Recipe parsePump(SolidPump pump) {
    Recipe res = new Recipe(Recipe.RecipeType.collecting);
    res.block = pump;
    res.productions.add(pump.result);

    for (Consume consume : pump.nonOptionalConsumers) {
      for (ObjectMap.Entry<Boolf<Consume>, Cons2<Recipe, Consume>> entry : vanillaConsParser) {
        if (entry.key.get(consume)) entry.value.get(res, consume);
      }
    }

    if (pump.baseEfficiency <= 0.0001f) {
      for (Block block : Vars.content.blocks()) {
        if (block.attributes.get(pump.attribute) <= 0) continue;
        res.materials.add(block);
      }
    }

    return res;
  }

  public boolean anyMaterial(UnlockableContent uc){
    return materials.contains(uc);
  }

  public boolean anyProduction(UnlockableContent uc){
    return productions.contains(uc);
  }

  public boolean anyBlock(Block b){
    return blocks.contains(b);
  }

  public boolean anyRecipe(UnlockableContent uc) {
    return materials.contains(uc) || productions.contains(uc) || (uc instanceof Block b && blocks.contains(b));
  }
}

