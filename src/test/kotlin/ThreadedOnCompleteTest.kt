/**
 * SPDX-FileCopyrightText: (c) 2022 Artyom IG <github.com/rtmigo>
 * SPDX-License-Identifier: MIT
 **/

import io.github.rtmigo.later.*
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.*
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.random.Random

@OptIn(Experimental::class)
@Tag("slow")
@Timeout(2)
class ThreadedOnCompleteTest {
    @RepeatedTest(1000)
    fun `onComplete called exactly once in each thread with additional delays`() {
        withLaterTestingDelays {
            threadedOnComplete()
        }
    }

    @RepeatedTest(1000)
    fun `onComplete called exactly once in each thread`() {
        threadedOnComplete()
    }

    private fun threadedOnComplete() {
        val futureA: CompletableLater<Int> = Later.completable()
        val futureB: Later<Int> = futureA.map { Later.value(it + 1) }
        val futureC: Later<Int> = futureB.map { Later.value(it * 2) }

        val maxMs = 7L
        val threadsNum = (1..31).random()

        val waitingThreads = (1..threadsNum).map {
            var calls = 0
            fun handler(x: Int) {
                calls++
            }

            val t = thread {
                sleep(Random.nextLong(maxMs))
                when ((1..3).random()) {
                    1 -> futureA.onSuccess(::handler)
                    2 -> futureB.onSuccess(::handler)
                    3 -> futureC.onSuccess(::handler)
                    else -> throw Error()
                }

                sleep(50)
                calls.shouldBe(1)
            }

            t
        }

        waitingThreads.size.shouldBe(threadsNum)

        futureA.value = 777

        waitingThreads.forEach { it.join() }

        futureA.value.shouldBe(777)
        futureB.value.shouldBe(778)
        futureC.value.shouldBe(1556)
    }
}