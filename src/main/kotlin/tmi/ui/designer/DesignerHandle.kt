package tmi.ui.designer

import arc.math.geom.Vec2
import arc.util.Time
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils.IO
import tmi.recipe.EnvParameter
import tmi.recipe.types.RecipeItem

abstract class DesignerHandle(val ownerView: DesignerView) {
  val handleTime = Time.globalTime

  abstract fun handle()
  abstract fun quash()
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
  private val beginPos: List<Vec2>
): DesignerHandle(ownerView){
  private val alignOffset: Array<Vec2> = Array(movedCards.size){ Vec2() }

  var moveX = 0f
  var moveY = 0f

  override fun handle() {
    movedCards.forEachIndexed { index, card ->
      beginPos[index].also { origin ->
        alignOffset[index].also { off ->
          card.setPosition(origin.x + moveX + off.x, origin.y + moveY + off.y)
        }
      }
    }
  }

  override fun quash() {
    movedCards.forEachIndexed { index, card ->
      beginPos[index].apply { card.setPosition(x, y) }
    }
  }

  fun computeAlign() {
    movedCards.forEachIndexed { index, card ->
      alignOffset[index].set(card.x, card.y).sub(beginPos[index]).sub(moveX, moveY)
    }
  }
}

class RemoveCardHandle(
  ownerView: DesignerView,
  private val removedCards: List<Card>
): DesignerHandle(ownerView){
  data class Link(
    val card: Card,
    val item: RecipeItem<*>,
    val linker: ItemLinker
  )

  private val linkOuts = removedCards.map { removed ->
    removed.linkerOuts.map {
      it.item to it.links.map { link ->
        Link(link.key.parent as Card, it.item, link.key)
      }
    }.toMap()
  }
  private val linkIns = removedCards.map { removed ->
    removed.linkerIns.map {
      it.item to it.links.map { link ->
        Link(link.key.parent as Card, it.item, link.key)
      }
    }.toMap()
  }

  override fun handle() {
    removedCards.forEach { ownerView.removeCard(it) }
  }

  override fun quash() {
    removedCards.forEach { ownerView.addCard(it) }
    removedCards.forEachIndexed { index, removed ->
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
  private val origin = target.links.copy()

  override fun handle() {
    to.forEach { target.setPresent(it.key, it.value)  }
  }

  override fun quash() {
    origin.forEach { target.setPresent(it.key, it.value) }
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

  var lock: Boolean = false

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

class SetIOHandle(
  ownerView: DesignerView,
  private val card: IOCard
): TimedHandle(ownerView, 60f) {
  private val origin = card.stack.amount

  var setTo = origin

  override fun handle() {
    card.stack.amount = setTo
    card.observeUpdate()
  }

  override fun quash() {
    super.quash()

    card.stack.amount = origin
    card.observeUpdate()
  }
}
