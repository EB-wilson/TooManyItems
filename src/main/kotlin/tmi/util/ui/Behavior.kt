package tmi.util.ui

import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.javaSetter

fun Behavior(block: Behavior.BehaviorBuilder.() -> Unit): Behavior {
  val builder = Behavior.BehaviorBuilder()
  builder.block()
  return builder.build()
}

class Behavior private constructor(
  private val observeVars: List<ObservableVar<*>>
){
  internal lateinit var targetElement: ElementBuilder<*>

  companion object {
    fun none() = BehaviorBuilder().build()
  }

  fun observedMuted() {
    targetElement.doRebuild()
  }

  fun setupObservers(){
    observeVars.forEach{ obs ->
      obs.observed(this)
    }
  }

  fun free(){
    observeVars.forEach{ obs ->
      obs.unobserved(this)
    }
  }

  var var1 by observed(1)

  class BehaviorBuilder {
    private val observedVars = mutableListOf<ObservableVar<*>>()

    fun observe(vararg properties: KProperty<*>) = also {
      properties.forEach { property ->
        property.javaField?.isAccessible = true
        property.javaGetter?.isAccessible = true
        if (property is KMutableProperty) property.javaSetter?.isAccessible = true

        if (property is KProperty0){
          val observed = property.getDelegate()
          if (observed is ObservableVar<*>) {
            observedVars.add(observed)
          }
          else throw IllegalArgumentException("Telegate target ${property.name} is not a ObservableVar")
        }
        else throw IllegalArgumentException("Unsupported handle that observe a extend property, property: $property")
      }
    }


    fun build(): Behavior = Behavior(
      observeVars = observedVars.toList()
    )
  }
}