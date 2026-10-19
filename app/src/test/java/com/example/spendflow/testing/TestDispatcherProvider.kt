package com.example.spendflow.testing

import com.example.spendflow.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestDispatcher

/** Routes every dispatcher to one test dispatcher, so injected code runs under the test scheduler. */
class TestDispatcherProvider(dispatcher: TestDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}
