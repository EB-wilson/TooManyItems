
//ConsumerParser中包装了对Consume的解析工作，您可以使用registerCons输入Consume来向给定的recipe中添加配方条目
//TMI中实现了对大部分Mindustry原版中的Consume的解析函数，但是如果需要解析自定义的Consume类型，则需要向ConsumerParser中添加解析记录
//此程序演示了一个简单的自定义Consume解析记录声明，您可以参考本程序实现您自己的消耗项解析

//一个假定的消耗类，它同时消耗若干个物品和液体
class ConsumeSample(
  val consItems: List<ItemStack>,
  val consLiquid: LiquidStack
): Consume(){
  //...
}

//您可以使用ConsumerParser中的伴生单例函数registerConsumeParser来添加解析记录
//消耗项解析记录记录本质上是一个过滤器和其回调函数，每当输入一个Consume时，会尝试匹配所有可匹配过滤器的记录，并执行其回调函数块
//而添加配方条目的工作即在回调函数块中进行，如下是一个简易的例子用于解析如上给出的ConsumeSample
fun sample(){
  //这是一个实用工具重载，它意为直接使用提供的泛型参数进行类型判断，来作为本条目的过滤器，详情请参阅外部API文档
  ConsumerParser.registerConsumeParser<ConsumeSample> { recipe, consume, handle ->
    //这个回调函数体会在注册Consume时对通过过滤器的Consume调用
    //接收的三个参数分别为：
    //- recipe：正在操作的配方
    //- consume：传入的消耗项
    //- handle：对新建的RecipeItemStack的外层回调
    //Consume的解析工作事实上与RecipeParser在parse中的工作类似，我们需要从Consume中获取它们的有效信息，并添加到传入的配方当中
    //值得注意的是，我们还需要通过从参数中接收到的函数`handle`去将添加的配方条目传递给调用处，这为的是在配方解析时可以方便的对从Consume中新增的配方条目设置其属性
    //您只需要像如下所示的形式照常添加配方条目，并将新增的RecipeItemStack作为参数去直接调用`handle`即可：
    handle(
      recipe.addMaterialInteger(consume.consLiquid.item.getWrap(), consume.consLiquid.amount)
        .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
        .setOptional(consume.optional)
    )

    //处理列表同理
    for (stack in consume.consItems) {
      handle(
        recipe.addMaterialInteger(stack.item.getWrap(), stack.amount)
          .setType(if (consume.booster) RecipeItemType.BOOSTER else RecipeItemType.NORMAL)
          .setOptional(consume.optional)
      )
    }
  }
}
