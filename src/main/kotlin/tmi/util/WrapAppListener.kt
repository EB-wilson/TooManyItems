package tmi.util

import arc.ApplicationListener
import arc.files.Fi
import arc.func.Cons

open class WrapAppListener(
  protected val origin: ApplicationListener,
  private var proxy: Boolean = false,
  private val reset: Cons<ApplicationListener>,
): ApplicationListener {
  fun reset(){
    reset.get(origin)
  }

  override fun init() {
    if (proxy) origin.init()
  }

  override fun resize(width: Int, height: Int) {
    if (proxy) origin.resize(width, height)
  }

  override fun update() {
    if (proxy) origin.update()
  }

  override fun pause() {
    if (proxy) origin.pause()
  }

  override fun resume() {
    if (proxy) origin.resume()
  }

  override fun dispose() {
    if (proxy) origin.dispose()
  }

  override fun exit() {
    if (proxy) origin.exit()
  }

  override fun fileDropped(file: Fi?) {
    if (proxy) origin.fileDropped(file)
  }
}