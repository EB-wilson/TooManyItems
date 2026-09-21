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

/**影子连线（[RecipeGraph.ShadowLink]）被写入存档的版本。 */
const val SHADOW_LINK_VERSION = 1

const val SAVE_VERSION = SHADOW_LINK_VERSION

class RecipeGraph: Iterable<RecipeGraphNode>{

  private val recipeNodes = Seq<RecipeGraphNode>()

  private val shadowLinkList = Seq<ShadowLink>()

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
    shadowLinkList.clear()
  }

  /**影子连线的伴随数据：把 [consumer] 的 [item] 输入在**布局**里改接到 [source] 的一个副本上。
   *
   * 影子节点只是布局阶段的临时节点（[RecipeGraphLayout.ShadowNode]），**不会**作为 [RecipeGraphNode]
   * 进入本图：真正的连线仍然记在 [consumer] 与 [source] 之间，所以产量、统计与图论结构都不受它影响。
   * 这里保存的只是「哪一条连线需要影子」，随图一起写出 / 读入（见 [write] / [read]）。
   *
   * 影子必然伴生自它登记的那一条连线：连线被断开 / 改接到别处之后，会改连线的那些事务会调用
   * [pruneShadowLinks] 把记录清掉，不会在上下文里留下指向已断连线的残留。*/
  class ShadowLink(
    val source: RecipeGraphNode,
    val item: RecipeItem<*>,
    val consumer: RecipeGraphNode,
  ) {
    override fun equals(other: Any?): Boolean = other is ShadowLink
      && source === other.source
      && consumer === other.consumer
      && item == other.item

    override fun hashCode(): Int =
      31*(31*System.identityHashCode(source) + item.hashCode()) + System.identityHashCode(consumer)

    override fun toString(): String =
      "ShadowLink(source=${source.graphIndex}, item=${item.name}, consumer=${consumer.graphIndex})"
  }

  /**当前登记的全部影子连线记录。[source] / [consumer] 都已经离开本图的记录会一并返回，
   * 但它们不会生效（见 [ShadowLink]），也不会被 [write] 写出。*/
  fun shadowLinks(): List<ShadowLink> = shadowLinkList.toList()

  /**这条连线是否已经登记过影子。*/
  fun hasShadowLink(source: RecipeGraphNode, item: RecipeItem<*>, consumer: RecipeGraphNode): Boolean =
    indexOfShadowLink(source, item, consumer) >= 0

  /**登记一条影子连线；同一 (source, item, consumer) 只会有一条，重复登记会被忽略。
   *
   * @return 是否真的新增了一条记录。*/
  fun addShadowLink(link: ShadowLink): Boolean {
    if (link.source.graph != this || link.consumer.graph != this) return false
    if (indexOfShadowLink(link.source, link.item, link.consumer) >= 0) return false

    shadowLinkList.add(link)
    return true
  }

  /**摘掉一条影子连线记录。
   *
   * @return 是否真的摘掉了一条记录。*/
  fun removeShadowLink(link: ShadowLink): Boolean {
    val index = indexOfShadowLink(link.source, link.item, link.consumer)
    if (index < 0) return false

    shadowLinkList.remove(index)
    return true
  }

  /**一条影子记录还成立吗：两端都还在本图里，而且这条输入连线确实还接在它的 [ShadowLink.source] 上。*/
  private fun isShadowLinkValid(link: ShadowLink): Boolean =
    link.source.graph == this
      && link.consumer.graph == this
      && link.consumer.getInput(link.item) === link.source

  /**清掉全部已经失效的影子记录 —— 连线被断开 / 改接到了别处，或者两端已经离开本图。
   *
   * 影子只伴生它登记的那一条连线，所以连线一断就不该再留在上下文里。会改连线的那些事务在提交时
   * 调用它，并把操作前的记录留一份以便回滚（见 `CalculatorTransactions.InputChange`）。
   *
   * @return 被清掉的条数。*/
  fun pruneShadowLinks(): Int {
    var removed = 0
    for (i in shadowLinkList.size - 1 downTo 0) {
      if (isShadowLinkValid(shadowLinkList[i])) continue

      shadowLinkList.remove(i)
      removed++
    }

    return removed
  }

  /**按 (source, item, consumer) 找一条影子连线；节点按身份比较，物品按值比较。*/
  private fun indexOfShadowLink(
    source: RecipeGraphNode,
    item: RecipeItem<*>,
    consumer: RecipeGraphNode,
  ): Int {
    for (i in 0 until shadowLinkList.size) {
      val link = shadowLinkList[i]
      if (link.source === source && link.consumer === consumer && link.item == item) return i
    }

    return -1
  }

  /**能落盘的影子连线：两端都还在本图里的那些。*/
  private fun persistableShadowLinks(): List<ShadowLink> =
    shadowLinkList.toList().filter { it.source.graph == this && it.consumer.graph == this }

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

    val shadows = persistableShadowLinks()
    writer.i(shadows.size)
    shadows.forEach { link ->
      writer.i(link.source.graphIndex)
      writer.str(link.item.name)
      writer.i(link.consumer.graphIndex)
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

    if (reversion >= SHADOW_LINK_VERSION) readShadowLinks(reader, indexMap)
  }

  /**读取影子连线伴随数据（[SHADOW_LINK_VERSION] 起才有）。两端有一端找不回来的记录直接丢弃：
   * 那说明存档里的图本身已经不自洽，没必要为了它把整张图判为损坏。*/
  private fun readShadowLinks(reader: Reads, indexMap: IntMap<RecipeGraphNode>) {
    val numLinks = reader.i()
    (0 until numLinks).forEach { _ ->
      val sourceIndex = reader.i()
      val item = TooManyItems.itemsManager.getByName<Any>(reader.str())
      val consumerIndex = reader.i()

      val source = indexMap.get(sourceIndex)
      val consumer = indexMap.get(consumerIndex)

      if (source == null || consumer == null) {
        CalculatorLayout.warnOnce(
          "Shadow link dropped: its ${if (source == null) "source" else "consumer"} node is missing (index $sourceIndex / $consumerIndex)."
        )
        return@forEach
      }

      addShadowLink(ShadowLink(source, item, consumer))
    }
  }

  override fun iterator() = recipeNodes.iterator()

  class MissingModException(msg: String, val requiredMods: List<String>) : Exception(msg)
}
