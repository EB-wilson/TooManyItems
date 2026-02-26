import arc.struct.Seq
import mindustry.Vars
import mindustry.world.Block
import mindustry.world.blocks.environment.Floor
import mindustry.world.blocks.production.AttributeCrafter
import tmi.recipe.Recipe
import tmi.recipe.RecipeItemGroup
import tmi.recipe.RecipeParser
import tmi.recipe.types.RecipeItemType
import tmi.recipe.RecipeType
import tmi.recipe.parser.ConsumerParser
import tmi.recipe.parser.GenericCrafterParser
import kotlin.math.min

//此类型为TMI内部对AttributeCrafter类型的工厂方块的配方解析器。
//此案例将向您演示如何利用Parser解析一系列方块，并说明各步骤中的操作细节。
open class AttributeCrafterParser : ConsumerParser<AttributeCrafter>() {
  //由于AttributeCrafter扩展自GenericCrafter类型，而对于GenericCrafter已经有一个解析器实现。
  //因为过滤方块时，我们使用的是类型检查，因此对于GenericCrafterParser，AttributeCrafter同样能通过过滤器。
  //为此，我们需要在excludes当中将GenericCrafterParser排除掉，使得AttributeCrafter仅由本解析器进行解析。
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(GenericCrafterParser::class.java)

  //本方法会识别并在目标为AttributeCrafter时返回true，仅有此方法返回为true时，方块才会由此解析器进行解析
  override fun isTarget(content: Block): Boolean {
    return content is AttributeCrafter
  }

  override fun parse(content: AttributeCrafter): Seq<Recipe> {
    //由于AttributeCrafter事实上只执行一种配方的生产，这里我们实例化一个Recipe作为解析结果
    val res = Recipe(
      recipeType = RecipeType.factory, //使用工厂作为配方类型
      //getWrap()为RecipeParser中的一个便捷扩展方法，可以便捷地对某个对象获取其包装
      //这与TooManyItems.itemsManager.getItem(content)是等价的
      ownerBlock = content.getWrap(),
      craftTime = content.craftTime,
    ).setBaseEff(content.baseEfficiency)//AttributeCrafter有其基础工作效率，再由环境项提供加算的额外效率。这里我们为配方设置其基础效率

    //ConsumerParser内的工具函数，大多数情况下您只需要直接向本函数提供定义方块生产工作的Consume即可完成材料项的注册
    //如果您有自定义的Consume类型，请参阅ConsumerParser的外部文档以了解添加消耗解析器的方法
    registerCons(res, *content.consumers)

    //对于AttributeCrafter而言，满足其环境属性的地板种类繁多，但其覆盖的地块属性只会生效其中效率最高的一类
    //因此可用的属性地块之间是互斥的，这种情况下我们需要为这些互斥项设置一个配方条目组，即RecipeItemGroup
    //位于同一个组内的条目会占用同一个位置（在布局与计算平衡时），并在显示时轮换显示。
    val attrGroup = RecipeItemGroup()
    //需要遍历所有方块搜索并过滤满足本方块的环境属性需求的方块
    for (block in Vars.content.blocks()) {
      if (content.attribute == null || block.attributes[content.attribute] <= 0 || (block is Floor && block.isDeep)) continue

      //计算此地板填充该方块下方时可以提供的额外效率，关于效率的计算公式请参阅RecipeItemType
      val eff = min(
        (content.boostScale*content.size*content.size*block.attributes[content.attribute]),
        content.maxBoost
      )

      //将地板项添加到配方当中并设置属性
      res.addMaterial(block.getWrap(), (content.size*content.size) as Number)
        .setType(RecipeItemType.ATTRIBUTE)
        .setOptional(content.baseEfficiency > 0.001f)
        .setEfficiency(eff)
        .efficiencyFormat(content.baseEfficiency, eff) //本函数基于该项原有的数量格式化函数，在后方添加其效率增量百分比文本。
        .setGroup(attrGroup)                           //设置该项所属的条目组
    }

    //添加配方输出项
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

    //解析配方返回的是一个配方列表，这考虑的时如单位工厂等可能存在多个配方的情况
    //对于仅有一个配方的工厂仅返回一个单元素Seq即可
    return Seq.with(res)
  }
}
