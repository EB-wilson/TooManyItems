
import universe.util.reflect.Reflection.accessField

class Test{
  private val a = ""
}

val Test.a: String by accessField("a")

fun main(){

}
