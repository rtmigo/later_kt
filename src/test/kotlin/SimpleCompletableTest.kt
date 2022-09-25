import io.github.rtmigo.later.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.*
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test


@OptIn(Experimental::class)
class SimpleCompletableTest {
    @Test
    fun completeCompletable() {
        val c = Later.completable<Int>()
        c.isComplete.shouldBeFalse()
        c.value = 5
        c.isComplete.shouldBeTrue()
        c.value.shouldBe(5)
    }

    @Test
    fun cannotCompleteTwice() {
        val c = Later.completable<Int>()
        c.value = 5
        shouldThrow<LaterCompletedException> {
            c.value = 5
            Unit
        }
    }

    @Test
    fun completableOnComplete() {
        val future = Later.completable<String>()
        var callsA = 0
        var callsB = 0

        future.onSuccess { callsA ++ }
        future.onSuccess { callsB ++ }
        callsA.shouldBe(0)
        callsB.shouldBe(0)

        // Завершаем future и убеждаемся, что каждый слушатель запустился один раз.
        future.value = "hello"
        callsA.shouldBe(1)
        callsB.shouldBe(1)

        // Теперь будущее завершено, но вызывать whenComplete можно.
        // Оно посто выполнится моментально.
        var callsC = 0
        future.onSuccess { callsC ++ }
        callsA.shouldBe(1)
        callsB.shouldBe(1)
        callsC.shouldBe(1)
    }
}