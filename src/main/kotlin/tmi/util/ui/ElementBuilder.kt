package tmi.util.ui

import arc.scene.Element

abstract class ElementBuilder<E: Element>(
  val modifier: Modifier = Modifier.none(),
  val behavior: Behavior = Behavior.none(),
) {
  init {
    modifier.targetElement = this
    behavior.targetElement = this
  }

  private var _element: E? = null

  val element: E get() = if (_element == null) buildElement().also {
    _element = it
    behavior.setupObservers()
  } else _element!!

  fun doRebuild() {

  }

  protected abstract fun buildElement(): E
}