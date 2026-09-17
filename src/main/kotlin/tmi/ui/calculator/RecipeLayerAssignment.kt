package tmi.ui.calculator

/**
 * 层分配（长边最小化）—— 精确的整数规划求解器。
 *
 * 这一层只处理「谁在哪一层」：输入是每个节点的下游消费者 / 上游提供方（都是按连线计的，
 * 同一对节点之间有几条材料连线就有几条），输出是每个节点的层号。它不依赖 arc、
 * 不依赖界面，[RecipeGraphLayout] 只负责把节点表转成这两张邻接表、再把结果写回去。
 *
 * ## 问题
 *
 * 连线的长度按「跨了几个层级」计：连接 [消费者] 与 [提供方] 的那条线，长度就是两层号之差。
 * 于是要在满足「提供方必须比消费方至少深一层」的约束下最小化全图连线长度之和：
 *
 *     min  Σ_边 ( depth(提供方) - depth(消费者) )
 *     s.t. depth(提供方) >= depth(消费者) + 1
 *          depth(最终产物) = 0        // 第 0 层就是最终产物行，[RecipeGraphLayout.Node.isRoot] 依赖它
 *
 * 目标里的层号是一个节点一个变量，约束全是「两个变量之差 ≥ 1」——这类约束的系数矩阵
 * 是全单模的，所以整数最优解与线性规划最优解重合，可以用一次最大流精确解出来。
 *
 * ## 解法：最大权闭合子图
 *
 * 记 `z(v,t) = [ depth(v) >= t ]`（t = 1..K）。`z` 的取值不是任意的，它必须满足三组蕴含关系：
 *
 *   - 前缀：   z(v,t+1) ⇒ z(v,t)                       （层号 ≥ t+1 当然也 ≥ t）
 *   - 跨层：   z(消费者,t) ⇒ z(提供方,t+1)              （提供方必须更深一层）
 *   - 基例：   z(提供方,1) = true                       （"≥ 0" 恒真，于是 t = 0 的这一档推出 ≥ 1）
 *   - 锚定：   z(最终产物,t) = false                    （最终产物钉在第 0 层）
 *   - 上限：   z(有提供方的节点,K) = false              （第 K 层之上没有层了，提供方推不上去）
 *
 * 目标是 `Σ_v c_v · Σ_t z(v,t)`，其中 `c_v = 下游连线数 - 上游连线数`；把权取负就是
 * **最大权闭合子图**：点权 `-c_v`、蕴含关系是「选了 u 就必须选 w」的有向边，
 * 经典做法是加源点/汇点后用一次最小割求出最优闭包；源点一侧的变量就是被选中的 `z`。
 *
 * ## 层号上限 K
 *
 * 取 `K = H + 1`，H 是「最长消费者链」分层的最大层号（也就是图里最长路径的边数）。
 * 任何可行解都不会超过它：层集合 `T_t = { v : depth(v) >= t }` 满足 `T_{t+1} ⊇ N(T_t)`
 * （N 取提供方），于是 `T_M ⊇ N^(M-1)(T_1)`；若 M > H + 1，就存在长度 > H 的提供方链，
 * 与 H 是最长链矛盾。所以按 K 层建图不会切掉任何可行解，得到的就是全问题的最优解。
 * 但层号被截断后**必须**补上「有提供方的节点不能出现在第 K 层」这条约束（见上表「上限」），
 * 否则闭包会在第 K 层白拿收益、解出不可行的层号。
 *
 * ## 兜底
 *
 * 图里有环（[RecipeGraphLayout.resolveLoops] 之后理论上不会再有）或者规模超出 [MAX_VARIABLES]
 * 时，退回「最长消费者链」分层：它满足全部约束（最终产物仍在第 0 层），只是不保证最小，
 * 并且会通过 [warn] 报一条日志。
 */
internal object RecipeLayerAssignment {
  /**
   * 建图规模上限（变量数 = 节点数 × 层上限）。超过就退回最长链分层，
   * 避免极端图上为了几像素的连线长度把布局拖住。
   */
  private const val MAX_VARIABLES = 200_000

  /** 层号上限相对最长链的余量，理由见类注释。 */
  private const val DEPTH_SLACK = 1

  /**
   * 求一组层号。[consumers] / [providers] 是同一批节点的邻接表（按连线计，允许重边），
   * 下标即节点编号，必须互为反向边。
   *
   * @return 每个节点的层号；返回 null 表示图里有环、算不出可行解（调用方保持原层号）。
   */
  fun solve(
    consumers: Array<IntArray>,
    providers: Array<IntArray>,
    warn: (String) -> Unit = {},
  ): IntArray? {
    val count = consumers.size
    if (count != providers.size) throw IllegalArgumentException("Consumers and providers must describe the same nodes.")
    if (count == 0) return IntArray(0)

    // 最长消费者链分层：既是可行解，也给出层号上限 H
    val minimal = longestChainDepth(consumers, providers) ?: return null

    var maxDepth = 0
    for (depth in minimal) if (depth > maxDepth) maxDepth = depth

    val levels = maxDepth + DEPTH_SLACK
    if (count.toLong()*levels > MAX_VARIABLES) {
      warn("Layer assignment skipped: too many layer variables ($count × $levels), layers fall back to the longest chain.")
      return minimal
    }

    val exact = solveExact(consumers, providers, levels)
    if (exact == null) {
      warn("Layer assignment failed, layers fall back to the longest chain.")
      return minimal
    }
    return exact
  }

  /**
   * 「每个节点都上提到下游消费者允许的最浅层」的层号：没有下游的就是第 0 层，否则比最深的
   * 下游再深一层。Kahn 拓扑序（先消费者、后提供方）一趟算完。
   *
   * @return 层号；图里有环时返回 null（有节点定不了层）。
   */
  private fun longestChainDepth(consumers: Array<IntArray>, providers: Array<IntArray>): IntArray? {
    val count = consumers.size
    val depth = IntArray(count)
    val pending = IntArray(count) { consumers[it].size }
    val queue = IntArray(count)
    var head = 0
    var tail = 0

    for (v in 0 until count) if (pending[v] == 0) queue[tail++] = v

    var resolved = 0
    while (head < tail) {
      val v = queue[head++]
      resolved++

      var d = 0
      val children = consumers[v]
      for (i in children.indices) {
        val candidate = depth[children[i]] + 1
        if (candidate > d) d = candidate
      }
      depth[v] = d

      val parents = providers[v]
      for (i in parents.indices) {
        val parent = parents[i]
        if (--pending[parent] == 0) queue[tail++] = parent
      }
    }

    return if (resolved == count) depth else null
  }

  /**
   * 用一次最大流求最大权闭合子图，得到精确最优层号。
   *
   * @param levels 层号上限 K，变量 (v,t) 的编号是 `v * levels + (t - 1)`。
   */
  private fun solveExact(consumers: Array<IntArray>, providers: Array<IntArray>, levels: Int): IntArray? {
    val count = consumers.size
    val variables = count*levels
    val source = variables
    val sink = variables + 1
    val flow = MaxFlow(variables + 2)

    // 变量点权 w = -(消费者数 - 提供方数)：w > 0 的从源点接收益边，w < 0 的接汇点成本边。
    // 收益之和（也就是任何只切有限边的割的上界）用来定无穷大的取值。
    var profit = 0L
    for (v in 0 until count) {
      val weight = providers[v].size - consumers[v].size
      val base = v*levels
      if (weight > 0) {
        profit += weight.toLong()*levels
        for (t in 0 until levels) flow.addEdge(source, base + t, weight.toLong())
      }
      else if (weight < 0) {
        for (t in 0 until levels) flow.addEdge(base + t, sink, -weight.toLong())
      }
    }

    val infinite = profit + 1L

    for (v in 0 until count) {
      val base = v*levels

      // 前缀：z(v,t+1) ⇒ z(v,t)
      for (t in 1 until levels) flow.addEdge(base + t, base + t - 1, infinite)

      // 基例 / 锚定：提供方至少在第 1 层；最终产物不能出现在第 1 层及以上
      if (consumers[v].isEmpty()) {
        for (t in 0 until levels) flow.addEdge(base + t, sink, infinite)
      }
      else {
        flow.addEdge(source, base, infinite)
      }

      // 上限边界：有提供方的节点不能出现在第 K 层 —— 那要求它的提供方在第 K+1 层，越界。
      // 少了这条，闭包可以「白拿」第 K 层的收益（不用把提供方一起推下去），
      // 解出来的层号会不可行，然后被 isFeasible 挡下来退回最长链分层。
      if (providers[v].isNotEmpty()) flow.addEdge(base + levels - 1, sink, infinite)

      // 跨层：z(消费者,t) ⇒ z(提供方,t+1)
      val parents = providers[v]
      for (i in parents.indices) {
        val parentBase = parents[i]*levels
        for (t in 0 until levels - 1) flow.addEdge(base + t, parentBase + t + 1, infinite)
      }
    }

    flow.run(source, sink)

    // 最小割的源点一侧就是最优闭包：层号 = 被选中的 z(v,t) 的个数
    val selected = flow.reachableFrom(source)
    val depth = IntArray(count)
    for (v in 0 until count) {
      val base = v*levels
      var d = 0
      for (t in 0 until levels) if (selected[base + t]) d++
      depth[v] = d
    }

    // 理论上一定可行；万一不是（浮点无关，纯整数，只会是算错），交给调用方兜底
    return if (isFeasible(depth, consumers, providers)) depth else null
  }

  /** 校验：提供方比消费方深、最终产物在第 0 层。 */
  private fun isFeasible(depth: IntArray, consumers: Array<IntArray>, providers: Array<IntArray>): Boolean {
    for (v in depth.indices) {
      if (consumers[v].isEmpty() && depth[v] != 0) return false
      val parents = providers[v]
      for (i in parents.indices) if (depth[parents[i]] < depth[v] + 1) return false
    }
    return true
  }

  /**
   * Dinic 最大流。节点数不大但边不少，所以用前向星（head/next）紧凑存，容量用 Long。
   * 增广走的是迭代版阻塞流：这个网络里残量图上的路径可能很长，递归容易把栈压爆。
   */
  private class MaxFlow(nodeCount: Int) {
    private val head = IntArray(nodeCount) { -1 }
    private var edgeTo = IntArray(64)
    private var edgeNext = IntArray(64)
    private var edgeCap = LongArray(64)
    private var edgeCount = 0

    private val level = IntArray(nodeCount)
    private val cursor = IntArray(nodeCount)
    private val queue = IntArray(nodeCount)

    fun addEdge(from: Int, to: Int, capacity: Long) {
      if (edgeCount + 2 > edgeTo.size) {
        var size = edgeTo.size
        while (size < edgeCount + 2) size = size shl 1
        edgeTo = edgeTo.copyOf(size)
        edgeNext = edgeNext.copyOf(size)
        edgeCap = edgeCap.copyOf(size)
      }

      edgeTo[edgeCount] = to
      edgeCap[edgeCount] = capacity
      edgeNext[edgeCount] = head[from]
      head[from] = edgeCount
      edgeCount++

      edgeTo[edgeCount] = from
      edgeCap[edgeCount] = 0L
      edgeNext[edgeCount] = head[to]
      head[to] = edgeCount
      edgeCount++
    }

    fun run(source: Int, sink: Int) {
      while (buildLevels(source, sink)) {
        System.arraycopy(head, 0, cursor, 0, head.size)
        blockingFlow(source, sink)
      }
    }

    private fun buildLevels(source: Int, sink: Int): Boolean {
      java.util.Arrays.fill(level, -1)
      level[source] = 0

      var headIndex = 0
      var tail = 0
      queue[tail++] = source
      while (headIndex < tail) {
        val u = queue[headIndex++]
        var e = head[u]
        while (e != -1) {
          val v = edgeTo[e]
          if (edgeCap[e] > 0L && level[v] < 0) {
            level[v] = level[u] + 1
            queue[tail++] = v
          }
          e = edgeNext[e]
        }
      }

      return level[sink] >= 0
    }

    /** 在当前分层图上把所有增广路一次推完。 */
    private fun blockingFlow(source: Int, sink: Int) {
      val pathEdge = IntArray(level.size)
      val pathNode = IntArray(level.size)
      pathNode[0] = source
      var depth = 0

      while (true) {
        val u = pathNode[depth]

        if (u == sink) {
          var bottleneck = Long.MAX_VALUE
          for (i in 0 until depth) {
            val capacity = edgeCap[pathEdge[i]]
            if (capacity < bottleneck) bottleneck = capacity
          }
          for (i in 0 until depth) {
            edgeCap[pathEdge[i]] -= bottleneck
            edgeCap[pathEdge[i] xor 1] += bottleneck
          }

          // 退到第一条被推满的边上，从那里继续找
          var i = 0
          while (i < depth && edgeCap[pathEdge[i]] > 0L) i++
          depth = i
          continue
        }

        var e = cursor[u]
        while (e != -1) {
          val v = edgeTo[e]
          if (edgeCap[e] > 0L && level[v] == level[u] + 1) break
          e = edgeNext[e]
        }
        cursor[u] = e

        if (e == -1) {
          // 死路：把 u 从分层图里摘掉再退回一步
          if (depth == 0) return
          level[u] = -1
          depth--
          cursor[pathNode[depth]] = edgeNext[pathEdge[depth]]
          continue
        }

        pathEdge[depth] = e
        depth++
        pathNode[depth] = edgeTo[e]
      }
    }

    /** 残量图上从 [source] 可达的点集 —— 即最小割的源点一侧。 */
    fun reachableFrom(source: Int): BooleanArray {
      val seen = BooleanArray(level.size)
      val stack = IntArray(level.size)
      var top = 0

      seen[source] = true
      stack[top++] = source
      while (top > 0) {
        val u = stack[--top]
        var e = head[u]
        while (e != -1) {
          val v = edgeTo[e]
          if (edgeCap[e] > 0L && !seen[v]) {
            seen[v] = true
            stack[top++] = v
          }
          e = edgeNext[e]
        }
      }

      return seen
    }
  }
}
