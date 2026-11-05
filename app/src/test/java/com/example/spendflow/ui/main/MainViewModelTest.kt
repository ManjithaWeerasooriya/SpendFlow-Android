package com.example.spendflow.ui.main

import app.cash.turbine.test
import com.example.spendflow.testing.FakeAuthRepository
import com.example.spendflow.testing.FakeUserSession
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(session: FakeUserSession, auth: FakeAuthRepository = FakeAuthRepository(session)) =
        MainViewModel(session, auth)

    @Test
    fun `logged-out user starts on the auth graph`() {
        assertEquals(AppGraph.AUTH, viewModel(FakeUserSession(initialUid = null)).graph.value)
    }

    @Test
    fun `restored session starts on the main graph`() {
        assertEquals(AppGraph.MAIN, viewModel(FakeUserSession()).graph.value)
    }

    @Test
    fun `log in goes to main and log out back to auth`() = runTest {
        val session = FakeUserSession(initialUid = null)
        val auth = FakeAuthRepository(session)
        viewModel(session, auth).graph.test {
            assertEquals(AppGraph.AUTH, awaitItem())
            auth.logIn("name@mail.com", "secret")
            assertEquals(AppGraph.MAIN, awaitItem())
            auth.logOut()
            assertEquals(AppGraph.AUTH, awaitItem())
        }
    }

    @Test
    fun `registration goes through onboarding to main`() = runTest {
        val session = FakeUserSession(initialUid = null)
        val auth = FakeAuthRepository(session)
        viewModel(session, auth).graph.test {
            assertEquals(AppGraph.AUTH, awaitItem())
            auth.register("name@mail.com", "secret1")
            assertEquals(AppGraph.ONBOARDING, awaitItem())
            auth.finishOnboarding()
            assertEquals(AppGraph.MAIN, awaitItem())
        }
    }
}
