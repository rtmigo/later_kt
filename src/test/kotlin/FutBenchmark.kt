//package utils.syncfuture
//
//
////import kotlinx.coroutines.*
//import org.junit.jupiter.api.*
//import utils.*
//import kotlin.system.measureTimeMillis
//
//@Disabled
//class FutBenchmark {
//    val N = 20000000
//    // тяп-ляп сравнение корутин и Fut
//    @Test
//    fun benchmarkFut() {
//        fun returnFut(): Later<Int> = later(3)
//
//        var sum = 0
//
//        val time= measureTimeMillis {
//            repeat(N) {
//                sum+=returnFut().map { later(it+4) }.map { later(it*2) }.value
//            }
//        }
//
//        println("Time $time")
//        println("Sum $sum")
//    }
//
//    @Test
//    fun benchmarkCoroutines() {
//        suspend fun returnInitial(scope: CoroutineScope): Int {
//            return withContext(scope.coroutineContext) {
//                3
//            }
//
////            return withContext(scope.coroutineContext) {
////                3
////            }
////            async {
////
////            }
////            return Deferred<Int>()
//
//        }
//        suspend fun thenOne(scope: CoroutineScope) = returnInitial(scope)+4
//        suspend fun thenTwo(scope: CoroutineScope) = thenOne(scope)*2
//
//        runBlocking {
//            var sum = 0
//            val time = measureTimeMillis {
//
//                repeat(N) {
//                    sum+=thenTwo(this) // thenOne(returnInitial(this)))
//                }
//            }
//
//            println("Time $time")
//            println("Sum $sum")
//
//        }
//
//    }
//
//
//}