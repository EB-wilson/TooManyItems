package tmi.ui.calculator

import tmi.recipe.types.RecipeItem

/**计算器界面上全部可撤销用户操作的收纳类。
 *
 * 每个内部类都是一次原子操作：构造时记下回滚所需的原始状态，[ITransaction.commit] 执行、
 * [ITransaction.rollback] 撤销。每个事务只记录自己这次操作会改动的那一点状态（一个节点的
 * 入射连线、一个槽位的已选项、一个目标产量），内存开销与操作规模同阶，而不是与整张图同阶。
 *
 * 用法：
 * ```
 * view.commitTransaction(CalculatorTransactions.AddRecipeCard(view, RecipeGraphNode(recipe)))
 * view.undo() / view.redo()
 * ```
 *
 * 约定：
 * - 一个事务实例只提交一次，构造之后不要再改动它捕获的状态；
 * - 界面原有的「断开连线后孤立的上游卡片被自动删除」行为被如实保留，撤销时会把它连同
 *   全部连线放回原来的位置；
 * - 历史表只保证事务自身的对称性，绕开事务直接改图的部分不会被单独还原。
 *
 * @author EBwilson */
class CalculatorTransactions private constructor() {
  /**事务提交或回滚后，视图需要的刷新强度。*/
  enum class Refresh {
    /**只需要重算产量与统计（例如修改目标产量，这样不会打断正在输入的文本框）。*/
    balance,

    /**需要重建整张图（节点、连线与布局）。*/
    structure,
  }

  /**一个节点的全部入射连线记录，用于对称回滚。
   *
   * 连线在模型里只以「消费方 -> 提供方」的形式存储，但一个节点既可能是消费方也可能是提供方，
   * 所以两个方向都要记下来才能完整复原。*/
  class IncidentLinks(node: RecipeGraphNode) {
    private val inputs = node.parentsWithItem()
    private val outputs = node.childrenWithItem().map { (item, nodes) -> item to nodes.toList() }

    /**把记录的连线补回 [node]；已经存在的连线会跳过，因此可以安全地重复调用。*/
    fun restoreTo(node: RecipeGraphNode) {
      inputs.forEach { (item, parent) ->
        if (node.getInput(item) != parent) node.setInput(item, parent)
      }

      outputs.forEach { (item, consumers) ->
        consumers.forEach { consumer ->
          if (consumer.getInput(item) != node) consumer.setInput(item, node)
        }
      }
    }
  }

  /**所有具体事务的基类，只提供视图引用、刷新强度提示与公共小工具。*/
  abstract class Transaction(protected val view: CalculatorView) : ITransaction {
    /**视图在 commit / rollback 之后应当执行的刷新方式。*/
    open val refresh: Refresh get() = Refresh.structure

    /**把 [node] 放回图里 [index] 位置并恢复它的存档序号；[node] 已在图中时不做任何事。*/
    protected fun insertNode(node: RecipeGraphNode, index: Int, graphIndex: Int) {
      if (node.graph == view.graph) return

      view.graph.addNode(node, index)
      node.graphIndex = graphIndex
    }
  }

  /**「某个输入连线发生变化」的事务基类。
   *
   * 负责记录被替换 / 被断开的上游卡片（含它在图里的位置与全部连线）：断开连线可能让它变成
   * 孤立卡片而被自动删除，撤销时必须把它连同连线一起放回去。*/
  abstract class InputChange(
    view: CalculatorView,
    protected val node: RecipeGraphNode,
    protected val item: RecipeItem<*>,
  ) : Transaction(view) {
    /**操作前的上游卡片，可能为空。*/
    protected val old: RecipeGraphNode? = node.getInput(item)

    private val oldLinks = old?.let { IncidentLinks(it) }
    private val oldIndex = old?.let { view.graph.toList().indexOf(it) } ?: -1
    private val oldGraphIndex = old?.graphIndex ?: 0

    /**断开当前输入连线；因此变成孤立卡片的上游节点会被自动删除（与界面原有行为一致）。*/
    protected fun detachInput() {
      if (node.getInput(item) != null) node.disInput(item)
    }

    /**恢复操作前的输入连线，并把它被连带删除的上游卡片放回图中。*/
    protected fun restoreInput() {
      val old = old

      if (old == null) {
        //原本就没有连线：回滚只需保证现在也没有，且不要牵连别的卡片。
        node.disInput(item, removeHovering = false)
        return
      }

      if (old.graph == null) {
        insertNode(old, oldIndex, oldGraphIndex)
        oldLinks?.restoreTo(old)
      }

      //先摘掉槽位上可能残留的其它连线：直接 setInput 覆盖不会清理旧提供方的 outputs。
      val current = node.getInput(item)
      if (current != null && current != old) node.disInput(item, removeHovering = false)

      if (node.getInput(item) != old) node.setInput(item, old)
    }
  }

  /**添加一张配方卡片。[autoLink] 为真时按当前自动连接设置把它接进已有的图。*/
  class AddRecipeCard(
    view: CalculatorView,
    private val node: RecipeGraphNode,
    private val autoLink: Boolean = true,
  ) : Transaction(view) {
    private val graphIndex = node.graphIndex
    private val index = view.graph.toList().size

    /**第一次提交时自动连接建立的连线；重做直接重放它们，不受期间自动连接开关变动的影响。*/
    private var links: IncidentLinks? = null

    override fun commit() {
      insertNode(node, index, graphIndex)

      val recorded = links

      if (recorded == null) {
        if (autoLink) view.linkExisted(node)
        links = IncidentLinks(node)
      }
      else recorded.restoreTo(node)
    }

    override fun rollback() {
      //只摘掉这张卡片自己：它的连线被一并断开，但不会连锁删除别的卡片。
      if (node.graph == view.graph) view.graph.removeNode(node)
    }
  }

  /**删除一张配方卡片，与它相连的所有连线会一并断开。*/
  class RemoveRecipeCard(
    view: CalculatorView,
    private val node: RecipeGraphNode,
  ) : Transaction(view) {
    private val index = view.graph.toList().indexOf(node)
    private val graphIndex = node.graphIndex
    private val links = IncidentLinks(node)

    override fun commit() {
      node.remove()
    }

    override fun rollback() {
      insertNode(node, index, graphIndex)
      links.restoreTo(node)
    }
  }

  /**把 [node] 的 [item] 输入接到 [from] 上；若原本接的是别的卡片会先断开旧连线。*/
  class ConnectInput(
    view: CalculatorView,
    node: RecipeGraphNode,
    item: RecipeItem<*>,
    private val from: RecipeGraphNode,
  ) : InputChange(view, node, item) {
    override fun commit() {
      if (node.getInput(item) == from) return

      detachInput()
      node.setInput(item, from)
    }

    override fun rollback() {
      //先断开新连线（不连锁删除，避免回滚时误删卡片），再恢复原连线。
      node.disInput(item, removeHovering = false)
      restoreInput()
    }
  }

  /**断开 [node] 上 [item] 的输入连线；因此变成孤立卡片的上游节点会被自动删除。*/
  class DisconnectInput(
    view: CalculatorView,
    node: RecipeGraphNode,
    item: RecipeItem<*>,
  ) : InputChange(view, node, item) {
    override fun commit() {
      detachInput()
    }

    override fun rollback() {
      restoreInput()
    }
  }

  /**编辑卡片上的可选输入项。[enabled] 为真时勾选，为假时取消勾选并断开它已有的连线。*/
  class SetOptional(
    view: CalculatorView,
    node: RecipeGraphNode,
    item: RecipeItem<*>,
    private val enabled: Boolean,
  ) : InputChange(view, node, item) {
    private val wasOptional = node.optionals.contains(item)

    override fun commit() {
      if (enabled) {
        node.optionals.add(item)
      }
      else {
        node.optionals.remove(item)
        detachInput()
      }
    }

    override fun rollback() {
      node.optionals.remove(item)
      if (wasOptional) node.optionals.add(item)

      //勾选不会动连线，只有取消勾选才会断开它。
      if (!enabled) restoreInput()
    }
  }

  /**编辑卡片的属性（ATTRIBUTE）槽位选择。
   *
   * [slotItems] 是同一个槽位的全部候选物品：提交时先清空该槽位（一个槽位同时只应有一个
   * 生效项）再按 [enabled] 决定是否写入 [item]，回滚时恢复该槽位原本的已选项。
   * 只有单个候选且不可选的固定槽位不应走这个事务。*/
  class SetAttribute(
    view: CalculatorView,
    private val node: RecipeGraphNode,
    private val item: RecipeItem<*>,
    slotItems: Iterable<RecipeItem<*>>,
    private val enabled: Boolean = true,
  ) : Transaction(view) {
    private val slot = slotItems.toList()
    private val previous = slot.filter { node.attributes.contains(it) }

    override fun commit() {
      slot.forEach { node.attributes.remove(it) }
      if (enabled) node.attributes.add(item)
    }

    override fun rollback() {
      slot.forEach { node.attributes.remove(it) }
      previous.forEach { node.attributes.add(it) }
    }
  }

  /**修改根卡片的目标产量。*/
  class SetTargetAmount(
    view: CalculatorView,
    private val node: RecipeGraphNode,
    private val amount: Int,
  ) : Transaction(view) {
    private val previous = node.targetAmount

    override val refresh: Refresh get() = Refresh.balance

    override fun commit() {
      node.targetAmount = amount
    }

    override fun rollback() {
      node.targetAmount = previous
    }
  }

  /**把多个事务合成一次可撤销操作：提交按顺序执行，回滚按相反顺序执行。
   *
   * 典型场景：从配方选择器里为某个输入选一张新卡片，需要「加卡片 + 取消可选 + 接上新卡片」，
   * 只需要保证子事务在提交前都还没被执行过。*/
  class Composite(
    view: CalculatorView,
    private val transactions: List<ITransaction>,
  ) : Transaction(view) {
    constructor(view: CalculatorView, vararg transactions: ITransaction) : this(view, transactions.toList())

    override fun commit() {
      transactions.forEach { it.commit() }
    }

    override fun rollback() {
      transactions.asReversed().forEach { it.rollback() }
    }
  }
}
