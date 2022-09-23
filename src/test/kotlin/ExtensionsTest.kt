/**
 * SPDX-FileCopyrightText: (c) 2022 Artyom IG <github.com/rtmigo>
 * SPDX-License-Identifier: MIT
 **/

import io.github.rtmigo.later.*
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionsTest {
    @Test
    fun alsoFut() {
        val f = mutableLater<Int>()
        var runs = 0
        f.thenAlso { runs++ }.shouldBe(f)
        runs.shouldBe(0)
        f.value = 5
        runs.shouldBe(1)
    }

    @Test
    fun testValueOrNull() {
        val f = mutableLater<Int>()
        f.valueOrNull.shouldBe(null)
        f.value = 5
        f.valueOrNull.shouldBe(5)
    }

    @Test
    fun testValueOrNullNullable() {
        val f = mutableLater<Int?>()
        f.valueOrNull.shouldBe(null)
        f.value = 5
        f.valueOrNull.shouldBe(5)
    }

    @Test
    fun onValue() {
        val f = mutableLater<Int>()
        var runs = 0

        f.onValue {
            it.shouldBe(5)
            runs++
        }.shouldBe(f)
        runs.shouldBe(0)
        f.value = 5

        runs.shouldBe(1)
    }
}