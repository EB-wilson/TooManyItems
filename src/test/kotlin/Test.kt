class Test {
  private var n: Int? = 10

  fun foo1(a: Int){
    println(n?:10)
    println(a + n!!)
  }
}