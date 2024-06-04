import java.io.File
import kotlin.math.min

val docs = File("./")
  .listFiles()!!
  .filter { it.isDirectory() }

fun main() {
  docs.forEach { dir ->
    File(dir, "index.txt")
      .takeIf { it.exists() }
      ?.apply {
        val tree = readIndex(this)
        val files = dir.listFiles()!!
        tree.children.forEachIndexed { index, node ->
          val file = files.find { it.nameWithoutExtension.run {
            replace(node.name, "").length == indexOf(node.name)
          } }

          file?.apply {
            file.renameTo(File(file.parentFile, "$index.$node.${file.extension}"))
          }
        }
      }
  }
}


data class TreeNode(val name: String){
  val children = mutableListOf<TreeNode>()

  fun findChild(name: String) = children.find { it.name == name }
}

fun readIndex(index: File): TreeNode {
  if (!index.exists()) return TreeNode("root")
  val lines = index.readLines().filter { it.isNotBlank() }

  val root = TreeNode("root")
  parseLines(lines, root, 0)

  return root
}

fun parseLines(lines: List<String>, parent: TreeNode, padding: Int){
  lines.forEachIndexed { index, line ->
    if (!line.startsWith(" ".repeat(padding)) || line[padding] == ' ') return@forEachIndexed

    val name = line.substring(padding)
    val node = TreeNode(name)

    val sub = lines.subList(min(index + 1, lines.size - 1), lines.size)
    val end = sub.indexOfFirst { it.indexOf(it.trimStart()) <= padding }
    if (end > 0){
      val next = sub.first()
      parseLines(sub.subList(0, end), node, next.indexOf(next.trimStart()))
    }

    parent.children.add(node)
  }
}
