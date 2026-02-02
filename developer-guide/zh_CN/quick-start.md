## 快速开始

> **注意**，若您的mod的内容使用的均是来自Mindustry内默认的内容类型（如`GenericCrafter`等），且没有使用自定义的生产形式去覆盖默认的生产行为，则TMI已经实现了对这些内容的兼容，您无需进行额外的兼容操作。

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

> 您可以将该入口类放置于您的mod中的任意位置，通常建议放入一个子模块中，最终只需要在元信息中正确定义改类的完整类路径即可正常加载。

### 创建并添加配方

配方定义为类型`tmi.recipe.Recipe`，要创建一个配方仅需要实例化一个`Recipe`对象，并在`init`阶段将其添加到配方管理器`TooManyItems.recipesManager`当中:

```kotlin
class MyEntry: RecipeEntry{
  override fun init(){
    val newRecipe = Recipe(...)

    TooManyItems.recipeManager.addRecipe(newRecipe)
  }
}
```

而关于配方的定义，首先需要解释物品包装。
