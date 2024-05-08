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
import tmi.recipe.types.RecipeItem

class RecipeItemManager {
  private val recipeItems = ObjectMap<Any, RecipeItem<*>>()
  private val itemNameMap = ObjectMap<String, RecipeItem<*>>()

  fun <T, R : RecipeItem<T>> addItemWrap(item: T, recipeItem: R): R {
    recipeItems.put(item, recipeItem)
    itemNameMap.put(recipeItem.name(), recipeItem)

    return recipeItem
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> getItem(item: T): RecipeItem<T> {
    return recipeItems.get(item) {
      for (entry in wrapper) {
        if (entry.key[item]) {
          val res = (entry.value as Func<T, RecipeItem<T>>).get(item)
          itemNameMap.put(res.name(), res)
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
    override fun ordinal(): Int {
      return item.id.toInt()
    }

    override fun typeID(): Int {
      return mirror[item.contentType, item.contentType.ordinal]
    }

    override fun name(): String {
      return item.name
    }

    override fun localizedName(): String {
      return item.localizedName
    }

    override fun icon(): TextureRegion {
      return item.uiIcon
    }

    override fun hidden(): Boolean {
      return false
    }

    override fun locked(): Boolean {
      return !item.unlockedNow()
    }

    override fun hasDetails(): Boolean {
      return true
    }

    override fun displayDetails() {
      Vars.ui.content.show(item)
    }

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
    private val ERROR = object : RecipeItem<String?>("error") {
      override fun ordinal(): Int {
        return -1
      }

      override fun typeID(): Int {
        return -1
      }

      override fun name(): String {
        return "<error>"
      }

      override fun localizedName(): String {
        return "<error>"
      }

      override fun icon(): TextureRegion {
        return Core.atlas.find("error")
      }

      override fun hidden(): Boolean {
        return true
      }
    }

    private val wrapper = OrderedMap<Boolf<Any>, Func<*, RecipeItem<*>>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> registerWrapper(matcher: Boolf<Any>, factory: Func<T, RecipeItem<T>>) {
      wrapper.put(matcher, factory as Func<*, RecipeItem<*>>)
    }

    init {
      registerWrapper(
        Boolf { e: Any? -> e is UnlockableContent },
        Func<UnlockableContent, RecipeItem<UnlockableContent>> { item ->
          RecipeUnlockableContent(item)
        })
    }
  }
}
