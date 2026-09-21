# 基于Arc UI的响应式UI框架

基本样式

```kotlin
makeCompose() {
  // mutableState是一个包装为可kotlin委托的变量容器，remember用于向当前的布局上下文添加受观察的变量
  // 当受观察的状态发生变化时，需要重建此层级的对应上下文
  var var1 by remember { mutableStateOf(0f) }
  
  withStyle(Styles.style){
    column( //创建一个纵列布局，子元素纵向排布
      box = Box {
        padding(4f)
        margin(4f)
        align(Align.left)
        growX()
      },
      modifier = Modifier {
        background(Tex.back)
        fill()
      },
    ){
      Label("")
    }
  }
}
```