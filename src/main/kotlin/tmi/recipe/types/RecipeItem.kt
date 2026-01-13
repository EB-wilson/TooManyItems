package tmi.recipe.types

import arc.Core
import arc.graphics.g2d.TextureRegion

abstract class RecipeItem<T> protected constructor(@JvmField val item: T) : Comparable<RecipeItem<*>> {
  abstract val ordinal: Int
  abstract val typeOrdinal: Int
  abstract val typeID: Int
  abstract val name: String
  abstract val localizedName: String
  abstract val icon: TextureRegion
  abstract val hidden: Boolean
  abstract val hasDetails: Boolean
  abstract val locked: Boolean

  open fun displayDetails() {}

  override fun compareTo(other: RecipeItem<*>): Int {
    val n = typeOrdinal.compareTo(other.typeOrdinal)

    if (n == 0) {
      return ordinal - other.ordinal
    }

    return n
  }

  override fun toString(): String {
    return "($item)"
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
