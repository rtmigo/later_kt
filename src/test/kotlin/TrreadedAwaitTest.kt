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

@Tag("slow")
@Timeout(2)
class TrreadedAwaitTest {
    @RepeatedTest(50)
    fun awaitOneThread() {
        val future = mutableLater<String>()
        thread {
            sleep(Random.nextLong(50))
            future.value = "Done!"
        }
        future.await().shouldBe("Done!")
        future.value.shouldBe("Done!")
    }

    @RepeatedTest(10)
    fun awaitForCompleted() {
        val future = mutableLater<String>()
        future.value = "Done"
        repeat(3) {
            future.await().shouldBe("Done")
        }
    }

    @RepeatedTest(1000)
    fun `await multiple threads with additional delays`() {
        withLaterTestingDelays {
            awaitMultipleThreadsInner()
        }
    }

    @RepeatedTest(1000)
    fun `await multiple threads`() {
        awaitMultipleThreadsInner()
    }


    fun awaitMultipleThreadsInner() {
        val futureA: CompletableLater<Int> = mutableLater<Int>()
        val futureB: Later<String> = futureA.map { ("$it=$it").asLater() }
        val futureC: Later<String> = futureB.map { ("$it!").asLater() }

        val maxMs = 7L
        val maxRepeats = 3
        val threadsNum = 20

        val waitingThreads = (1..threadsNum).map {
            thread {
                repeat(Random.nextInt(maxRepeats)) {
                    sleep(Random.nextLong(maxMs))
                    when ((1..3).random()) {
                        1 -> futureA.await()
                        2 -> futureB.await()
                        3 -> futureC.await()
                        else -> throw Error()
                    }
                }
            } }

        waitingThreads.size.shouldBe(threadsNum )

        thread {
            sleep(Random.nextLong(maxMs * maxRepeats))
            futureA.value = 777
        }


        futureC.await()
        futureB.value.shouldBe("777=777")
        futureC.value.shouldBe("777=777!")
        futureA.value.shouldBe(777)

        waitingThreads.forEach { it.join() }
    }
}