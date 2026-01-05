package tmi.ui.calculator

class RecipeContext(
  val tree: RecipeGraph
) {
  init {
    update()
  }

  fun update() {
    tree
  }
}