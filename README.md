![Generic badge](https://img.shields.io/badge/maturity-experimental-red.svg)
![Generic badge](https://img.shields.io/badge/Kotlin-1.6-blue.svg)
![Generic badge](https://img.shields.io/badge/JVM-8-blue.svg)


# [later](https://github.com/rtmigo/later_kt)

A `Later` represents a potential value, that will be available at some
time in the future.

It can only already contain a value, or not yet contain one.

- `later.isComplete` will return `true`, if we have the value

- `later.value` will return a value, or throw if we don't have it yet

- `later.await()` will block and wait until the value is assigned 

The object is somewhat similar to
[Deferred](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-deferred/)
in Kotlin, [Future](https://api.dart.dev/be/175791/dart-async/Future-class.html)
in Dart,
[Promise](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Promise)
in JS. But unlike them, `Later` is extremely simple: it does not mess with
coroutines, threads, task queues ot centralized loops. `Later` just runs
synchronous callback functions (but in thread-safe manner:).


# Install

#### settings.gradle.kts

```kotlin
sourceControl {
    gitRepository(java.net.URI("https://github.com/rtmigo/later_kt.git")) {
        producesModule("io.github.rtmigo:later")
    }
}
```

#### build.gradle.kts

```kotlin
dependencies {
    implementation("io.github.rtmigo:later") {
        version { branch = "staging" }
    }
}
```

# Basic example

```kotlin
import io.github.rtmigo.later.*

fun slowComputation(x: Int): Later<Int> {
    val result = mutableLater<Int>()
    thread {
        // one second later assign value to the returned object
        sleep(1000)
        result.value = x * 2   
    }
    return result  // return immediately
}

fun main() {
    // print "16" after one second
    println(slowComputation(8).await()) 
}
```

# Async callbacks

```kotlin
// init
val a = mutableLater<String>()
a.onValue { println("What is $it?!") }

// assigning the value will trigger the callback
a.value = "love"

// What is love?!
```


We also can set multiple callbacks for the same `Later`.

```kotlin
val a = mutableLater<String>()
a.onValue { println("What is $it?!") }
a.onValue { println("Is $it great?!") }
a.value = "Britain"

// What is Britain?
// Is Britain great? 
```



We don't actually need a meaningful value, when all we need is calling a callback.

```kotlin
val kindaEvent = mutableLater<Unit>()
kindaEvent.onValue { println("Kinda callback") }

kindaEvent.value = Unit

// Kinda callback
```

# Mapping

Without having a calculated value ready, we can specify future transformations
for this value.

```kotlin
val a = mutableLater<Int>()                         // a is MutableLater<Int>
val b = a.map { "The number is $it".asLater() }     // b is Later<String>
val c = b.map { it.uppercase()+"!" }                // c is Later<String>

// None of the objects have a value yet. Attempting to read `.value` 
// will throw an exception. But we can assign value to `a`:

a.value = 5

println(a.value)  // 5
println(b.value)  // The number is 5
println(c.value)  // THE NUMBER IS 5!
```