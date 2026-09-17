package tmi.ui.calculator

import arc.func.Cons
import arc.func.Cons2
import arc.struct.IntMap
import arc.struct.ObjectIntMap
import arc.struct.Seq
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import tmi.TooManyItems
import tmi.recipe.types.RecipeItem
import tmi.util.Consts
import tmi.util.set
import kotlin.math.max

const val SAVE_VERSION = 0

class RecipeGraph: Iterable<RecipeGraphNode>{

  private val recipeNodes = Seq<RecipeGraphNode>()

  fun addNode(node: RecipeGraphNode){
    recipeNodes.add(node)
    node.graph = this
  }

  /**把 [node] 插回 [index] 位置；用于撤销删除时保持节点在表内的原顺序（顺序会影响布局）。*/
  fun addNode(node: RecipeGraphNode, index: Int){
    recipeNodes.insert(index.coerceIn(0, recipeNodes.size), node)
    node.graph = this
  }

  fun removeNode(node: RecipeGraphNode) {
    if (node.graph == this) {
      node.childrenWithItem().forEach { (i, n) -> n.forEach {
        it.disInput(i, false)
      } }
      node.parentsWithItem().forEach { (i, _) -> node.disInput(i, false) }

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
    val depthOf = HashMap<RecipeGraphNode, Int>()

    val flowed = mutableSetOf<RecipeGraphNode>()
    val isolated = mutableSetOf<MutableList<RecipeGraphNode>>()
    recipeNodes.forEach {
      if (!flowed.contains(it)) {
        val nodes = mutableListOf<RecipeGraphNode>()
        isolated.add(nodes)
        it.visit(0, flowed) { dep, node ->
          depthOf[node] = dep
          nodes.add(node)
        }
      }
    }

    val top = isolated.map { sub ->
      sub.filter { it.children().isEmpty() }.takeIf { it.any() }?: listOf(sub.minBy { depthOf[it] ?: 0 })
    }

    top.forEach { list ->
      val visited = mutableSetOf<RecipeGraphNode>()
      val anyRoot = list.any { it.children().isEmpty() }
      list.forEach { root ->
        root.visit(0, visited){ depth, node ->
          set.add(node)
          depthOf[node] = max(depthOf[node] ?: 0, depth)
        }
      }

      val min = set.minOf { depthOf[it] ?: 0 }
      if (anyRoot) {
        visited.forEach { depthOf[it] = if (it.children().isEmpty()) 0 else (depthOf[it] ?: 0) - min + 1 }
      }
      else {
        visited.forEach { depthOf[it] = (depthOf[it] ?: 0) - min }
      }
    }

    set.forEach { callBack.get(depthOf[it] ?: 0, it) }
  }

  fun write(writer: Writes){
    writer.i(SAVE_VERSION)

    val requiredMods = recipeNodes.flatMap { node ->
      node.recipe.requiredMods
    }.toSet()

    writer.i(requiredMods.size)
    requiredMods.forEach { mod ->
      writer.str(mod)
    }

    writer.i(recipeNodes.size)
    recipeNodes.forEach { node ->
      writer.i(node.graphIndex)
      writer.str(node.recipe.flattenID)
      writer.i(node.targetAmount)

      writer.i(node.attributes.size)
      node.attributes.forEach { writer.str(it.name) }
      writer.i(node.optionals.size)
      node.optionals.forEach { writer.str(it.name) }

      val parents = node.parentsWithItem()
      writer.i(parents.size)
      parents.forEach { (item, parent) ->
        writer.str(item.name)
        writer.i(parent.graphIndex)
      }
    }
  }

  fun read(reader: Reads, reversion: Int){
    clear()

    val existedMods = mutableSetOf<String>()
    Vars.mods.list().forEach { mod ->
      existedMods.add(mod.name)
    }

    val mods = reader.i()
    val requiredMods = mutableListOf<String>()
    (0 until mods).forEach { _ ->
      val requiredMod = reader.str()
      requiredMods.add(requiredMod)
    }

    requiredMods.forEach { mod ->
      if (mod != Consts.VANILLA && !existedMods.contains(mod))
        throw MissingModException("Mod $mod does not exist.", requiredMods)
    }

    class Temp(val node: RecipeGraphNode){
      val parents = ObjectIntMap<RecipeItem<*>>()
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

      val numParents = reader.i()
      (0 until numParents).forEach { _ ->
        val item = TooManyItems.itemsManager.getByName<Any>(reader.str())
        val targetIndex = reader.i()

        tmp.parents.put(item, targetIndex)
      }

      list.add(tmp)
    }

    list.forEach { node ->
      recipeNodes.add(node.node)
      node.node.graph = this
    }
    list.forEach {
      it.parents.forEach { entry ->
        val item = entry.key
        val index = entry.value
        val target = indexMap[index]

        it.node.setInput(item, target)
      }
    }
  }

  override fun iterator() = recipeNodes.iterator()

  class MissingModException(msg: String, val requiredMods: List<String>) : Exception(msg)
}
