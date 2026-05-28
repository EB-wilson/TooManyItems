package tmi.recipe

import arc.Core
import arc.func.Boolf
import arc.func.Func
import arc.graphics.g2d.TextureRegion
import arc.struct.ObjectIntMap
import arc.struct.ObjectMap
import arc.struct.OrderedMap
import arc.struct.Seq
import mindustry.Vars
import mindustry.ctype.ContentType
import mindustry.ctype.UnlockableContent
import mindustry.world.Block
import tmi.util.invoke
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.SingleItemMark
import tmi.util.Consts
import tmi.util.set

class RecipeItemManager {
  private val recipeItems = ObjectMap<Any, RecipeItem<*>>()
  private val itemNameMap = ObjectMap<String, RecipeItem<*>>()

  val wrapper = OrderedMap<Boolf<Any>, Func<*, RecipeItem<*>>>()

  init {
    registerWrapper<UnlockableContent> { RecipeUnlockableContent(it) }
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> registerWrapper(matcher: Boolf<Any>, factory: Func<T, RecipeItem<T>>) {
    wrapper[matcher] = factory as Func<*, RecipeItem<*>>
  }

  @Suppress("UNCHECKED_CAST")
  inline fun <reified T> registerWrapper(factory: Func<T, RecipeItem<T>>) {
    wrapper[Boolf{ e: Any? -> e is T }] = factory as Func<*, RecipeItem<*>>
  }

  fun <T, R : RecipeItem<T>> addItemWrap(item: T, recipeItem: R): R {
    recipeItems.put(item, recipeItem)
    itemNameMap.put(recipeItem.name, recipeItem)

    return recipeItem
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> getItem(item: T): RecipeItem<T> {
    return recipeItems.get(item) {
      for (entry in wrapper) {
        if (entry.key[item]) {
          val res = (entry.value as Func<T, RecipeItem<T>>)(item)
          itemNameMap.put(res.name, res)
          return@get res
        }
      }
      ERROR
    } as RecipeItem<T>
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> getByName(name: String): RecipeItem<T> {
    return itemNameMap[name, ERROR] as RecipeItem<T>
  }

  val list: Seq<RecipeItem<*>>
    get() = recipeItems.values().toSeq().sort()

  private class RecipeUnlockableContent(item: UnlockableContent) : RecipeItem<UnlockableContent>(item) {
    override val ordinal = item.id.toInt()
    override val typeTag: String by lazy {
      if (item is Block) Core.bundle["database-tag." + item.databaseTag, item.databaseTag]
      else Core.bundle["database-category." + item.databaseCategory, item.databaseCategory]
    }
    override val typeID: Int = item.contentType.ordinal
    override val ownMod: String = item.minfo.mod?.name?: Consts.VANILLA
    override val name: String = item.name
    override val localizedName: String = item.localizedName
    override val icon: TextureRegion = item.uiIcon?:throw IllegalStateException("Item $name no icon")
    override val hidden = false
    override val locked = !item.unlockedNow()
    override val hasDetails = true

    override fun displayDetails() = Vars.ui.content.show(item)

    companion object {
      private val mirror = ObjectIntMap<ContentType>()

      init {
        mirror.put(ContentType.item, 0)
        mirror.put(ContentType.liquid, 1)
        mirror.put(ContentType.block, 2)
        mirror.put(ContentType.unit, 3)
      }
    }
  }

  companion object {
    private val ERROR = object : SingleItemMark("<error>") {
      override val ownMod: String get() = "<error>"
      override val icon get() = Core.atlas.find("error")
      override val hidden = true
    }
  }
}
