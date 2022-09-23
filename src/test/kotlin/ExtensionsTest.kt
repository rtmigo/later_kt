/**
 * SPDX-FileCopyrightText: (c) 2022 Artyom IG <github.com/rtmigo>
 * SPDX-License-Identifier: MIT
 **/

@file:OptIn(Experimental::class)

import io.github.rtmigo.later.*
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionsTest {
    @Test
    fun onComplete() {
        val f = Later.completable<Int>()
        var runs = 0

        f.onComplete {
            it.shouldBe(5)
            runs++
        }.shouldBe(f)
        runs.shouldBe(0)
        f.value = 5

        runs.shouldBe(1)
    }
}