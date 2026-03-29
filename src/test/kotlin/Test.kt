import universe.util.reflect.Enums.accessEnum0

enum class Test{
  A, B, C
}


fun main(){
  val enumHandler = Test::class.accessEnum0()
  enumHandler.appendEnumInstance("D")
  enumHandler.newEnumInstance("E", 2)

  Test.entries.forEach { println(it.name) }
}
