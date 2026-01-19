package tmi.util

import arc.func.Prov
import arc.scene.Element
import arc.scene.event.InputEvent
import arc.scene.event.InputListener
import arc.util.pooling.Pools
import kotlin.jvm.java
import kotlin.let

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