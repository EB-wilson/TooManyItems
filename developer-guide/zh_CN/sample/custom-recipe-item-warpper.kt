
//TMI使用RecipeItem包装所有参与配方的条目，而TMI内默认实现了对游戏原版的大部分内容的包装方法
//而如果您有自定义的且非派生自UnlockableContent的类别需要参与包装，则您需要针对此类型实现它的RecipeItem
//RecipeItem是一个抽象类，包含了作为配方条目需要的所有信息，本样例会向您演示如何添加自定义类型的包装实现

//假定我们有一个不属于UnlockableContent的材料概念，假设它叫做“夸克”
class Quark(
  val type: Int
)

//然后实现夸克的配方包装
class RecipeQuark(quark: Quark): RecipeItem<Quark>(quark){
  override val ordinal get() = quark.type //主序号，用于排序
  override val typeOrdinal get() = 0      //类型序号，用于排序
  override val typeID = 2938746           //类型ID，用于生成标识，应尽量唯一
  override val name = "quark-${quark.type}"
  override val localizedName: String = Core.bundle["quark.type-${quark.type}.name"]
  override val icon: TextureRegion get() = Core.atlas.find("quark-${quark.type}")
  override val hidden = false             //条目是否仅用于描述配方信息而不显示在物品列表中
  override val hasDetails = false         //条目是否携带长按可查看的详情，若为true还需要实现displayDetails()函数
  override val locked = false             //条目当前是否处于锁定状态，这只会在显示时显示一个锁定图标
}

fun sample1(){
  //接着，您可以通过如下方式添加包装函数
  //该函数是一个实用工具函数，通过泛型参数接收匹配输入物类型，后继的函数块接收这个对象后返回它经过包装后的对象
  //这里我们使用Quark类型进行过滤，并直接将其使用上述定义的RecipeQuark进行包装
  TooManyItems.itemsManager.registerWrapper<Quark>{ RecipeQuark(it) }

  //现在，创建几种不同的夸克
  val e1 = Quark(1)
  val e2 = Quark(2)
  val e3 = Quark(3)

  //分别获取它们的配方包装，此操作会对未包装过的物品创建包装实例，并添加到管理器的容器中
  //当再次对同一个对象获取其包装时，会返回那个已经存在的包装
  val recE1 = TooManyItems.itemsManager.getItem(e1)
  val recE2 = TooManyItems.itemsManager.getItem(e2)
  val recE3 = TooManyItems.itemsManager.getItem(e3)

  //此外，您也可以通过getByName(name)来获取已经包装的条目
  val recE1byName = TooManyItems.itemsManager.getByName("quark-1")
  val recE2byName = TooManyItems.itemsManager.getByName("quark-2")
  val recE3byName = TooManyItems.itemsManager.getByName("quark-3")

  Log.info(recE1 == recE1byName) // -> true
  Log.info(recE2 == recE2byName) // -> true
  Log.info(recE3 == recE3byName) // -> true
}

//当然，有时候我们会遇到一些概念性的配方条目，它们经过包装往往仅存在一个单例，例如电力的包装为PowerMark，HeatMark
//TMI基于RecipeItem实现了一层抽象扩展SingleItemMark，它不需要针对某一对象进行包装，其自身即有效的配方条目
//现在还是假定一种材料以太，与夸克不同，它始终只有其本身，而非一类材料的类型，我们可以像如下的形式描述这个配方条目
//SingleItemMark接收一个字符串作为持久唯一标识，在SingleItemMark中包含了其他属性的默认值，并且会自动将此单例添加到条目管理器
object EtherMark: SingleItemMark("ether-mark"){
  override val ordinal = 0
  override val icon: TextureRegion get() = Core.atlas.find("ether")
}

//您可以像使用任何RecipeItem一样直接使用EtherMark这个单例
fun sample2(recipe: Recipe){
  val ether = EtherMark
  recipe.addMaterialPresec(EtherMark, 1f)
}
