## 快速开始

本文档旨在帮助开发者快速了解*TooManyItems*的第三方API结构与使用方法，以简单的案例帮助开发者快速创建对自己模组的配方适配器。

出于行文方便，下文将直接使用*TMI*简写代指*TooManyItems*。

> **注意**，若您的mod的内容使用的均是来自Mindustry内默认的内容类型（如`GenericCrafter`等），且没有使用自定义的生产形式去覆盖默认的生产行为，则TMI已经实现了对这些内容的兼容，您无需进行额外的兼容操作。

### 添加依赖项

在您的项目build.gralde中添加如下内容：

```gradle
dependencies {
    compileOnly 'com.github.EB-wilson:TooManyItems:$tmiVerison'
}
```

如果您尚未将jitpack添加到maven仓库路径，请添加如下声明：

```gradle
dependencyResolutionManagement { 
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 配方入口

TMI为配方及参与配方的条目提供了规范的包装与抽象，几乎可兼容所有可能的生产消耗形式，并为第三方mod提供了独立的模块入口，以使得TMI相关的程序可以存在于一个独立的模块中，并只在用户已安装TMI的情况下才被调用。

要为您的mod编写TMI模块入口，您只需要创建一个类型，并令其实现`tmi.RecipeEntry`接口：

```kotlin
class MyEntry: RecipeEntry{
  override fun init(){
    //主初始化工作
    Log.info("recipe entry init")
  }

  override fun afterInit(){
    //后初始化工作
    Log.info("recipe entry after init")
  }
}
```

然后在您的mod元信息文件（mod.json/mod.hjson）中添加该键值对：`"recipeEntry": "MyEntry"`即可。

若您正确设置了元信息和上述的类型，那么在同时安装了TMI与您的mod时启动游戏，您将会在日志中先后看到`recipe entry init`与`recipe entry after init`两条消息。

**或者**，如果您的mod采用的是Java或者Kotlin而非JavaScript进行编写，您也可以通过在您的Mod主类上方添加`@RecipeEntryPoint`注解来注册您的配方入口：

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

此注册方法的效果与添加mod元信息的效果一致。

> 您可以将该入口类放置于您的mod中的任意位置，通常建议放入一个子模块中，最终只需要在元信息中正确定义改类的完整类路径即可正常加载。

### 配方条目包装

在TMI中，所有的如"物品"、"液体"这样材料或产出物概念，均会被包装为一个标准的配方条目，在程序中定义为一个抽象泛型类`tmi.recipe.RecipeItem<I>`，配方当中存储的正是被包装好的配方条目。

同时，所有的配方条目都会被存储在配方条目管理器内，这是一个单例，被保存在变量`TooManyItems.itemsManager`中，当你需要获取某个对象的配方条目包装时，应当从管理器中获得。

您可以使用`TooManyItems.itemsManager.getItem(object)`来对任意对象获取它的配方条目包装，在配方管理器中已经保存了对Mindustry中大多数默认材料项的实现，因此对默认游戏的大部分物品都可以直接获得其包装：

```kotlin
//铜
val wrappedCopper = TooManyItems.itemsManager.getItem(Items.copper)
//水
val wrappedWater = TooManyItems.itemsManager.getItem(Liquids.water)
//双管
val wrappedDuo = TooManyItems.itemsManager.getItem(Blocks.duo)
//日食
val wrappedEclipse = TooManyItems.itemsManager.getItem(UnitTypes.eclipse)
```

在后文中我们均使用的是通过此方式获得的配方条目对象。

> 如果您对一个没有提供包装方法的类型进行此操作，那么您会得到一个错误条目，它的所有信息都会被标记为'error'。有关自定义实现请参阅`RecipeItemManager`的外部API文档。

### 创建并添加配方

配方被定义为类型`tmi.recipe.Recipe`，所有的配方均被存储在一个配方管理器单例内，该单例被保存在`TooManyItems.recipesManager`中，您创建的配方也应当被添加至此管理器。

要创建一个配方仅需要实例化一个`Recipe`对象，并在`init`阶段将其添加到配方管理器:
 
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

`Recipe`的构造函数带有三个参数：

- `recipeType`：配方的类型，这会决定此配方会如何被可视化以及如何处理平衡及效率计算。
- `ownerBlock`：该配方所属的方块，可能为null，表示该配方无所属方块。
- `craftTime`：配方执行的时长，单位为tick，设置为负数表示该配方为连续生产。

其中`recipeType`为抽象类`tmi.recipe.RecipeType`，此类中保存了4类默认实现：

- `RecipeType.factory`：工厂生产，泛用于各类接收材料并制造产物的工厂配方。
- `RecipeType.building`：建筑建造，用于记录某个方块的建造成本。
- `RecipeType.collecting`：资源采集，泛用于从环境中获取资源的方块如钻头和泵。
- `RecipeType.generator`：能源产出，泛用于各类发电机。

通常这些默认类型足以覆盖大部分配方形式。

### 描述配方内容

TMI中的配方是一个若干配方条目对象与其数量等信息构成的表格，更具体的说，在`Recipe`中分别存储了材料与产出物的一系列配方条目堆，即类型`tmi.recipe.RecipeItemStack`的实例。

`RecipeItemStack`中记录了一个配方条目以及包含数量在内的各信息，使用`addMaterial`或`addProduction`将一个条目堆添加到配方中，此处我们假设输入的条目已经经过包装：

```kotlin
//直接将条目堆添加至配方
recipe.addMaterial(RecipeItemStack(/*...*/))//材料
recipe.addProduction(RecipeItemStack(/*...*/))//产物

//直接添加配方条目，无任何额外设置，此方法会返回其创建的条目堆
recipe.addMaterial(copper, 1f)//材料
recipe.addProduction(titanium, 1f)//产物
```

> `Recipe`中包含了一系列工具方法可以便捷地快速设置一些条目属性。

条目堆中记录了一系列此项的各信息，其中包含了若干链式函数，您可以以形如下方的形式对目标条目的各信息进行设置：

```kotlin
recipe.addMaterial(copper, 1f)
  //设置数量的格式化函数，接收此条目的每刻单位数量，返回经格式化后的数字文本，默认为不显示数字文本
  .setFormat { amount -> "amount: $amount" }
  //设置备选的数量格式化函数，大致同上，用于在按下热键时显示的备选格式化文本
  .setAltFormat { amount -> /*...*/ }
  //设置该条目的类型，这会决定条目参与布局和计算时的行为，默认为NORMAL
  .setType(RecipeItemType.BOOSTER)
  //设置该条目的满载效率，用于配方平衡时的效率计算，默认为1.0f
  .setEfficiency(1.1f)
  //设置条目的可选性，默认为false
  .setOptional(true)
  //设置条目从属的组，属于同一个条目组的条目会在布局和计算时视作同一个位置的若干可选目标
  .setGroup(group)
```

或者使用kotlin风格的声明形式：

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

### 实例说明

以游戏原版的热能坩埚为例，我们可以按照下方的形式定义它的配方：

```kotlin
class MyEntry: RecipeEntry{
  override fun init(){
    val items = TooManyItems.itemsManager
    
    val recipe = Recipe(
      recipeType = RecipeType.factory,
      ownerBlock = items.getItem(Blocks.siliconCrucible),
      craftTime = 90f,
    )

    //addMaterialInteger为一个工具方法，可快捷添加整数数量的项目
    recipe.addMaterialInteger(items.getItem(Items.coal), 4)
    recipe.addMaterialInteger(items.getItem(Items.sand), 6)
    recipe.addMaterialInteger(items.getItem(Items.pyratite), 1)
   
    //addMaterialPresec同样为工具方法，用于快捷设置显示格式为单位时间内的消耗量
    //此处为添加耗电项，电力没有实例，因此由一个单例对象PowerMark提供其概念包装，热量同属此范畴为HeatMark
    recipe.addMaterialPresec(PowerMark, 4f)
    
    val attrGroup = RecipeItemGroup()
    //添加灼热地板的效率增幅环境项
    recipe.addMaterial(items.getItem(Blocks.hotrock), 9/*size*size*/)
      //表示此项为环境项
      .setType(RecipeItemType.ATTRIBUTE)
      // 此项可选
      .setOptional(true)
      // 此可选项生效时的工作效率，ATTRIBUTE区的效率是加算在基础效率上的
      .setEfficiency(0.43f)
      // 此工具函数用于将效率百分比文本附加在已有的数量格式化函数后
      .efficiencyFormat(content.baseEfficiency, 0.43f)
      // 设置此项的组
      .setGroup(attrGroup)
    
    //添加熔岩地板的效率增幅环境项
    recipe.addMaterial(items.getItem(Blocks.magmarock), 9)
      .setType(RecipeItemType.ATTRIBUTE)
      .setOptional(true)
      .setEfficiency(1f)
      .efficiencyFormat(content.baseEfficiency, 1f)
      //同上，这会使得该项与前一项处于同一位置，最终只会生效选中的那一个
      .setGroup(attrGroup)
    
    recipe.addProductionInteger(items.getItem(Items.silicon), 8)

    TooManyItems.recipeManager.addRecipe(newRecipe)
  }
}
```

运行它，即可在TMI的配方浏览器中找到这个自定义的热能坩埚配方，他会与另一个已有的热能坩埚的配方同时存在。

### 配方解析器

在实际情况下，需要添加的配方数量可能非常庞大，我们肯定不能一个个的编写它们的配方声明，为此，我们需要一个自动化的方法来批量分析和生成工厂等方块参与的配方。

TMI提供了配方解析器来遍历并分析游戏中的所有方块，这个工具由一个抽象类`tmi.recipe.RecipeParser<T>`描述，它当中包含了若干抽象API用于筛选过滤目标方块，以及解析目标方块以返回此方块上工作的配方列表。

TMI对Mindustry当中的默认游戏内容的配方解析，正是依赖此方法定义了对原版游戏内容的解析器，实现一个`RecipeParser`的形式大致如下：

```kotlin
class MyParser : RecipeParser<BlockType>() {
  //需要排除的互斥解析器
  override val excludes: Seq<Class<out RecipeParser<*>>> = Seq.with(OtherParser::class.java)

  //初始化阶段调用
  override fun init() {
    Log.info("parser initializing")
  }

  //目标过滤器，只有返回true的目标会被解析
  override fun isTarget(content: Block): Boolean {
    return content is BlockType
  }

  //解析接收到的目标方块，并返回解析后的配方列表
  override fun parse(content: BlockType): Seq<Recipe> {
    Log.info("parsing: $content")
  }
}
```

其中，`excludes`的意义在于解决一种冲突，当我们使用方块的类型来过滤解析目标时，若有两个解析器过滤了在一个类层次结构中的上下两个不同层次的类型，则会导致类层次结构中的子类型也通过了对父级类型的过滤器，而如果我们希望这个子类型只在对应的解析器中解析，则应当在解析子类的那个解析器的excludes中提供需要排除的互斥解析器类型。

您可以按照前文中创建配方的方法，在`parse(content)`函数中去对输入的`content`进行分析，通常我们都能知道这个目标内所声明的配方内容，我们只需要将它们的效率等信息声明到`Recipe`中，并将它添加到列表中再返回即可，方块可能还有多个配方，也只需要向列表中添加多个配方。

在您实现配方解析器后，只需要在配方入口的`init`阶段将它实例化并通过`TooManyItems.recipesManager.registerParser(parser)`函数注册到配方管理器当中即可生效：

```kotlin
class MyEntry: RecipeEntry{
  override fun init(){
    TooManyItems.recipesManager.registerParser(MyParser())
  }
}
```

### ConsumerParser

另外，`RecipeParser`有一个扩展抽象`ConsumerParser`，该类中定义了对Mindustry中`Consume`的匹配与解析工具，默认实现了对游戏中大部分默认消耗类型的解析。

使用此类作为父级时，您只需要使用函数`registerCons(recipe, consumes)`函数即可完成将输入的若干`Consume`解析并添加入配方的工作，同时此方法还有两个重载，会加入一个回调函数用于对解析出来的条目堆进行属性设置：

```kotlin
class MyParser: ConsumerParser<BlockType>() {
  //...
  
  override fun parse(content: BlockType): Seq<Recipe> {
    val recipe = Recipe(/*...*/)
    
    //直接解析目标方块的nonOptionalConsumes，写入recipe
    registerCons(recipe, content.nonOptionalConsumes)
    
    //解析方块的optionalConsumes，所有被添加的条目堆均会调用一次回调
    //回调中将所有解析出的条目均设置为了可选的
    registerCons(recipe, { stack ->
      stack.setOptional(true)
    }, content.optionalConsumes)
    
    //同上，但是回调还会接收正在解析的目标Consume
    registerCons(recipe, { cons, stack ->
      stack.setOptional(true)
    }, content.optionalConsumes)
  }
}
```

> 您可以通过`ConsumerParser.registerConsumeParser(...)`函数来定义对指定类型的`Consume`的解析方法，请参阅`ConsumerParser`的外部API文档。
