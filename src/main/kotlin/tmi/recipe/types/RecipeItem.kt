package tmi.recipe.types

import arc.Core
import arc.graphics.g2d.TextureRegion

abstract class RecipeItem<T> protected constructor(@JvmField val item: T) : Comparable<RecipeItem<*>> {
  @Suppress("DEPRECATION") open val ordinal: Int get() = ordinal()
  @Suppress("DEPRECATION") open val typeOrdinal: Int get() = typeOrdinal()
  @Suppress("DEPRECATION") open val typeID: Int get() = typeID()
  @Suppress("DEPRECATION") open val name: String get() = name()
  @Suppress("DEPRECATION") open val localizedName: String get() = localizedName()
  @Suppress("DEPRECATION") open val icon: TextureRegion get() = icon()
  @Suppress("DEPRECATION") open val hidden: Boolean get() = hidden()
  @Suppress("DEPRECATION") open val hasDetails: Boolean get() = hasDetails()
  @Suppress("DEPRECATION") open val locked: Boolean get() = locked()

  @Deprecated("Use property", ReplaceWith("this.ordinal")) open fun ordinal() = -1
  @Deprecated("Use property", ReplaceWith("this.typeOrdinal")) open fun typeOrdinal() = -1
  @Deprecated("Use property", ReplaceWith("this.typeID")) open fun typeID() = -1
  @Deprecated("Use property", ReplaceWith("this.name")) open fun name() = ""
  @Deprecated("Use property", ReplaceWith("this.localizedName")) open fun localizedName() = ""
  @Deprecated("Use property", ReplaceWith("this.icon")) open fun icon(): TextureRegion = Core.atlas.find("error")
  @Deprecated("Use property", ReplaceWith("this.hidden")) open fun hidden() = false
  @Deprecated("Use property", ReplaceWith("this.hasDetails")) open fun hasDetails() = false
  @Deprecated("Use property", ReplaceWith("this.locked")) open fun locked() = false

  open fun displayDetails() {}

  override fun compareTo(other: RecipeItem<*>): Int {
    val n = typeOrdinal.compareTo(other.typeOrdinal)

    if (n == 0) {
      return ordinal - other.ordinal
    }

    return n
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val that = other as RecipeItem<*>
    return item == that.item
  }

  override fun hashCode(): Int {
    return typeID*31 + name.hashCode()
  }
}
