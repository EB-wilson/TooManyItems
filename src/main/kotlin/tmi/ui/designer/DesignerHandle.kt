package tmi.ui.designer

import arc.graphics.Color
import arc.math.geom.Vec2
import arc.scene.style.Drawable
import arc.util.Align
import arc.util.Time
import tmi.recipe.InputTable
import tmi.recipe.types.RecipeItem
import tmi.util.vec1

@Deprecated("Use recipe calculator")
abstract class DesignerHandle(val ownerView: DesignerView) {
  val handleTime = Time.globalTime

  abstract fun handle()
  abstract fun quash()
}

@Deprecated("Use recipe calculator")
abstract class ContinuousHandle(ownerView: DesignerView): DesignerHandle(ownerView){
  abstract fun sync()
  open fun post(){}
}

@Deprecated("Use recipe calculator")
abstract class TimedHandle(
  ownerView: DesignerView,
  private val maxInterval: Float
): DesignerHandle(ownerView) {
  private var timer = Time.globalTime

  val isExpired: Boolean
    get() = Time.globalTime - timer > maxInterval

  fun updateTimer(): Boolean {
    if (isExpired) return false

    timer = Time.globalTime
    return true
  }

  override fun quash() {
    timer = -maxInterval
  }
}

@Deprecated("Use recipe calculator")
class CombinedHandles(
  ownerView: DesignerView,
  private vararg val handles: DesignerHandle
): DesignerHandle(ownerView) {
  override fun handle() {
    handles.forEach { it.handle() }
  }

  override fun quash() {
    handles.reversed().forEach { it.quash() }
  }
}

@Deprecated("Use recipe calculator")
class CardSizeAlignHandle(
  ownerView: DesignerView,
  private val isAlign: Boolean,
  private val cards: List<Card>
): DesignerHandle(ownerView) {
  override fun handle() {
    doAlign(isAlign)
  }

  override fun quash() {
    doAlign(!isAlign)
  }

  private fun doAlign(align: Boolean){
    for (card in cards) {
      card.adjustSize(align)
    }
  }
}

@Deprecated("Use recipe calculator")
class AddCardHandle(
  ownerView: DesignerView,
  private val addedCard: Card
): DesignerHandle(ownerView){
  override fun handle() {
    ownerView.addCard(addedCard)
  }

  override fun quash() {
    ownerView.removeCard(addedCard)
  }
}

@Deprecated("Use recipe calculator")
class MoveCardHandle(
  ownerView: DesignerView,
  private val movedCards: List<Card>,
): ContinuousHandle(ownerView){
  private val beginPos = movedCards.map { Vec2(it.x, it.y) }
  private val toPos = beginPos.map { Vec2() }

  var moveX = 0f
  var moveY = 0f

  override fun handle() {
    movedCards.forEachIndexed { index, card ->
      toPos[index].also {
        card.setPosition(it.x, it.y)
        if (card.isFold) ownerView.alignFoldCard(card)
      }
    }
  }

  override fun quash() {
    movedCards.forEachIndexed { index, card ->
      beginPos[index].also {
        card.setPosition(it.x, it.y)
        if (card.isFold) ownerView.alignFoldCard(card)
      }
    }
  }

  override fun sync() {
    movedCards.forEachIndexed { index, card ->
      beginPos[index].also { origin ->
        card.setPosition(origin.x + moveX, origin.y + moveY)
      }
    }
  }

  override fun post() {
    movedCards.forEachIndexed { index, card ->
      if (card.isFold) ownerView.alignFoldCard(card)
      toPos[index].set(card.x, card.y)
    }
  }
}

@Deprecated("Use recipe calculator")
class IOCardItemHandle(
  ownerView: DesignerView,
  private val card: IOCard,
  private val item: RecipeItem<*>,
  private val removing: Boolean
): DesignerHandle(ownerView){
  override fun handle() {
    if (removing) card.removeItem(item)
    else card.addItem(item)
  }

  override fun quash() {
    if (removing) card.addItem(item)
    else card.removeItem(item)
  }
}

@Deprecated("Use recipe calculator")
abstract class CardsHandle(
  ownerView: DesignerView,
  private val handleCards: List<Card>
): DesignerHandle(ownerView){
  data class Link(
    val card: Card,
    val item: RecipeItem<*>,
    val linker: ItemLinker
  )

  private val linkOuts = handleCards.map { removed ->
    removed.linkerOuts.associate {
      it.item to it.links.map { link ->
        Link(link.key.parent as Card, it.item, link.key)
      }
    }
  }
  private val linkIns = handleCards.map { removed ->
    removed.linkerIns.associate {
      it.item to it.links.map { link ->
        Link(link.key.parent as Card, it.item, link.key)
      }
    }
  }

  fun removeAll() {
    handleCards.forEach { ownerView.removeCard(it) }
  }

  fun addAll() {
    handleCards.forEach { ownerView.addCard(it) }
    handleCards.forEachIndexed { index, removed ->
      for (linkOut in linkOuts[index]) removed.linkerOuts.find { it.item == linkOut.key }
        ?.apply {
          linkOut.value.forEach { link ->
            val target = link.card.linkerIns.find { it.item == link.item }
              ?:link.linker.also{ link.card.addIn(it) }

            linkTo(target)
            target.averange()
          }
        }

      for (linkIn in linkIns[index]) removed.linkerIns.find{ it.item == linkIn.key }
        ?.apply {
          linkIn.value.forEach { link ->
            link.linker.linkTo(this)
          }
          averange()
        }
    }
  }
}

@Deprecated("Use recipe calculator")
class RemoveCardHandle(
  ownerView: DesignerView,
  removeCards: List<Card>
): CardsHandle(ownerView, removeCards){
  override fun handle() {
    removeAll()
  }

  override fun quash() {
    addAll()
  }
}

@Deprecated("Use recipe calculator")
class AddCardsHandle(
  ownerView: DesignerView,
  addCards: List<Card>
): CardsHandle(ownerView, addCards){
  override fun handle() {
    addAll()
  }

  override fun quash() {
    removeAll()
  }
}

@Deprecated("Use recipe calculator")
class FoldCardHandle(
  ownerView: DesignerView,
  private val cards: List<Card>,
  private val fold: Boolean,
  private val move: Vec2 = Vec2.ZERO,
): DesignerHandle(ownerView){
  private val originPos = cards.associateWith { c ->
    c.localToAscendantCoordinates(ownerView, Vec2(c.width/2f, c.height/2f))
  }
  private val toPos = cards.associateWith { c ->
    c.localToAscendantCoordinates(ownerView, Vec2(c.width/2f, c.height/2f ).add(move))
  }

  override fun handle() {
    if (fold) cards.filter { !it.isFold }.forEach { ownerView.foldCard(it) }
    else cards.filter { it.isFold }.forEach { ownerView.unfoldCard(it) }

    cards.forEach { card ->
      val vec = vec1.set(toPos[card]!!)
      if (fold) ownerView.localToDescendantCoordinates(ownerView.foldPane, vec)
      else ownerView.localToDescendantCoordinates(ownerView.container, vec)

      card.setPosition(vec.x, vec.y, Align.center)
      if (fold) ownerView.alignFoldCard(card)
      else card.gridAlign(ownerView.cardAlign)
    }
  }

  override fun quash() {
    if (fold) cards.filter { it.isFold }.forEach { ownerView.unfoldCard(it) }
    else cards.filter { !it.isFold }.forEach { ownerView.foldCard(it) }

    cards.forEach { card ->
      val vec = vec1.set(originPos[card]!!)
      if (fold) ownerView.localToDescendantCoordinates(ownerView.container, vec)
      else ownerView.localToDescendantCoordinates(ownerView.foldPane, vec)

      card.setPosition(vec.x, vec.y, Align.center)
      if (fold) card.gridAlign(ownerView.cardAlign)
      else ownerView.alignFoldCard(card)
    }
  }
}

@Deprecated("Use recipe calculator")
class AddOutputLinkerHandle(
  ownerView: DesignerView,
  private val addedLinker: ItemLinker,
  private val target: Card
): DesignerHandle(ownerView){
  override fun handle() {
    target.addOut(addedLinker)
  }

  override fun quash() {
    addedLinker.remove()
  }
}

@Deprecated("Use recipe calculator")
class RemoveLinkerHandle(
  ownerView: DesignerView,
  private val linker: ItemLinker
): DesignerHandle(ownerView){
  private val ownerCard = linker.parentCard
  private val links = linker.links.associate { it.key to it.value.copy() }

  override fun handle() {
    linker.links.copy().forEach { linker.deLink(it.key) }
    linker.remove()
  }

  override fun quash() {
    if (linker.isInput) ownerCard.addIn(linker)
    else ownerCard.addOut(linker)

    links.forEach {
      linker.linkTo(it.key)
      linker.setProportion(it.key, it.value.rate)
    }
  }
}

@Deprecated("Use recipe calculator")
class MoveLinkerHandle(
  ownerView: DesignerView,
  private val movedLinker: ItemLinker
): DesignerHandle(ownerView) {
  private val beginX = movedLinker.x
  private val beginY = movedLinker.y
  private val beginDir = movedLinker.dir

  var endX = beginX
  var endY = beginY
  var endDir = beginDir

  override fun handle() {
    movedLinker.setPosition(endX, endY)
    movedLinker.dir = endDir
  }

  override fun quash() {
    movedLinker.setPosition(beginX, beginY)
    movedLinker.dir = beginDir
  }
}

@Deprecated("Use recipe calculator")
class DoLinkHandle(
  ownerView: DesignerView,
  private val from: ItemLinker,
  private val to: ItemLinker,
  private val delink: Boolean
): DesignerHandle(ownerView) {
  private val targetPar = to.parent as Card

  override fun handle() {
    if (delink) doDelink() else doLink()
  }

  override fun quash() {
    if (delink) doLink() else doDelink()
  }

  private fun doLink(){
    if (to.parent == null){
      targetPar.addIn(to)
    }
    from.linkTo(to)
    to.averange()
  }

  private fun doDelink(){
    from.deLink(to)
    if (to.links.isEmpty){
      to.parent.removeChild(to)
    }
    else to.averange()
  }
}

@Deprecated("Use recipe calculator")
class SetLinkPresentHandle(
  ownerView: DesignerView,
  private val target: ItemLinker,
  private val to: Map<ItemLinker, Float>
): DesignerHandle(ownerView) {
  private val origin = target.links.associate { it.key to it.value.rate }

  override fun handle() {
    to.forEach {
      target.setProportion(it.key, it.value)
      target.parentCard.observeUpdate()
    }
  }

  override fun quash() {
    origin.forEach {
      target.setProportion(it.key, it.value)
      target.parentCard.observeUpdate()
    }
  }
}

@Deprecated("Use recipe calculator")
class StandardizeHandle(
  ownerView: DesignerView,
  private val moveX: Float,
  private val moveY: Float,
): DesignerHandle(ownerView) {
  override fun handle() {
    ownerView.cards.forEach { it.moveBy(moveX, moveY) }
  }

  override fun quash() {
    ownerView.cards.forEach { it.moveBy(-moveX, -moveY) }
  }
}

@Deprecated("Use recipe calculator")
class SetRecipeArgsHandle(
  ownerView: DesignerView,
  private val target: RecipeCard
): TimedHandle(ownerView, 60f) {
  private val originEffScl = target.effScale
  private val originEnvArgs = target.environments.copy()
  private val originOpts = target.optionalSelected.toList()

  var effScale: Float = originEffScl

  val envArgs: InputTable = originEnvArgs.copy()
  val optionals: MutableSet<RecipeItem<*>?> = originOpts.toMutableSet()

  override fun handle() {
    target.effScale = effScale
    target.environments.set(envArgs, true)
    target.optionalSelected.apply {
      clear()
      addAll(*optionals.toTypedArray())
    }

    target.calculateEfficiency()
    target.observeUpdate()
    target.rebuildAttrs()
    target.rebuildSimAttrs()
    target.rebuildOptionals()
  }

  override fun quash() {
    super.quash()

    target.effScale = originEffScl
    target.environments.set(originEnvArgs, true)
    target.optionalSelected.apply {
      clear()
      addAll(*originOpts.toTypedArray())
    }

    target.calculateEfficiency()
    target.observeUpdate()
    target.rebuildAttrs()
    target.rebuildSimAttrs()
    target.rebuildOptionals()
  }
}

@Deprecated("Use recipe calculator")
class SetFoldIconInfHandle(
  ownerView: DesignerView,
  private val card: Card,
) : ContinuousHandle(ownerView) {
  var setIcon: Boolean = false
  var foldIcon: Drawable? = null
  val foldColor: Color = Color(card.foldColor)
  val iconColor: Color = Color(card.iconColor)
  val backColor: Color = Color(card.backColor)

  private val origIcon = card.foldIcon
  private val origFoldColor = Color(card.foldColor)
  private val origIconColor = Color(card.iconColor)
  private val origBackColor = Color(card.backColor)
  override fun sync() {
    handle()
  }

  override fun handle() {
    if (setIcon) card.foldIcon = foldIcon
    else {
      card.foldColor.set(foldColor)
      card.iconColor.set(iconColor)
      card.backColor.set(backColor)
    }
  }

  override fun quash() {
    if (setIcon) card.foldIcon = origIcon
    else {
      card.foldColor.set(origFoldColor)
      card.iconColor.set(origIconColor)
      card.backColor.set(origBackColor)
    }
  }
}

@Deprecated("Use recipe calculator")
class SwitchSimplifiedHandle(
  ownerView: DesignerView,
  private val cards: List<Card>,
  private val toSimplified: Boolean
): DesignerHandle(ownerView){
  override fun handle() {
    cards.forEach {
      it.switchSimplified(toSimplified)
    }
  }

  override fun quash() {
    cards.forEach {
      it.switchSimplified(!toSimplified)
    }
  }
}

@Deprecated("Use recipe calculator")
class SetGlobalIOHandle(
  ownerView: DesignerView,
  private val item: RecipeItem<*>,
  private val isInput: Boolean,
  private val removing: Boolean
): DesignerHandle(ownerView){
  override fun handle() {
    val set = (if (isInput) ownerView.globalInput else ownerView.globalOutput)

    if (removing) set.remove(item)
    else set.add(item)

    ownerView.statistic()
  }

  override fun quash() {
    val set = (if (isInput) ownerView.globalInput else ownerView.globalOutput)

    if (removing) set.add(item)
    else set.remove(item)

    ownerView.statistic()
  }
}
