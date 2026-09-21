package tmi.ui.calculator

import arc.Core
import arc.files.Fi
import arc.func.Boolp
import arc.graphics.Camera
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.input.KeyCode
import arc.math.Mathf
import arc.math.geom.Rect
import arc.scene.Element
import arc.scene.Group
import arc.scene.event.*
import arc.scene.ui.Button
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.Seq
import arc.util.Align
import arc.util.Log
import arc.util.Tmp
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import tmi.graphic.ChunkedFrameBuffer
import tmi.recipe.AmountFormatter
import tmi.recipe.RecipeItemStack
import tmi.recipe.types.RecipeItem
import tmi.recipe.types.RecipeItemType
import tmi.ui.*
import tmi.ui.calculator.RecipeGraphElement.AddRecipeButton
import tmi.ui.calculator.RecipeGraphLayout.ShadowKind
import tmi.util.Consts
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class CalculatorView: Table(), CalculatorDialog.TipsProvider {
  companion object {
    val statisticDialog = StatisticDialog()
  }

  var isUpdated = false
    private set
  var astringentValid = false
    private set

  var browsMode = false
  var showGrid = true
  var autoLinkInput = true
  var autoLinkOutput = true

  var graph = RecipeGraph()
    set(value) {
      field = value
      graphUpdated()
    }
  var statistic = RecipeStatistic(graph)

  /**历史事务表，内部游标是「已执行 / 已回滚」的分界：只有紧邻分界的那一个事务可以被回滚，
   * 也只有紧邻分界的那一个已回滚事务可以被重做。回滚后提交新事务会丢弃分界之后的全部事务。*/
  val history = Seq<ITransaction>()

  /**历史表容量上限，超出后丢弃最早的事务。*/
  var historyLimit = 200

  private var historyCursor = 0

  var imageGenerating: Boolean = false
    private set
  private var tmpGridAlpha: Float = 1f

  private var layers: Array<Seq<RecipeGraphLayout.Node>> = arrayOf()

  private val recipeElements = Seq<RecipeGraphElement>()
  private val nodeToElement = ObjectMap<RecipeGraphLayout.Node, RecipeGraphElement>()

  /**「添加配方」按钮。它是纯界面入口，不是图里的节点：不进 [layers]，因此不参与布局宽度与位置的计算，
   * 只在首层排完之后贴到首层节点的最右侧（见 [CalculatorLayout.layoutAddButton]）。*/
  private var addButton: AddRecipeButton? = null

  /**当前悬停到的那张卡片。两者最多只有一个非空 —— 它们决定这一帧画哪几条影子连线。*/
  private var hoveringShadow: ShadowTab? = null
  private var hoveringRecipe: RecipeTab? = null

  private val shadowTabs = Seq<ShadowTab>()

  /**影子节点连回真身的虚线；每次布局重算一次，绘制时直接照抄（见 [layoutShadowLines]）。*/
  private val shadowRoutes = Seq<ShadowRoute>()
  private val shadowBranches = ObjectMap<ShadowTab, ShadowBranch>()

  private val linkLines = Seq<CalculatorLayout.LinkLine>()
  private val layerCenter = Seq<Float>()

  private var lastZoom: Float = -1f
  private var panX: Float = 0f
  private var panY: Float = 0f

  private val viewBound = Rect()
  private lateinit var graphView: Group
  private lateinit var container: Group
  private lateinit var zoom: Group

  private lateinit var ioList: Table
  private lateinit var status: Table
  private lateinit var inputs: Table
  private lateinit var optionalInputs: Table
  private lateinit var outputs: Table

  private val tabSelectors = object : Group() {
    var selecting: RecipeItem<*>? = null
      private set

    init {
      visible = false
      touchable = Touchable.childrenOnly
    }

    fun hide(){
      selecting = null
      visible = false
    }

    fun show(node: RecipeGraphNode, item: RecipeItem<*>): Boolean {
      //只有真身卡片能作为连接目标：影子是另一个类型（[ShadowTab]），压根不在这个列表里 ——
      //它与真身共用同一个 targetNode，点它等于给真身接线，只会和真身那条候选重复。
      val validNodes = recipeElements
        .filterIsInstance<RecipeTab>()
        .filter { it.node.recipe.containsProduction(item) }

      selecting = item
      visible = true

      clearChildren()
      validNodes.forEach { tab ->
        val select = Button()
        select.style = Consts.recipeTabSelector
        select.clicked {
          val target = tab.node.targetNode

          if (node.getInput(item) != target) {
            //输入还没接在这张卡片上：这是一次普通连接。
            this@CalculatorView.commitTransaction(
              CalculatorTransactions.ConnectInput(this@CalculatorView, node, item, target)
            )
          }
          else if (this@CalculatorView.graph.hasShadowLink(target, item, node)) {
            //已经接在这张卡片上、而且这条连线正由它的影子呈现：再点一次就是把影子撤掉，恢复直连。
            this@CalculatorView.commitTransaction(
              CalculatorTransactions.RemoveShadowNode(this@CalculatorView, node, item, target)
            )
          }
          else {
            //已经接在这张卡片上：把这条连线收起来，交给它的影子副本呈现。
            this@CalculatorView.commitTransaction(
              CalculatorTransactions.MakeShadowNode(this@CalculatorView, node, item, target)
            )
          }
          hide()
        }
        select.setBounds(
          tab.x - Scl.scl(16f), tab.y - Scl.scl(16f),
          tab.width + Scl.scl(32f), tab.height + Scl.scl(32f)
        )
        select.addEventBlocker()
        addChild(select)
      }

      return validNodes.any()
    }
  }

  /**一个影子节点接出来的那条横向支线。*/
  private class ShadowBranch(val tab: ShadowTab) {
    var route: ShadowRoute? = null
    var x = 0f
    var y = 0f
  }

  /**同一个真身的全部影子连回真身的一束虚线。
   *
   * 它们共用一条竖直**主支** [trunkX]：每条支线从影子横向走到主支上（[ShadowBranch.x] → [trunkX]），
   * 主支再单独接一条线回真身（[sourceX] / [sourceY]）。多条主支之间用 [lane] 错开 —— 0 是离图最近的
   * 一条，越大越靠左；分配方式见 [assignShadowLanes]。
   *
   * 主支的纵向区间、以及这一束里哪些支线要画，都在绘制时按当前悬停现算（见 `drawShadowLines`）：
   * 默认只画断环替身，悬停到真身时画出它的全部影子，悬停到某个影子时只画那一条。*/
  private class ShadowRoute(val source: RecipeGraphLayout.RecNode) {
    val branches = mutableListOf<ShadowBranch>()

    /**全部横向支线的 y，外加真身那一端；[assignShadowLanes] 用它判断「谁跨过谁」。*/
    val stubYs = mutableListOf<Float>()

    var lane = 0
    var trunkX = 0f
    var topY = 0f
    var bottomY = 0f
    var sourceX = 0f
    var sourceY = 0f
  }

  override fun getTip(): String = Core.bundle["calculator.tips.selectExisted"]
  override fun tipValid(): Boolean = tabSelectors.visible

  /**记下当前悬停到的卡片；两者互斥，后进来的那个把另一个清掉（进出事件可能交错）。*/
  private fun focusHover(tab: Element) {
    hoveringShadow = tab as? ShadowTab
    hoveringRecipe = tab as? RecipeTab
  }

  /**指针离开 [tab] 时才清空；离开的是别人就不动（避免两个卡片的进出事件互相踩）。*/
  private fun clearHover(tab: Element) {
    if (hoveringShadow === tab) hoveringShadow = null
    if (hoveringRecipe === tab) hoveringRecipe = null
  }

  /**让这张卡片参与影子连线的悬停显隐（见 `drawShadowLines`）。有两件事必须都做对：
   *
   * 1. **卡片自己要可被命中**。卡片是 `Table`，默认 `Touchable.childrenOnly` —— 指针落在它的空白处
   *    时不会被命中，悬停事件压根不会产生，于是只有移到某个格子（子元素）上才有效果。
   * 2. **不能用 `Convenients.enterSt`**。它把「从空处进入」的 enter（`fromActor == null`）当成卡片内部
   *    的移动跳过了，指针从图外挪到卡片上时反而不触发。这里任何一次 enter 都算数。
   *
   * exit 只在真的离开整张卡片（去往非子元素）时才清空，所以卡片内部的格子之间移动不会中断悬停。
   * 两个状态都是立刻写入的：进出事件本来就有先后，延到下一帧反而可能把「进来又出去」记成一直悬停。*/
  private fun Element.trackShadowHover() {
    touchable = Touchable.enabled

    addListener(object : InputListener() {
      override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        focusHover(this@trackShadowHover)
      }

      override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?) {
        if (!(toActor?.isDescendantOf(this@trackShadowHover) ?: false)) clearHover(this@trackShadowHover)
      }
    })
  }

  fun getBound() = Rect(viewBound).also {
    it.y -= ioList.height
    it.height += ioList.height
  }

  fun layoutRecipeTabs(){
    CalculatorLayout.layoutNodes(layers, { nodeToElement[it] }, viewBound, layerCenter)

    //添加按钮是纯界面入口，不参与上面的宽度 / 位置计算：这里按排好的首层结果单独摆位。
    val button = addButton ?: return
    CalculatorLayout.layoutAddButton(layers, { nodeToElement[it] }, button)
  }

  fun layoutLinkLines(){
    CalculatorLayout.layoutLinks(layers, { nodeToElement[it] }, layerCenter, linkLines) { tab, linked, line ->
      tab.setupInputOverListener(line)
      linked.setupOutputOverListener(line)
    }
  }

  /**把影子节点连回真身的虚线整理成「一个真身一条主支」。
   *
   * 同一个真身的全部影子共用一条竖直**主支**：每个影子先用一条横向支线走到主支上，主支再单独接
   * 一条线回真身。这样同一真身内部不再有一堆互相平行的竖线；不同真身的主支之间按交叉数错开
   * （见 [assignShadowLanes]）。
   *
   * 这里只把几何算定（主支的横向位置、每条支线的坐标）；**画哪几条**是按当前悬停现算的，
   * 见 `drawShadowLines`。
   *
   * 与 [layoutLinkLines] 一样只在布局阶段算一次，绘制阶段直接照抄结果 —— 节点位置本来就是布局
   * 阶段定下来的。*/
  fun layoutShadowLines() {
    shadowRoutes.clear()
    shadowBranches.clear()
    if (shadowTabs.isEmpty) return

    val routes = LinkedHashMap<RecipeGraphLayout.RecNode, ShadowRoute>()

    shadowTabs.forEach { tab ->
      val shadow = tab.node
      val real = nodeToElement[shadow.shadowed] ?: return@forEach

      val route = routes.getOrPut(shadow.shadowed) { ShadowRoute(shadow.shadowed).also { shadowRoutes.add(it) } }

      val center = tab.centerOffset()
      val branch = ShadowBranch(tab)
      branch.route = route
      branch.x = center.x + tab.nodeX
      branch.y = center.y + tab.nodeY

      route.branches.add(branch)
      route.stubYs.add(branch.y)

      val realCenter = real.centerOffset()
      route.sourceX = realCenter.x + real.nodeX
      route.sourceY = realCenter.y + real.nodeY

      shadowBranches.put(tab, branch)
    }

    shadowRoutes.forEach { route ->
      route.topY = (route.stubYs + route.sourceY).min()
      route.bottomY = (route.stubYs + route.sourceY).max()
    }

    assignShadowLanes()

    shadowRoutes.forEach { route ->
      route.trunkX = viewBound.x - Scl.scl(CalculatorLayout.SHADOW_SIDE_OFFSET) -
        Scl.scl(CalculatorLayout.SHADOW_LANE_STEP)*route.lane
    }
  }

  /**给每条主支分配左侧偏移量 [ShadowRoute.lane]（0 最靠近图，越大越靠左）。
   *
   * 一条横向支线从影子走到自己的主支时会跨过全部**比它更靠近图**的主支；如果被跨过的那条主支的
   * 纵向区间正好盖住这条支线的 y，就算一次交叉。这里每一档都挑「当前交叉最少」的那条主支放上去，
   * 让最靠内的几条主支尽量不被跨过。同分时按影子表里的先后顺序取（顺序稳定）。
   *
   * 这是个贪心，不保证全局交叉数最少；主支数量很少，够用且结果稳定。*/
  private fun assignShadowLanes() {
    val remaining = shadowRoutes.toMutableList()
    val placed = mutableListOf<ShadowRoute>()
    var lane = 0

    while (remaining.isNotEmpty()) {
      var best: ShadowRoute? = null
      var bestCrossings = Int.MAX_VALUE

      remaining.forEach { route ->
        var crossings = 0
        route.stubYs.forEach { y ->
          placed.forEach { other ->
            if (y >= other.topY && y <= other.bottomY) crossings++
          }
        }

        if (crossings < bestCrossings) {
          bestCrossings = crossings
          best = route
        }
      }

      val chosen = best ?: break
      chosen.lane = lane++
      placed.add(chosen)
      remaining.remove(chosen)
    }
  }

  private fun setupGraphView() {
    graphView = object : Group() {
      private val tempList = Seq<CalculatorLayout.LinkLine>()

      override fun childrenChanged() {
        invalidate()
      }

      override fun layout() {
        val children = getChildren()

        children.forEach { it.validate() }

        layoutRecipeTabs()
        layoutLinkLines()
        layoutShadowLines()
      }

      override fun removeChild(actor: Element?, unfocus: Boolean): Boolean {
        val removed = super.removeChild(actor, unfocus)
        if (removed && actor is RecipeTab) removeRecipeTab(actor)
        return removed
      }

      override fun draw() {
        validate()
        drawLines()
        drawShadowLines()
        super.draw()
      }

      private fun drawLines() {
        Lines.stroke(Scl.scl(CalculatorLayout.LINE_STROKE))
        fun draw(line: CalculatorLayout.LinkLine, off: Float) {
          val from = line.from
          val to = line.to

          val cent = y + line.centerY - off

          val fromX = x + from.x - off
          val fromY = y + from.y - off
          val toX = x + to.x - off
          val toY = y + to.y - off

          Lines.line(
            fromX, fromY,
            fromX, cent
          )
          Lines.line(
            fromX, cent,
            toX, cent,
          )
          Lines.line(
            toX, cent,
            toX, toY
          )
        }

        tempList.clear()

        linkLines.forEach { line ->
          if (line.isOver) {
            tempList.add(line)
            return@forEach
          }
          Draw.color(Pal.darkestGray)
          draw(line, Scl.scl(CalculatorLayout.LINE_OUTLINE_OFFSET))
          Draw.color(Color.gray)
          draw(line, 0f)
        }

        Draw.color(Pal.accent)
        tempList.forEach { line ->
          draw(line, 0f)
        }

        Draw.color()
      }

      /**这一帧该画哪些影子连线。
       *
       * 默认只画断环替身：环边的替身是理解这张图的必需品，而用户自己收起来的那些线一旦全画出来
       * 就是一团乱麻。悬停真身时把它名下全部影子的线摊开；悬停某个影子时只画它那一条。*/
      private fun drawShadowLines() {
        val sig = Scl.scl(CalculatorLayout.SHADOW_DASH)
        val hoveredShadow = hoveringShadow
        val hoveredReal = hoveringRecipe

        /**画一段虚线。[x1] / [y1] / [x2] / [y2] 是相对本元素的坐标。*/
        fun dash(x1: Float, y1: Float, x2: Float, y2: Float) {
          val len = max(abs(x2 - x1), abs(y2 - y1))
          Lines.dashLine(x + x1, y + y1, x + x2, y + y2, (len/sig).roundToInt().coerceAtLeast(1))
        }

        fun isVisible(branch: ShadowBranch, route: ShadowRoute): Boolean = when {
          //断环替身一直画。
          branch.tab.kind == ShadowKind.LOOP -> true
          //悬停某个影子：只画它那一条。
          hoveredShadow != null -> hoveredShadow === branch.tab
          //悬停真身：把挂在它名下的影子全部画出来。
          hoveredReal != null -> nodeToElement[route.source] === hoveredReal
          else -> false
        }

        Lines.stroke(Scl.scl(CalculatorLayout.LINE_STROKE))
        Draw.color(Color.gray)

        shadowRoutes.forEach { route ->
          //主支的纵向区间只覆盖这一帧真正要画的支线，加上真身那一端 —— 藏起来的支线不会把主支撑长。
          var top = Float.POSITIVE_INFINITY
          var bottom = Float.NEGATIVE_INFINITY
          var any = false

          route.branches.forEach { branch ->
            if (!isVisible(branch, route)) return@forEach

            any = true
            if (branch.y < top) top = branch.y
            if (branch.y > bottom) bottom = branch.y

            dash(branch.x, branch.y, route.trunkX, branch.y)
          }

          if (!any) return@forEach

          if (route.sourceY < top) top = route.sourceY
          if (route.sourceY > bottom) bottom = route.sourceY

          dash(route.trunkX, top, route.trunkX, bottom)
          dash(route.trunkX, route.sourceY, route.sourceX, route.sourceY)
        }

        //当前悬停到的那一个影子：把它的那条线用强调色重画一遍。起止点与灰色那遍完全一样，
        //虚线相位因此对齐，不会出现重影。
        hoveredShadow?.also { shadow ->
          val branch = shadowBranches.get(shadow) ?: return@also
          val route = branch.route ?: return@also
          if (!isVisible(branch, route)) return@also

          Lines.stroke(Scl.scl(CalculatorLayout.LINE_STROKE))
          Draw.color(Pal.accent, Color.gray, Mathf.absin(10f, 1f))

          dash(branch.x, branch.y, route.trunkX, branch.y)
        }

        Draw.color()
      }
    }
  }

  fun build() {
    clear()

    setupGraphView()
    graphView.setBounds(0f, 0f, 0f, 0f)
    tabSelectors.setBounds(0f, 0f, 0f, 0f)

    setupIOList()

    container = object : Group() {
      override fun act(delta: Float) {
        super.act(delta)

        setPosition(panX + zoom.width/2f, panY + zoom.height/2f, Align.center)
      }

      override fun draw() {
        if (showGrid) {
          val a = if (imageGenerating) tmpGridAlpha else 1f
          Lines.stroke(Scl.scl(CalculatorLayout.GRID_STROKE), Pal.gray)
          Draw.alpha(parentAlpha*a)
          val gridSize = Scl.scl(Core.settings.getInt("tmi_gridSize", CalculatorLayout.GRID_SIZE_DEFAULT).toFloat())

          var offX = 0f
          while (offX <= (Core.scene.width)/zoom.scaleX - panX) {
            Lines.line(x + offX, -Core.scene.height/zoom.scaleY, x + offX, Core.scene.height/zoom.scaleY*2)
            offX += gridSize
          }
          offX = 0f
          while (offX >= -(Core.scene.width)/zoom.scaleX - panX) {
            Lines.line(x + offX, -Core.scene.height/zoom.scaleY, x + offX, Core.scene.height/zoom.scaleY*2)
            offX -= gridSize
          }

          var offY = 0f
          while (offY <= (Core.scene.height)/zoom.scaleY - panY) {
            Lines.line(-Core.scene.width/zoom.scaleX, y + offY, Core.scene.width/zoom.scaleX*2, y + offY)
            offY += gridSize
          }
          offY = 0f
          while (offY >= -(Core.scene.height)/zoom.scaleY - panY) {
            Lines.line(-Core.scene.width/zoom.scaleX, y + offY, Core.scene.width/zoom.scaleX*2, y + offY)
            offY -= gridSize
          }
        }
        super.draw()
      }
    }
    container.addChild(graphView)
    container.addChild(tabSelectors)
    container.addChild(ioList)

    fill { t ->
      zoom = t
      zoom.isTransform = true
    }
    zoom.addChild(container)

    graphUpdated()

    touchable = Touchable.enabled
    setZoomListener()
    setPanListener()

    addEventBlocker(true){ e -> browsMode && e is InputEvent && e.type == InputEvent.InputEventType.touchDown }

    addListener(object: ClickListener(KeyCode.mouseLeft){
      override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
        super.touchDragged(event, x, y, pointer)
        if (!cancelled && Mathf.dst(x, y, touchDownX, touchDownY) > 8) {
          cancel()
        }
      }

      override fun clicked(event: InputEvent?, x: Float, y: Float) {
        tabSelectors.hide()
      }
    })
  }

  private fun setupIOList() {
    ioList = Table()
    val ioList = ioList

    ioList.visibility = Boolp{ !graph.isEmpty() }
    ioList.update {
      ioList.setPosition(viewBound.x + viewBound.width/2f, viewBound.y, Align.top)
    }

    ioList.table { label ->
      label.add().size(32f)
      label.add(Core.bundle["dialog.calculator.statistic"]).color(Pal.accent)
        .padLeft(12f).padRight(12f).growX()
        .labelAlign(Align.center)
      label.button(Icon.listSmall, Styles.clearNonei, ::showStatistic).size(32f)
    }.pad(8f).padTop(26f).growX()
    ioList.row()
    ioList.image().color(Pal.accent).growX().height(4f).padBottom(8f)

    ioList.row()
    ioList.table { i -> status = i }.growX().fillY()

    ioList.row()
    ioList.table { info ->
      info.add(Core.bundle["dialog.calculator.statOutputs"])
      info.image(Icon.upload).size(32f).pad(8f)
      outputs = info.table().growX().fillY().pad(8f).get()

      info.row()
      info.add(Core.bundle["dialog.calculator.statInputs"])
      info.image(Icon.download).size(32f).pad(8f).padTop(16f)
      inputs = info.table().growX().fillY().pad(8f).get()
      info.row()
      optionalInputs = info.table().growX().fillY().pad(8f).colspan(3).get()
    }.pad(8f).growX().fillY()
  }

  fun showStatistic() {
    statisticDialog.show(statistic = statistic)
  }

  fun rebuildIO() {
    val formatter = AmountFormatter.timedAmountFormatter()

    status.clearChildren()
    if (!astringentValid) {
      status.table { s ->
        s.image(Icon.warning).color(Color.crimson).size(36f).pad(8f)
        s.add(Core.bundle["dialog.calculator.astringentFailed"])
      }
    }

    outputs.clearChildren()
    val out = statistic.resultOutputs()
    val red = statistic.resultRedundant()

    (out + red).groupBy { it.item }
      .map { (k, v) ->
        RecipeItemStack(k, v.sumOf{ s -> s.amount.toDouble() }.toFloat())
      }
      .filter { it.amount > 0.000001f }
      .forEachIndexed { i, stack ->
        if (i > 0 && i % 6 == 0) outputs.row()
        outputs.add(RecipeItemCell(CellType.PRODUCTION, stack).also {
          it.style = Styles.cleart
          it.setFormatter(formatter)
        }).size(56f).pad(8f).padLeft(12f).padRight(12f)
      }

    inputs.clearChildren()
    val nonOptional = statistic.resultInputs()
    val optional = statistic.resultOptionalInputs()
      nonOptional.forEachIndexed { i, stack ->
      if (i > 0 && i % 6 == 0) inputs.row()
      inputs.add(RecipeItemCell(CellType.MATERIAL, *stack.toTypedArray()).also {
        it.style = Styles.cleart
        it.setFormatter(formatter)
      }).size(56f).pad(8f).padLeft(12f).padRight(12f)
    }

    if (optional.isNotEmpty()) {
      optionalInputs.clearChildren()
      optionalInputs.table{ t ->
        t.add(Core.bundle["misc.optional"]).pad(8f)
        t.row()
        t.image().color(Pal.gray).growX().height(4f).padTop(8f).padBottom(8f)
        t.row()
        t.table { items ->
          optional.forEachIndexed { i, stack ->
            if (i > 0 && i%6 == 0) inputs.row()
            items.add(RecipeItemCell(CellType.MATERIAL, *stack.toTypedArray()).also {
              it.style = Styles.cleart
              it.setFormatter(formatter)
            }).size(56f).pad(8f).padLeft(12f).padRight(12f)
          }
        }
      }.fill()
    }

    ioList.validate()
    ioList.pack()
  }

  fun showRecipeSelector(
    item: RecipeItem<out Any?>,
    graphNode: RecipeGraphNode,
  ) {
    val existed = if (tabSelectors.selecting != item) tabSelectors.show(graphNode, item) else false

    if (!existed) {
      tabSelectors.hide()

      TmiUI.recipesDialog.showWith {
        setCurrSelecting(item, RecipesDialog.Mode.RECIPE, true)
        callbackRecipe(Icon.tree) { recipe ->
          val newNode = RecipeGraphNode(recipe)

          //加卡片、取消可选、接上新卡片属于同一次用户操作，作为整体提交与撤销。
          commitTransaction(CalculatorTransactions.Composite(
            this@CalculatorView,
            CalculatorTransactions.SetOptional(this@CalculatorView, graphNode, item, false),
            CalculatorTransactions.ConnectInput(this@CalculatorView, graphNode, item, newNode),
            CalculatorTransactions.AddRecipeCard(this@CalculatorView, newNode),
          ))

          hide()
        }
        showDoubleRecipe(true)
      }
    }
  }

  /**按当前的自动连接设置把 [target] 接进已有的图。
   *
   * 注意：这会直接修改图，只应由 [CalculatorTransactions.AddRecipeCard] 在事务内部调用，
   * 否则这部分改动无法被撤销。*/
  fun linkExisted(target: RecipeGraphNode) {
    if (autoLinkInput || autoLinkOutput) {
      val validConsume = mutableMapOf<RecipeItem<*>, RecipeGraphNode>()
      val validProduct = mutableMapOf<RecipeItem<*>, Seq<RecipeGraphNode>>()
      val recipe = target.recipe

      graph.forEach { node ->
        if (node == target) return@forEach

        val nodeRec = node.recipe

        if (autoLinkInput) {
          recipe.materials
            .filter { it.itemType != RecipeItemType.POWER && it.itemType != RecipeItemType.ATTRIBUTE }
            .filter { !it.isOptional || node.optionals.contains(it.item) }
            .forEach { mat ->
              if (nodeRec.containsProduction(mat.item) && !target.hasInput(mat.item)
              && (!mat.isOptional || target.optionals.contains(mat.item))) {
                validConsume[mat.item] = node
              }
            }
        }

        if (autoLinkOutput) {
          recipe.productions
            .filter { it.itemType != RecipeItemType.POWER }
            .forEach { prod ->
              val nodeMat = nodeRec.getMaterial(prod.item)
              if (nodeMat != null && !node.hasInput(prod.item)
              && (!nodeMat.isOptional || node.optionals.contains(prod.item))) {
                validProduct.computeIfAbsent(prod.item) { Seq() }.add(node)
              }
            }
        }
      }

      if (autoLinkInput) validConsume.forEach { (item, n) -> target.setInput(item, n) }
      if (autoLinkOutput) validProduct.forEach { (item, nodes) -> nodes.forEach { n -> target.setOutput(item, n) } }
    }
  }
  
  fun graphUpdated() {
    CalculatorLayout.beginBuild()

    try {
      buildGraph()
    }
    catch (e: RecipeGraph.MissingModException) {
      CalculatorLayout.endBuild()
      throw e
    }
    catch (e: Exception) {
      CalculatorLayout.endBuild()
      showGraphError(e)
      return
    }

    CalculatorLayout.endBuild()
  }

  private fun buildGraph() {
    recipeElements.clear()
    nodeToElement.clear()
    shadowTabs.clear()
    shadowRoutes.clear()
    shadowBranches.clear()
    hoveringShadow = null
    hoveringRecipe = null
    graphView.clearChildren()

    layers = RecipeGraphLayout.generateLayout(graph)

    layers.forEach { layer ->
      layer.filterIsInstance<RecipeGraphLayout.RecNode>().forEach { layoutNode ->
        //影子是真身之外的另一种卡片：它不可编辑，所以是独立类型，只有「点击聚焦真身」这一种交互。
        if (layoutNode is RecipeGraphLayout.ShadowNode) {
          val elem = ShadowTab(layoutNode, this)

          elem.trackShadowHover()
          elem.clicked { focusOn(layoutNode.shadowed) }
          addRecipeTab(elem)
        }
        else {
          //悬停真身时把它名下全部影子的连线摊开，见 drawShadowLines。
          val elem = RecipeTab(layoutNode, this)

          elem.trackShadowHover()
          addRecipeTab(elem)
        }
      }
    }

    layers.forEach { layer ->
      layer.filterIsInstance<RecipeGraphLayout.LineMark>().forEach mark@{ mark ->
        val origin = mark.getOriginNode()
        val target = mark.getTargetNode()
        val from = target?.let { nodeToElement[it] }
        val to = origin?.let { nodeToElement[it] }

        if (from == null || to == null) {
          CalculatorLayout.defect("Illegal graph linking (link in or out was null).")
          return@mark
        }

        addRecipeTab(LineMark(mark, from, to))
      }
    }

    //添加按钮不参与布局：它不进任何一层，首层的宽度与位置因此完全由真实节点决定。
    //它的位置在 layoutAddButton 里按首层结果单独贴出来。
    val add = AddRecipeButton(this)
    addButton = add
    addRecipeTab(add)

    balanceUpdated()
  }

  fun focusOn(node: RecipeGraphLayout.Node) {
    val elem = nodeToElement[node] ?: run {
      CalculatorLayout.warnOnce("No such layout node, skip.")
      return
    }

    panX = -(elem.nodeX + elem.nodeWidth/2f)
    panY = -(elem.nodeY + elem.nodeHeight/2f)
  }

  private fun showGraphError(e: Exception) {
    Log.err(e)

    recipeElements.clear()
    nodeToElement.clear()
    shadowTabs.clear()
    shadowRoutes.clear()
    shadowBranches.clear()
    hoveringShadow = null
    hoveringRecipe = null
    linkLines.clear()
    layerCenter.clear()
    layers = arrayOf()
    addButton = null
    viewBound.setSize(0f)
    if (this::graphView.isInitialized) graphView.clearChildren()

    clear()

    table { t ->
      t.left().defaults().left().padBottom(6f)
      t.add(Core.bundle["dialog.calculator.fileCorruption"]).fontScale(1.2f).color(Color.crimson)
      t.row()

      e.message?.also { msg ->
        t.add(msg).color(Color.lightGray).wrap()
        t.row()
      }

      t.button(Core.bundle["dialog.calculator.forceOpen"], Styles.defaultt) {
        CalculatorLayout.forceOpen()
        Core.app.post { build() }
      }
      t.row()

      t.pane { p -> p.add(e.stackTraceToString()) }.grow()
    }.grow().pad(12f)
  }

  fun balanceUpdated(){
    isUpdated = true

    graph.forEach {
      it.balanceAmount = -1f
    }
    recipeElements.filterIsInstance<RecipeTab>().forEach { it.setupEnvParameters(it.graphNode.inputTable) }

    var iterated = 0
    val nodeSet = linkedSetOf<RecipeGraphNode>()

    val rootNodes = HashSet<RecipeGraphNode>()
    layers.forEach { layer ->
      layer.forEach { node ->
        if (node is RecipeGraphLayout.RecNode && node !is RecipeGraphLayout.ShadowNode && node.isRoot) {
          rootNodes.add(node.targetNode)
        }
      }
    }

    nodeSet.addAll(
      layers.flatMap { it }
        .filterIsInstance<RecipeGraphLayout.RecNode>()
        .filter { it !is RecipeGraphLayout.ShadowNode }
        .map { it.targetNode }
    )

    while (nodeSet.isNotEmpty()) {
      astringentValid = true
      iterated++
      val nl = nodeSet.toList()
      nodeSet.clear()
      nl.forEach { node ->
        node.updateEfficiency()
        if (node.updateBalance(isRoot = rootNodes.contains(node))){
          node.visit(visitedSet = nodeSet) { _, _ -> }
        }
      }

      if (iterated >= 100) {
        astringentValid = false
        break
      }
    }

    statistic.reset()
    statistic.updateStatistic()
    graphView.act(0f) // update the balanced amount

    //两种卡片的文字都是缓存的，平衡数量一变就要重新刷一遍并重新量尺寸。
    recipeElements.forEach { element ->
      if (element is RecipeTab) element.refreshLayout()
      else if (element is ShadowTab) element.refreshLayout()
    }

    graphView.invalidate()

    rebuildIO()
  }

  private fun addRecipeTab(recipeTab: RecipeGraphElement){
    if (recipeTab is Element) graphView.addChild(recipeTab)
    if (recipeTab is ShadowTab) shadowTabs.add(recipeTab)
    recipeElements.add(recipeTab)
    nodeToElement.put(recipeTab.node, recipeTab)
  }

  private fun removeRecipeTab(recipeTab: RecipeGraphElement){
    recipeElements.remove(recipeTab)
    nodeToElement.remove(recipeTab.node)
    if (recipeTab is Element) graphView.removeChild(recipeTab)
    if (recipeTab is ShadowTab) shadowTabs.remove(recipeTab)
  }

  private fun clamp() {
    val pad = Scl.scl(40f)
    val bounds = viewBound

    val ox = width/2f
    val oy = height/2f
    var rx = bounds.x + panX + ox
    var ry = panY + oy + bounds.y
    val rw = bounds.width
    val rh = bounds.height
    rx = Mathf.clamp(rx, -rw + pad, width - pad)
    ry = Mathf.clamp(ry, -rh + pad, height - pad)
    panX = rx - bounds.x - ox
    panY = ry - bounds.y - oy
  }

  private fun setPanListener() {
    addCaptureListener(object : ElementGestureListener() {
      var panEnable: Boolean = false

      override fun zoom(event: InputEvent, initialDistance: Float, distance: Float) {
        zoom.setOrigin(Align.center)

        if (lastZoom < 0) {
          lastZoom = zoom.scaleX
        }

        zoom.setScale(Mathf.clamp(distance/initialDistance*lastZoom, 0.25f, 1f))

        clamp()
      }

      override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        zoom.setOrigin(Align.center)
        if (button != KeyCode.mouseLeft || pointer != 0) return
        panEnable = true
      }

      override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: KeyCode) {
        if (button != KeyCode.mouseLeft || pointer != 0) return
        lastZoom = zoom.scaleX
        panEnable = false
      }

      override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (!panEnable) return

        panX += deltaX/zoom.scaleX
        panY += deltaY/zoom.scaleY
        clamp()
      }
    })
  }

  private fun setZoomListener() {
    addCaptureListener(object : InputListener() {
      override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
        zoom.setOrigin(Align.center)
        zoom.setScale(Mathf.clamp(zoom.scaleX - amountY/10f*zoom.scaleX, 0.25f, 1f).also { lastZoom = it })

        clamp()
        return true
      }

      override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?) {
        requestScroll()
        super.enter(event, x, y, pointer, fromActor)
      }
    })
  }

  //IO
  fun save(file: Fi): Boolean {
    val writer = Writes(DataOutputStream(file.write(false)))

    try {
      graph.write(writer)

      isUpdated = false
    } finally {
      writer.close()
    }

    return true
  }

  fun load(file: Fi): Boolean {
    var reader: Reads? = null

    try {
      reader = Reads(DataInputStream(file.read()))
      val ver = reader.i()

      //读取文件会整体替换图，旧的历史事务不能再作用于新内容。
      clearHistory()

      graph.read(reader, ver)
      isUpdated = false

      graphUpdated()
    }
    catch (e: RecipeGraph.MissingModException) {
      throw e
    }
    catch (e: Exception) {
      showGraphError(e)
    }
    finally {
      isUpdated = false
      reader?.close()
    }

    return true
  }

  fun resetView() {
    panX = 0f
    panY = 0f
    zoom.scaleX = 1f
    zoom.scaleY = 1f
  }

  //== Transactions ==
  /**执行 [transaction]，把它记入历史表，并按事务声明的强度刷新视图。
   *
   * 这是唯一应该在界面上修改图的入口：直接改图而不走事务，会让撤销把改动一起回滚掉。
   * 一个事务实例只提交一次（它记录的是自己这一次操作的前后状态）。*/
  fun commitTransaction(transaction: ITransaction) {
    transaction.commit()

    //新操作会丢弃原有的重做分支。
    while (history.size > historyCursor) history.remove(history.size - 1)

    history.add(transaction)
    historyCursor = history.size

    while (history.size > historyLimit.coerceAtLeast(1)) {
      history.remove(0)
      historyCursor--
    }

    refreshFor(transaction)
  }

  /**是否还有可以撤销的事务。*/
  val canUndo: Boolean get() = historyCursor > 0

  /**是否还有可以重做的事务。*/
  val canRedo: Boolean get() = historyCursor < history.size

  /**撤销最近一次已提交的事务。*/
  fun undo() {
    if (!canUndo) return

    val transaction = history[--historyCursor]
    transaction.rollback()

    //撤销/重做后的模型不是由界面操作直接得到的，直接整图重建以保证界面与模型完全一致。
    graphUpdated()
  }

  /**重做最近一次被撤销的事务。*/
  fun redo() {
    if (!canRedo) return

    val transaction = history[historyCursor++]
    transaction.commit()

    graphUpdated()
  }

  /**清空历史表（例如新建 / 打开文件之后调用）。*/
  fun clearHistory() {
    history.clear()
    historyCursor = 0
  }

  private fun refreshFor(transaction: ITransaction) {
    val refresh = (transaction as? CalculatorTransactions.Transaction)?.refresh
      ?: CalculatorTransactions.Refresh.structure

    when (refresh) {
      CalculatorTransactions.Refresh.balance -> balanceUpdated()
      CalculatorTransactions.Refresh.structure -> graphUpdated()
    }
  }

  fun drawToBuffer(chunkedBuffer: ChunkedFrameBuffer, padding: Float, backAlpha: Float) {
    tmpGridAlpha = backAlpha
    val view = getBound()

    val par = container.parent
    val x = container.x
    val y = container.y
    val sclX = zoom.scaleX
    val sclY = zoom.scaleY
    val scW = Core.scene.viewport.worldWidth
    val scH = Core.scene.viewport.worldHeight

    imageGenerating = true
    container.forEach { it.act(0f) }

    val imageWidth = (view.width + padding*2)
    val imageHeight = (view.height + padding*2)

    val originX = view.x - padding
    val originY = view.y - padding

    Core.scene.viewport.worldWidth = imageWidth
    Core.scene.viewport.worldHeight = imageHeight

    chunkedBuffer.eachChunk { chunk ->
      val width = chunk.widthFactor*imageWidth
      val height = chunk.heightFactor*imageHeight
      val dx = originX + chunk.u1*imageWidth
      val dy = originY + chunk.v1*imageHeight

      val camera = Camera()
      camera.width = width
      camera.height = height
      camera.position.x = dx + width/2f
      camera.position.y = dy + height/2f
      camera.update()

      zoom.scaleX = 1f
      zoom.scaleY = 1f
      container.parent = null
      container.x = 0f
      container.y = 0f

      Draw.proj(camera)
      chunk.begin(Tmp.c1.set(Pal.darkerGray).a(backAlpha))
      container.draw()
      chunk.end()
      Draw.flush()
    }

    imageGenerating = false
    container.forEach { it.act(0f) }

    container.parent = par
    container.x = x
    container.y = y
    zoom.scaleX = sclX
    zoom.scaleY = sclY
    Core.scene.viewport.worldWidth = scW
    Core.scene.viewport.worldHeight = scH
  }

}
