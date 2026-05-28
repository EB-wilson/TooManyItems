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
  private val recipeNodes = Seq<RecipeGraphNode>()

  fun addNode(node: RecipeGraphNode){
    recipeNodes.add(node)
    node.graph = this
  }

  fun removeNode(node: RecipeGraphNode) {
    if (node.graph == this) {
      node.parentsWithItem().forEach { (i, n) -> n.forEach {
        it.disInput(i, false)
      } }
      node.childrenWithItem().forEach { (i, n) -> node.disInput(i, false) }

      node.graph = null
      node.graphIndex = -1
      recipeNodes.remove(node)
    }
  }

  fun clear(){
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

    val flowed = mutableSetOf<RecipeGraphNode>()
    val isolated = mutableSetOf<MutableList<RecipeGraphNode>>()
    recipeNodes.forEach {
      if (!flowed.contains(it)) {
        val nodes = mutableListOf<RecipeGraphNode>()
        isolated.add(nodes)
        it.visit(0, flowed) { dep, node ->
          node.contextDepth = dep
          nodes.add(node)
        }
      }
    }

    val top = isolated.map { sub ->
      sub.filter { it.parents().isEmpty() }.takeIf { it.any() }?: listOf(sub.minBy { it.contextDepth })
    }

    top.forEach { list ->
      val visited = mutableSetOf<RecipeGraphNode>()
      val anyRoot = list.any { it.parents().isEmpty() }
      list.forEach { root ->
        root.visit(0, visited){ depth, node ->
          set.add(node)
          node.contextDepth = max(node.contextDepth, depth)
        }
      }

      val min = set.minOf { it.contextDepth }
      if (anyRoot) {
        visited.forEach { it.contextDepth = if (it.parents().isEmpty()) 0 else it.contextDepth - min + 1 }
      }
      else {
        visited.forEach { it.contextDepth -= min }
      }
    }

    set.forEach { callBack.get(it.contextDepth, it) }
  }

  fun write(writer: Writes){
    writer.i(recipeNodes.size)
    recipeNodes.forEach { node ->
      writer.i(node.graphIndex)
      writer.str(node.recipe.flattenID)
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
      val recipe = TooManyItems.recipesManager.getByID(reader.str())
      val amount = reader.i()
      val node = RecipeGraphNode(recipe)
      val tmp = Temp(node)

      node.graphIndex = index
      node.targetAmount = amount

      indexMap[node.graphIndex] = node

      val attrs = reader.i()
      (0 until attrs).forEach { _ ->
        node.attributes.add(TooManyItems.itemsManager.getByName<Any>(reader.str()))
      }

      val opts = reader.i()
      (0 until opts).forEach { _ ->
        node.optionals.add(TooManyItems.itemsManager.getByName<Any>(reader.str()))
      }

      val numChildren = reader.i()
      (0 until numChildren).forEach { _ ->
        val item = TooManyItems.itemsManager.getByName<Any>(reader.str())
        val targetIndex = reader.i()

        tmp.children.put(item, targetIndex)
      }

      list.add(tmp)
    }

    list.forEach { node ->
      recipeNodes.add(node.node)
      node.node.graph = this
    }
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