package tmi.ui.designer

import arc.math.geom.Vec2
import arc.util.Time
import mindustry.content.SectorPresets.origin
import mindustry.graphics.Layer.end
import tmi.recipe.EnvParameter
import tmi.recipe.types.RecipeItem
import java.util.Collections.addAll

abstract class DesignerHandle(val ownerView: DesignerView) {
  val handleTime = Time.globalTime

  abstract fun handle()
  abstract fun quash()
}

abstract class ContinuousHandle(ownerView: DesignerView): DesignerHandle(ownerView){
  abstract fun sync()
  open fun post(){}
}

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
          }
        }

      for (linkIn in linkIns[index]) removed.linkerIns.find{ it.item == linkIn.key }
        ?.apply {
          linkIn.value.forEach { link ->
            link.linker.linkTo(this)
          }
        }
    }
  }
}

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

class FoldCardHandle(
  ownerView: DesignerView,
  private val card: Card,
  private val toPos: Vec2,
  private val fold: Boolean
): DesignerHandle(ownerView){
  private val originPos = Vec2(card.x, card.y)

  override fun handle() {
    if (fold) ownerView.foldCard(card)
    else ownerView.unfoldCard(card)

    card.setPosition(toPos.x, toPos.y)

    if (fold) ownerView.alignFoldCard(card)
  }

  override fun quash() {
    if (fold) ownerView.unfoldCard(card)
    else ownerView.foldCard(card)

    card.setPosition(originPos.x, originPos.y)

    if (!fold) ownerView.alignFoldCard(card)
  }
}

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
  }

  private fun doDelink(){
    from.deLink(to)
    if (to.links.isEmpty){
      to.parent.removeChild(to)
    }
  }
}

class SetLinkPresentHandle(
  ownerView: DesignerView,
  private val target: ItemLinker,
  private val to: Map<ItemLinker, Float>
): DesignerHandle(ownerView) {
  private val origin = target.links.associate { it.key to it.value.rate }

  override fun handle() {
    to.forEach { target.setProportion(it.key, it.value)  }
  }

  override fun quash() {
    origin.forEach { target.setProportion(it.key, it.value) }
  }
}

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

class SetRecipeArgsHandle(
  ownerView: DesignerView,
  private val target: RecipeCard
): TimedHandle(ownerView, 60f) {
  private val originEffScl = target.effScale
  private val originEnvArgs = target.environments.copy()
  private val originOpts = target.optionalSelected.toList()

  var effScale: Float = originEffScl

  val envArgs: EnvParameter = originEnvArgs.copy()
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
    target.rebuildOptionals()
  }
}
