package com.example.spendflow.ui.main

import app.cash.turbine.test
import com.example.spendflow.testing.FakeUserSession
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `logged-out user starts on the auth graph`() {
        assertEquals(AppGraph.AUTH, MainViewModel(FakeUserSession(initialUid = null)).graph.value)
    }

    @Test
    fun `logged-in user starts on the main graph`() {
        assertEquals(AppGraph.MAIN, MainViewModel(FakeUserSession()).graph.value)
    }

    @Test
    fun `follows sign-in and sign-out`() = runTest {
        val session = FakeUserSession(initialUid = null)
        MainViewModel(session).graph.test {
            assertEquals(AppGraph.AUTH, awaitItem())
            session.signIn()
            assertEquals(AppGraph.MAIN, awaitItem())
            session.signOut()
            assertEquals(AppGraph.AUTH, awaitItem())
        }
    }
}
