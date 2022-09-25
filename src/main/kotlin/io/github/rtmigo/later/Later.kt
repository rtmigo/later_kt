/**
 * SPDX-FileCopyrightText: (c) 2022 Artyom IG <github.com/rtmigo>
 * SPDX-License-Identifier: MIT
 **/

@file:OptIn(Experimental::class)

package io.github.rtmigo.later


import java.util.concurrent.locks.*
import kotlin.concurrent.withLock
import kotlin.random.Random

@RequiresOptIn
annotation class Experimental

class LaterErrorException(val inner: Throwable) : Exception()
class LaterCompletedException : IllegalStateException()
class LaterUncompletedException : IllegalStateException()

/**
 * Represents a potential value, that will be available at some
 * time in the future.
 **/
@Experimental
interface Later<T> {
    /**
     * Returns the value, if it is available.
     *
     * Throws [LaterUncompletedException] if [isComplete] is `false`.
     *
     * Throws [LaterErrorException] if [isError] is `true`.
     *
     **/
    val value: T

    /**  */
    val error: Throwable?

    /**
     * Returns `true` if this object has a [value] set.
     **/
    val isComplete: Boolean

    /**
     * Transforms future [value] of the current object to another [Later]. This mapping can be set
     * before we actually receive the value.
     **/
    fun <R> map(block: (arg: T) -> Later<R>): Later<R>

    /**
     * Runs [block] when the [value] becomes available. If the value was available before the
     * call, run [block] immediately.
     **/
    fun onComplete(
        ifError: (e: Throwable) -> Unit,
        ifSuccess: (t: T) -> Unit,
    ): Later<T>

    /**
     * Blocks the current thread until the [value] is available. Then returns the value.
     *
     * If the value was available before the call, just returns the value.
     **/
    fun await(): T

    companion object
}

/**
 * A [Later] with [value] was not initially set.
 **/
interface CompletableLater<T> : Later<T> {
    /** This property can only be set once. Retrying will result in an exception. */
    override var value: T
}

private typealias LaterCompanion = Later.Companion

/** Creates a completed [Later] with value [t]. */
fun <T> LaterCompanion.value(t: T): Later<T> = LaterImpl<T>(
    _value = t, _error = null, _isComplete = true)

/** Creates a completed [Later] with error [e]. */
fun <T> LaterCompanion.error(e: Throwable): Later<T> = LaterImpl<T>(
    _value = null, _error = e, _isComplete = true)

/** Creates an uncompleted [Later]. The [Later.value] is expected to be set later when it becomes
 * available. */
fun <T> LaterCompanion.completable(): CompletableLater<T> =
    LaterImpl<T>(_value = null, _error = null, _isComplete = false)

/** Creates a completed [Later] with [this] as value. */
fun <T> T.asLater(): Later<T> = Later.value(this)

fun <T> Later<T>.onSuccess(block: (t: T) -> Unit): Later<T> =
    this.onComplete(ifError = { }, ifSuccess = block)

fun <T> Later<T>.onError(block: (e: Throwable) -> Unit): Later<T> =  // TODO unit-test
    this.onComplete(ifError = block, ifSuccess = { })

val Later<*>.isError: Boolean get() = this.error != null

@Deprecated("Obsolete", ReplaceWith("Later.completable<T>()"))
fun <T> mutableLater(): CompletableLater<T> = LaterCompanion.completable()

@Deprecated("Obsolete", ReplaceWith("Later.value<T>(v)"))
fun <T> later(v: T): Later<T> = LaterImpl<T>(_value = v, _error = null, _isComplete = true)


private data class Resolver<T>(
    val onError: (Throwable) -> Unit,
    val onSuccess: (T) -> Unit,
)

/**
 * Если установить в `true`, то [LaterImpl] (благодаря [LaterImpl.randomPauseIfTesting]) будет
 * делать случайные задержки, помогающие проявить проблемы синхронизации потоков.
 *
 * Это действует только в коде, где разрешены ассерты. В продакшн-коде просто не будет проверок
 * этого условия, и соответственно не будет задержек.
 *
 **/
private var RANDOM_PAUSES_IN_DEBUG_BUILDS = false

internal fun withLaterTestingDelays(block: () -> Unit) {
    RANDOM_PAUSES_IN_DEBUG_BUILDS = true
    try {
        block()
    } finally {
        RANDOM_PAUSES_IN_DEBUG_BUILDS = false
    }

}

private class LaterImpl<T> constructor(
    private var _value: T?,
    private var _error: Throwable?,
    private var _isComplete: Boolean,
) : CompletableLater<T> {
    private fun <R> synced(block: () -> R) = synchronized(this, block)

    //private val lock = ReentrantLock(false)

    init {
        checkArgs(_isComplete, _value, _error)
    }

    // следующий лок мы инициализируем только в случае вызова await
    private var awaitingLock: ReentrantLock? = null
    private var awaitingCondition: Condition? = null

    private fun randomPauseIfTesting(): Boolean {
        // убедимся, что эта функция запускается только в отладочных билдах

        var assertionsEnabled = false
        try {
            assert(false)
        } catch (e: java.lang.AssertionError) {
            assertionsEnabled = true
        }
        require(assertionsEnabled)

        if (RANDOM_PAUSES_IN_DEBUG_BUILDS)
            Thread.sleep(Random.nextLong(2))

        return true
    }

    override fun await(): T {
        if (isComplete)
            return this.value
        else {
            assert(randomPauseIfTesting())

            synced {
                assert(randomPauseIfTesting())
                if (!isComplete && awaitingLock == null) {
                    awaitingLock = ReentrantLock()
                    assert(randomPauseIfTesting())
                    awaitingCondition = awaitingLock!!.newCondition()
                }
                assert(randomPauseIfTesting())
            }

            assert(randomPauseIfTesting())

            // Здесь awaitingLock может по-прежнему быть null. Но только в случае, если в прошлом
            // синхроблоке мы обнаружили, что объект уже isComplete. Он уже не перестанет быть
            // isComplete в таком случае
            assert(isComplete || awaitingLock != null)

            awaitingLock?.withLock {
                assert(randomPauseIfTesting())
                // Есть риск, что перед нами в такой лок запрыгнул финализер. Тогда он там вызвал
                // signalAll, и больше таких сигналов не будет. Запускать await уже поздно -
                // зависнем.
                //
                // Чтобы дать подсказку, что финализер уже отстрелялся, он устанавливает
                // awaitingCondition в null, причём внутри такого же withLock

                if (this.isComplete || this.awaitingCondition == null)
                    return this.value
                else
                    this.awaitingCondition!!.await()

                assert(randomPauseIfTesting())
            }

            assert(randomPauseIfTesting())
        }

        assert(randomPauseIfTesting())

        assert(this.isComplete)
        return this.value  // this will throw error, if there is error
    }

    override var isComplete: Boolean
        get() = _isComplete
        private set(x) {
            // мы можем только один раз заменить null на что-то другое
            if (!x)
                throw IllegalArgumentException()
            if (this._isComplete)
                throw IllegalStateException()
            this._isComplete = x
        }

    override var value: T
        get() =
            if (this.isComplete)
                if (this.isError)
                    throw LaterErrorException(this.error!!)
                else
                    @Suppress("UNCHECKED_CAST")
                    this._value as T
            else
                throw LaterUncompletedException()
        set(newValue: T) {
            complete(newValue, null)
        }

    override var error: Throwable?
        get() =
            if (this.isComplete)
                this._error
            else
                throw LaterUncompletedException()
        set(e) {
            complete(null, e)
        }


    private fun complete(newValue: T?, newError: Throwable?) {
        checkArgs(true, newValue, newError)

        val listenersToRun = synced {
            if (!this.isComplete) {
                // Сначала меняем _value, а потом isComplete. Это позволит даже в
                // несинхронизированном коде верить положительному значению isComplete. То есть,
                // `if (x.isComplete) x.value else null` не будет требовать synchronized.
                // Единственный нюанс в том, что без synchronized объект может быть готов
                // возвращать значение чуть раньше, чем isComplete станет сообщать об этой
                // готовности

                this._value = newValue
                this._error = newError

                assert(!this.areListenersImmediate)
                this.isComplete = true
                assert(this.areListenersImmediate)

                assert(randomPauseIfTesting())

                // Следующий лок мы устанавливаем в не-null в таком же synchronized-блоке.
                // Значениям null и не-null можно верить. А после установки статуса
                // isComplete, поле никогда больше не инициализируется в не-null.
                this.awaitingLock?.withLock {
                    assert(randomPauseIfTesting())

                    this.awaitingCondition!!.signalAll()

                    // следующая строчка сообщает методу Later.await(), чтобы не пытался ждать
                    // awaitingCondition.await() - сигналов не будет. Он проверит значение
                    // в таком же withLock
                    this.awaitingCondition = null

                    assert(randomPauseIfTesting())
                }

                assert(randomPauseIfTesting())

                // Статус isComplete значит что с данного момента последующие вызовы onComplete
                // уже не будут обновлять поле listeners.
                //
                // Все прежние обновления listeners производились с синхронизацией. Поскольку мы
                // внутри синхронизированного блока, то значение listeners актуально, и
                // обновлено.
                //
                // Поскольку именно мы установили isComplete (а это можно сделать лишь раз),
                // значит это мы запретили списку listeners будущие обновления. Мы можем сейчас
                // этим списком эксклюзивно распорядиться.
                //
                // Устанавливаем список обработчиков в null, чтобы облегчить работу сборщику
                // мусора. А сами обработчики возвращаем наружу блока synchronized. Его копия
                // есть только у нас, и поэтому далее синхронизация списку необязательна

                val prevListenersOrNull = this.listeners

                this.listeners = null
                assert(randomPauseIfTesting())

                prevListenersOrNull
            } else
                throw LaterCompletedException()
        }

        assert(this.listeners == null)
        assert(this.areListenersImmediate)

        // listenersToRun может быть null, если таким же было поле listeners, то есть, если
        // ни один слушатель никогда не был задан
        listenersToRun?.forEach(::runResolver)
    }


    /**
     * Когда это свойство начинает возвращать `true`, это значит, что метод [applyLater] должен
     * запускать слушателей моментально, а не ставить их в очередь.
     *
     * Значению `true` можно верить в несинхронизированном коде, а `false` стоит перепроверить
     * после синхронизации.
     **/
    private val areListenersImmediate
        get() = this.isComplete


    private fun addListener(listener: Resolver<T>) {
        val runNowListener = if (this.areListenersImmediate) {
            listener
        } else {
            // Значению areListenersImmediate=false мы не верим, поскольку параллельный поток прямо
            // сейчас может комплитить этот объект. Поэтому синхронизируемся и проверяем ещё раз
            synced {
                if (this.areListenersImmediate)
                    listener
                else {
                    if (this.listeners == null)
                        this.listeners = mutableListOf(listener)
                    else
                        this.listeners!!.add(listener)
                    null
                }
            }
        }
        if (runNowListener != null)
            runResolver(runNowListener)
    }

    private fun runResolver(r: Resolver<T>) {
        if (this.isError)
            r.onError(this.error!!)
        else
            r.onSuccess(this.value)
    }

    private var listeners: MutableList<Resolver<T>>? = null

    override fun onComplete(
        ifError: (arg: Throwable) -> Unit,
        ifSuccess: (arg: T) -> Unit,
    ): Later<T> {
        this.addListener(Resolver<T>(ifError, ifSuccess))
        return this
    }

    /**
     * Трансформирует будущее значение данного объекта [Later] в другой объект [Later].
     *
     * [block] запускается, когда данный объект получает статус [isComplete]. Если статус уже такой,
     * то запуск происходит сразу.
     *
     * ```kotlin
     * future.then { Fut.value(it*2) }
     * ```
     **/
    override fun <R> map(block: (arg: T) -> Later<R>): Later<R> {
        val returnedLater = LaterImpl<R>(_value = null, _error = null, _isComplete = false)

        this.onComplete(
            ifError = {
                returnedLater.error = it
            },
            ifSuccess = {
                assert(this@LaterImpl.isComplete)

                val mappedLater: Later<R>? =
                    try {
                        block(this.value)
                    } catch (e: Throwable) {
                        // block(...) throws exception - this means there will be no mapped value
                        // (the mapped Later results an error).
                        //
                        // This does not apply to THIS object: if we have a value, it's ok
                        returnedLater.error = e
                        null
                    }

                // the block generated a new Later for us. We need that Later *only* to update
                // the Later we returned from `map()`
                mappedLater?.onComplete(
                    ifError = { returnedLater.error = it },
                    ifSuccess = { returnedLater.value = it })
            }
        )

        return returnedLater
    }

    companion object {
        private fun <T> checkArgs(
            isComplete: Boolean,
            value: T?,
            error: Throwable?,
        ) {
            if (value != null || error != null)
                require(isComplete)
        }
    }
}