/**
 * SPDX-FileCopyrightText: (c) 2022 Artyom IG <github.com/rtmigo>
 * SPDX-License-Identifier: MIT
 **/

import io.github.rtmigo.later.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.*
import io.kotest.matchers.shouldBe
//import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import kotlin.concurrent.thread


class SmallTests {
    @Test
    fun twoThen() {
        val a = mutableLater<Int>()
        val b = a.map {
            later(it * 2)
        }

        val c = a.map {
            later(it.toString() + it.toString() + it.toString())
        }

        a.isComplete.shouldBeFalse()
        b.isComplete.shouldBeFalse()
        c.isComplete.shouldBeFalse()

        a.value = 5

        a.isComplete.shouldBeTrue()
        b.isComplete.shouldBeTrue()
        c.isComplete.shouldBeTrue()

        a.value.shouldBe(5)
        b.value.shouldBe(10)
        c.value.shouldBe("555")
    }

    @Test
    fun thenSuccessChain() {
        val a = mutableLater<Int>()
        val b = a.map {
            later(it + 1)
        }
        val c = b.map {
            later(it * it)
        }

        a.value = 2
        c.value.shouldBe(9) // (2+1)^2
    }

    @Test
    fun thenSuccessTree() {
        val a = mutableLater<Int>()
        val b1 = a.map { later(it + 10) }
        val b2 = a.map { later(it + 20) }

        val c1 = b1.map { later(it * 2) }
        val c2 = b2.map { later(it * 3) }

        a.value = 1
        b1.value.shouldBe(11)
        b2.value.shouldBe(21)

        c1.value.shouldBe(22)
        c2.value.shouldBe(63)
    }

    @Test
    fun thenSuccessChainX() {
        val a = mutableLater<Int>()
        val b = a.map { later(it + 1) }
        val c = b.map { later(it * it) }

        a.value = 2
        c.value.shouldBe(9) // (2+1)^2
    }


    @Test
    fun `cannot read until success`() {
        val a = mutableLater<Int>()
        shouldThrow<LaterNotCompletedException> { a.value }

        a.value = 1
        a.value
    }




//    @Test
//    fun runningSyncFromCoroutines() {
//        val a = mutableLater<Int>()
//        val b = a.map { later(it * 2) }
//
//        runBlocking {
//            delay(100)
//            a.value = 5
//        }
//
//        b.value.shouldBe(10)
//    }

    @Test
    fun withThreads() {
        val a = mutableLater<Int>()
        val b = a.map {
            val result = mutableLater<Int>()
            thread {
                sleep(50)
                result.value = it * 2
            }
            result
        }
        val c = b.map { later(it + 1) }

        a.value = 2
        c.isComplete.shouldBeFalse()
        sleep(100)
        c.isComplete.shouldBeTrue()
        c.value.shouldBe(5)  // 2*2+1
    }
}