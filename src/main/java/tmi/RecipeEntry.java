package tmi;

/**三方mod的被动加载入口基类，为其他mod注册TMI配方提供易用的配方加载器。
 * <p>由于被动加载的特性，调用方可以在其mod中开辟一个类似于{@link mindustry.mod.Mod}的入口分支，
 * 这个分支当中可以安全的依赖TooManyItems的代码，而无需担心在没有安装TMI的情况下造成{@link ClassNotFoundException}
 *
 * <p>要使用这个类型，您只需要像mod表示主类类路径那样，在mod.json(.hjson)当中添加：{@code "recipeEntry": xxx}，并将xxx填写为指向一个继承该类型的类路径即可*/
public abstract class RecipeEntry {
  public abstract void init();
  public abstract void afterInit();
}
