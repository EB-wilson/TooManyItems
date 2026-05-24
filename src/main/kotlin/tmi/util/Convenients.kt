package tmi.util

import arc.func.Prov
import arc.scene.Element
import arc.scene.Group
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.scene.event.Touchable
import arc.scene.ui.layout.Table
import arc.util.pooling.Pools

inline fun <reified T> poolObtain(block: Prov<T>): T = Pools.obtain(T::class.java, block)

fun Element.enterSt(block: Runnable){
  this.addListener(object: InputListener(){
    override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?) {
      super.enter(event, x, y, pointer, fromActor)

      if (fromActor?.isDescendantOf(this@enterSt)?:true) return

      block.run()
    }
  })
}

fun Element.exitSt(block: Runnable){
  this.addListener(object: InputListener(){
    override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?) {
      super.exit(event, x, y, pointer, toActor)

      if (!let { toActor?.isDescendantOf(this@exitSt) ?: true }) {
        block.run()
      }
    }
  })
}

fun Group.fillRect(rect: Table.DrawRect): Element {
  val e = object : Element() {
    override fun draw() {
      rect.draw(x, y, width, height)
    }
  }
  e.setFillParent(true)

  e.touchable = Touchable.disabled
  addChild(e)
  return e
}
