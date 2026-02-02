package tmi.ui.calculator

import arc.func.Cons
import arc.func.Cons2
import arc.struct.IntMap
import arc.struct.ObjectIntMap
import arc.struct.Seq
import arc.util.io.Reads
import arc.util.io.Writes
import tmi.TooManyItems
import tmi.recipe.types.RecipeItem
import tmi.util.set
import kotlin.math.max

class RecipeGraph: Iterable<RecipeGraphNode>{
  private var lastIndex = 0
  private val recipeNodes = Seq<RecipeGraphNode>()

  fun addNode(node: RecipeGraphNode){
    recipeNodes.add(node)
    node.graph = this
    node.graphIndex = lastIndex++
  }

  fun removeNode(node: RecipeGraphNode) {
    if (node.graph == this) {
      node.graph = null
      node.graphIndex = -1
      recipeNodes.remove(node)
    }
  }

  fun clear(){
    lastIndex = 0
    recipeNodes.forEach { it.graph = null }
    recipeNodes.clear()
  }

  fun isEmpty() = recipeNodes.isEmpty

  fun eachNode(callBack: Cons<RecipeGraphNode>){
    recipeNodes.forEach { callBack.get(it) }
  }

  fun eachNode(callBack: Cons2<Int, RecipeGraphNode>){
    val set = linkedSetOf<RecipeGraphNode>()

    recipeNodes.forEach { it.contextDepth = 0 }

    for (root in recipeNodes.select { e -> e.parents().isEmpty() }) {
      root.visit(0){ depth, node ->
        set.add(node)
        node.contextDepth = max(node.contextDepth, depth)
      }
    }

    set.forEach { callBack.get(it.contextDepth, it) }
  }

  fun write(writer: Writes){
    writer.i(recipeNodes.size)
    recipeNodes.forEach { node ->
      writer.i(node.graphIndex)
      writer.i(node.recipe.hashCode())
      writer.i(node.targetAmount)

      writer.i(node.attributes.size)
      node.attributes.forEach { writer.str(it.name) }
      writer.i(node.optionals.size)
      node.optionals.forEach { writer.str(it.name) }

      val children = node.childrenWithItem()
      writer.i(children.size)
      children.forEach { (item, child) ->
        writer.str(item.name)
        writer.i(child.graphIndex)
      }
    }
  }

  fun read(reader: Reads){
    clear()

    class Temp(val node: RecipeGraphNode){
      val children = ObjectIntMap<RecipeItem<*>>()
    }

    val list = mutableListOf<Temp>()
    val indexMap = IntMap<RecipeGraphNode>()

    val numNodes = reader.i()
    (0 until numNodes).forEach { _ ->
      val index = reader.i()
      val recipe = TooManyItems.recipesManager.getByID(reader.i())
      val node = RecipeGraphNode(recipe)
      node.graphIndex = index
      node.targetAmount = reader.i()

      indexMap[node.graphIndex] = node

      val attrs = reader.i()
      (0 until attrs).forEach { _ ->
        node.attributes.add(TooManyItems.itemsManager.getByName<Any>(reader.str()))
      }

      val opts = reader.i()
      (0 until opts).forEach { _ ->
        node.optionals.add(TooManyItems.itemsManager.getByName<Any>(reader.str()))
      }

      val tmp = Temp(node)

      val numChildren = reader.i()
      (0 until numChildren).forEach { _ ->
        val item = TooManyItems.itemsManager.getByName<Any>(reader.str())
        val targetIndex = reader.i()

        tmp.children.put(item, targetIndex)
      }

      list.add(tmp)
    }

    list.forEach { addNode(it.node) }
    list.forEach {
      it.children.forEach { entry ->
        val item = entry.key
        val index = entry.value
        val target = indexMap[index]

        it.node.setInput(item, target)
      }
    }
  }

  override fun iterator() = recipeNodes.iterator()
}