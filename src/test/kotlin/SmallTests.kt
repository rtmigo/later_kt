/**
 * SPDX-FileCopyrightText: (c) 2022 Artyom IG <github.com/rtmigo>
 * SPDX-License-Identifier: MIT
 **/

import io.github.rtmigo.later.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
//import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import java.lang.Thread.sleep
import kotlin.concurrent.thread


@OptIn(Experimental::class)
class SmallTests {
    @Test
    fun mapTwice() {
        val a = Later.completable<Int>()
        val b = a.map {
            Later.value(it * 2)
        }

        val c = a.map {
            Later.value(it.toString() + it.toString() + it.toString())
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
    fun mapChain() {
        val a = Later.completable<Int>()
        val b = a.map {
            Later.value(it + 1)
        }
        val c = b.map {
            Later.value(it * it)
        }

        a.value = 2
        c.value.shouldBe(9) // (2+1)^2
    }

    @Test
    fun mapTree() {
        val a = Later.completable<Int>()
        val b1 = a.map { Later.value(it + 10) }
        val b2 = a.map { Later.value(it + 20) }

        val c1 = b1.map { Later.value(it * 2) }
        val c2 = b2.map { Later.value(it * 3) }

        a.value = 1
        b1.value.shouldBe(11)
        b2.value.shouldBe(21)

        c1.value.shouldBe(22)
        c2.value.shouldBe(63)
    }

    @Test
    fun mapErrorPropagation() {
        val a = Later.completable<Int>()

        val goodPathA = a
            .map { Later.value(it *2) }
            .map { Later.value(it + 1) }
            .map { (it*it).asLater() }

        val badPath = a
            .map { Later.value(it *2) }
            .map { Later.error<Int>(IllegalArgumentException("Oops")) }
            .map { Later.value(it + 1) }
            .map { (it*it).asLater() }

        a.value = 5

        goodPathA.value.shouldBe(121)
        goodPathA.error.shouldBe(null)

        badPath.error.shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun mapErrorCatching() {
        val a = Later.completable<Int>()

        val goodPathA = a
            .map { Later.value(it *2) }
            .map { Later.value(it + 1) }
            .map { (it*it).asLater() }

        val badPath = a
            .map { Later.value(it *2) }
            .map<Int> { throw IllegalArgumentException("Oops!") }
            .map { Later.value(it + 1) }
            .map { (it*it).asLater() }

        a.value = 5

        goodPathA.value.shouldBe(121)
        goodPathA.error.shouldBe(null)

        badPath.error.shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun thenSuccessChainX() {
        val a = Later.completable<Int>()
        val b = a.map { Later.value(it + 1) }
        val c = b.map { Later.value(it * it) }

        a.value = 2
        c.value.shouldBe(9) // (2+1)^2
    }


    @Test
    fun `cannot read until success`() {
        val a = Later.completable<Int>()
        shouldThrow<LaterUncompletedException> { a.value }

        a.value = 1
        a.value
    }

    @Test
    fun withThreads() {
        val a = Later.completable<Int>()
        val b = a.map {
            val result = Later.completable<Int>()
            thread {
                sleep(50)
                result.value = it * 2
            }
            result
        }
        val c = b.map { Later.value(it + 1) }

        a.value = 2
        c.isComplete.shouldBeFalse()
        sleep(100)
        c.isComplete.shouldBeTrue()
        c.value.shouldBe(5)  // 2*2+1
    }

    @Test
    fun readingValueWhenThereIsError() {
        val later = Later.error<Int>(IllegalArgumentException("Oops"))
        val e = shouldThrow<LaterErrorException> {
            later.value
        }
        e.inner.shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun awaitingErrorValue() {
        val later = Later.completable<Int>()

        val chain = later
            .map { (it*2).asLater() }
            .map<Int> { throw IllegalArgumentException("Oops") }
            .map { (it*2).asLater() }
            .map { (it*2).asLater() }

        thread {
            later.value = 5
        }

        val e = shouldThrow<LaterErrorException> {
            chain.await()
        }


        e.inner.shouldBeInstanceOf<IllegalArgumentException>()
    }
}