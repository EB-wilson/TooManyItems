import tmi.util.ui.observed
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter

class A {

}

class C {
  var A.obs by observed(1)

  fun foo(){
    val a = A()
    println(a.obs)
  }
}

fun main(){
  C().foo()
}
