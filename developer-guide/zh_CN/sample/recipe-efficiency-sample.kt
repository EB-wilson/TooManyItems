import mindustry.type.Liquid

//TMI中的配方可模拟计算工作效率，通常这被用于蓝图平衡计算器。
//而效率的计算可大致认为是由配方描述的若干个计算区域组成的多项式，而配方条目的条目类型则决定了它们将处于哪一个计算区
//以下是最终效率的计算公式：
//mul = (base + ATTRIBUTE)*POWER
//eff = NORMAL*max(BOOSTER, 1)*mul*ISOLATE
//其中的各个计算区由配方中所有属于该类型的材料条目依次按照由Recipe中记录的计算方法计算得出
//本程序将向您简要地说明如何定义Recipe的效率计算方法以及模拟计算recipe的工作效率。
fun sample(block: RecipeItem<Block>){
  val recipe = Recipe(
    recipeType = RecipeType.factory,
    ownerBlock = block,
    craftTime = 60f
  ).setBaseEff(0.5f)//设置配方的基础效率为0.5f，即上述公式中的`base`变量

  //您可以为各材料的效率计算区设置它们的计算方法，计算方法由枚举CalculateMethod提供
  //CalculateMethod中描述了几种常用的对一串数字的计算方式
  recipe.setNormalMethod(CalculateMethod.MULTIPLE)//设置NORMAL计算区的计算方法为累乘，该计算区内的所有条目效率累乘作为该计算区的计算结果
  recipe.setAttributeMethod(CalculateMethod.ADD)//设置ATTRIBUTE计算区的计算方法为累加
  recipe.setBoosterMethod(CalculateMethod.MIN)//设置BOOSTER计算区的计算方法为取最小值

  val item1 = TooManyItems.itemsManager.getItem(Items.copper)
  val item2 = TooManyItems.itemsManager.getItem(Items.silicon)
  val item3 = TooManyItems.itemsManager.getItem(Blocks.sand)
  val item4 = TooManyItems.itemsManager.getItem(Liquids.water)
  val item5 = TooManyItems.itemsManager.getItem(Liquids.cryofluid)

  //描述配方内容
  recipe.addMaterial(item1, 1f).setType(RecipeItemType.NORMAL)
  recipe.addMaterial(item2, 1f).setType(RecipeItemType.NORMAL)
  recipe.addMaterial(item3, 1f).setType(RecipeItemType.ATTRIBUTE).setEfficiency(0.5f).setOptional(true)
  recipe.addMaterial(item4, 1f).setType(RecipeItemType.BOOSTER).setEfficiency(1.5f).setOptional(true)
  recipe.addMaterial(item5, 1f).setType(RecipeItemType.BOOSTER).setEfficiency(2f).setOptional(true)

  //Recipe中包含两个用于计算配方消耗倍率的方法calculateMultiple及计算工作效率的方法calculateEfficiency
  //它们分别对应前文公式中的`mul`公式与`eff`公式，我们需要先计算出消耗倍率，再将此倍率提供给效率计算函数

  //这两个方法需要接收一个InputTable作为参数，这是一个记录用于模拟的输入到配方的材料项清单，此处我们初始化一个InputTable并添加输入项
  val param = InputTable()
  param.add(item1, 0.8f)
  param.add(item2, 0.7f)
  param.add(item3, 1f)
  param.add(item5, 1f)

  //计算倍率及效率时，对于每个输入项，设：
  //- input: 清单的输入数量
  //- amount: 配方需要的输入数量
  //- efficiency: 配方中该消耗项的效率系数
  //则此输入项对接收到的清单工作效率系数为`min(input/amount, 1)*efficiency`

  //如果有计算区为空的话，ATTRIBUTE及BOOSTER计算区会使用0作为默认效率，其余计算区则采用1作为该区默认效率
  //对于本案例而言，该清单下的等效计算过程：
  //base = 0.5
  //ATTRIBUTE = min(1/1, 1)*0.5 = 0.5
  //POWER = 1
  //mul = (0.5 + (0.5))*1 = 1
  val mul = recipe.calculateMultiple(param)

  //对于optional的消耗项，会在其效率系数会导致计算结果减小时被无效化，本例中item4输入为0，会被无效化
  //而对于min计算方法的无效化数值为无穷大，即INFINITY，对eff的等效计算过程：
  //NORMAL = min(0.8/1, 1)*1 + min(0.7/1, 1)*1 = 0.8*0.7 = 0.56
  //BOOSTER = min(INFINITY, min(1/1, 1)*2) = 2
  //ISOLATE = 1
  //eff = 0.56*max(2, 1)*1*1 = 0.56 * 2 = 1.12
  val eff = recipe.calculateEfficiency(param, mul)

  println("efficiency: $eff") // -> 1.12
}

//当然，calculateMultiple与calculateEfficiency都是可重写的虚函数
//若您的Recipe需要特殊的计算方式来获取工作效率，则可以通过扩展Recipe并重写这两个函数来自定义计算方式
//如下所示的SampleRecipe类是一个极其简单的自定义效率计算演示
class SampleRecipe(
  recipeType: RecipeType,
  ownerBlock: RecipeItem<*>,
  craftTime: Float = -1f,
): Recipe(recipeType, ownerBlock, craftTime){
  override fun calculateMultiple(parameter: InputTable, multiplier: Float = 1f): Float {
    //令消耗倍率取2次幂
    val superMul = super.calculateMultiple(parameter, multiplier)
    return superMul*superMul
  }

  override fun calculateEfficiency(parameter: InputTable, multiplier: Float = calculateMultiple(parameter)): Float {
    //简单将倍率缩放1.2倍
    return super.calculateEfficiency(parameter, multiplier)*1.2f
  }
}
