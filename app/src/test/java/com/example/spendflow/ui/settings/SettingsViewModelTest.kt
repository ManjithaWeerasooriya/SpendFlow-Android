package com.example.spendflow.ui.settings

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.common.AppResult
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.domain.model.CapThresholds
import com.example.spendflow.domain.model.Money
import com.example.spendflow.domain.model.PaymentMethod
import com.example.spendflow.domain.model.Transaction
import com.example.spendflow.domain.model.TransactionType
import com.example.spendflow.domain.model.User
import com.example.spendflow.domain.model.UserPreferences
import com.example.spendflow.testing.FakeAuthRepository
import com.example.spendflow.testing.FakeProfileRepository
import com.example.spendflow.testing.FakeUserSession
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeProfileRepository(User("u1", null, null, "LKR"))
    private val transactions = Transactions()
    private val session = FakeUserSession()
    private val auth = FakeAuthRepository(session)

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) =
        SettingsViewModel(profiles, transactions, profiles, auth, state)

    private suspend fun ReceiveTurbine<UiState<SettingsContent>>.awaitContent(): SettingsContent {
        var item = awaitItem()
        while (item is UiState.Loading) item = awaitItem()
        return (item as UiState.Success).data
    }

    @Test
    fun `profile without saved preferences shows the defaults`() = runTest {
        viewModel().uiState.test {
            val content = awaitContent()
            assertEquals("LKR", content.baseCurrency)
            assertEquals(UserPreferences(), content.preferences)
            assertEquals(CapThresholds(75, 90, 100), content.preferences.capThresholds)
            assertFalse(content.hasTransactions)
        }
    }

    @Test
    fun `missing profile is an error state`() = runTest {
        profiles.profile.value = null
        viewModel().uiState.test {
            var item = awaitItem()
            while (item is UiState.Loading) item = awaitItem()
            assertEquals(UiState.Error(AppError.NotFound), item)
        }
    }

    @Test
    fun `turning the streak off saves it and leaves transactions alone`() = runTest {
        profiles.profile.value = User("u1", null, null, "LKR", UserPreferences(streakEnabled = true))
        transactions.rows.value = listOf(expense())
        val model = viewModel()
        model.uiState.test {
            assertTrue(awaitContent().preferences.streakEnabled)
            model.setStreakEnabled(false)
            assertFalse(awaitContent().preferences.streakEnabled)
        }
        assertFalse(profiles.profile.value!!.preferences.streakEnabled)
        assertEquals(listOf(expense()), transactions.rows.value)
        assertEquals(0, transactions.writes)
    }

    @Test
    fun `lock-screen switch is saved to the profile`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitContent()
            model.setLockScreenQuickEntry(true)
            assertTrue(awaitContent().preferences.lockScreenQuickEntry)
        }
    }

    @Test
    fun `currency change without transactions applies at once`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitContent()
            model.events.test {
                model.selectBaseCurrency("USD")
                assertEquals(SettingsEvent.CurrencyChanged("USD"), awaitItem())
            }
            assertEquals("USD", awaitContent().baseCurrency)
        }
        assertNull(model.pendingCurrency.value)
    }

    @Test
    fun `currency change with transactions waits for confirmation`() = runTest {
        transactions.rows.value = listOf(expense())
        val state = SavedStateHandle()
        val model = viewModel(state)
        model.uiState.test {
            assertTrue(awaitContent().hasTransactions)
            model.selectBaseCurrency("USD")
            assertEquals("USD", model.pendingCurrency.value)
            assertEquals(0, profiles.writeCount)

            // Survives process death
            assertEquals("USD", viewModel(state).pendingCurrency.value)

            model.confirmBaseCurrency()
            assertEquals("USD", awaitContent().baseCurrency)
        }
        assertNull(model.pendingCurrency.value)
        assertEquals(0, transactions.writes)
    }

    @Test
    fun `dismissed currency change writes nothing`() = runTest {
        transactions.rows.value = listOf(expense())
        val model = viewModel()
        model.uiState.test {
            awaitContent()
            model.selectBaseCurrency("EUR")
            model.dismissBaseCurrency()
            assertNull(model.pendingCurrency.value)
            expectNoEvents()
        }
        assertEquals(0, profiles.writeCount)
        assertEquals("LKR", profiles.profile.value!!.baseCurrency)
    }

    @Test
    fun `same or unknown currency is ignored`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitContent()
            model.selectBaseCurrency("LKR")
            model.selectBaseCurrency("XYZ")
        }
        assertEquals(0, profiles.writeCount)
    }

    @Test
    fun `cap thresholds are validated before saving`() = runTest {
        val model = viewModel()
        model.uiState.test {
            awaitContent()
            assertEquals(R.string.settings_cap_error_range, model.saveCapThresholds("", "90", "100"))
            assertEquals(R.string.settings_cap_error_range, model.saveCapThresholds("0", "90", "100"))
            assertEquals(R.string.settings_cap_error_range, model.saveCapThresholds("75", "90", "101"))
            assertEquals(R.string.settings_cap_error_range, model.saveCapThresholds("7.5", "90", "100"))
            assertEquals(R.string.settings_cap_error_order, model.saveCapThresholds("90", "75", "100"))
            assertEquals(R.string.settings_cap_error_order, model.saveCapThresholds("75", "75", "100"))
            assertEquals(0, profiles.writeCount)

            assertNull(model.saveCapThresholds(" 50", "80 ", "95"))
            assertEquals(CapThresholds(50, 80, 95), awaitContent().preferences.capThresholds)
        }
    }

    @Test
    fun `failed write is reported`() = runTest {
        profiles.writeError = AppError.PermissionDenied
        val model = viewModel()
        model.uiState.test {
            awaitContent()
            model.events.test {
                model.setStreakEnabled(true)
                assertEquals(SettingsEvent.ShowError(AppError.PermissionDenied), awaitItem())
            }
        }
    }

    @Test
    fun `log out ends the session`() = runTest {
        viewModel().logOut()
        assertEquals(1, auth.logOutCount)
        assertNull(session.currentUid())
    }

    private fun expense() = Transaction(
        id = "t1",
        type = TransactionType.EXPENSE,
        amount = Money(2500, "LKR"),
        rate = BigDecimal.ONE,
        baseAmount = Money(2500, "LKR"),
        dateTime = Instant.parse("2026-10-15T10:00:00Z"),
        categoryId = "food",
        paymentMethod = PaymentMethod.CASH,
    )

    private class Transactions : TransactionRepository {
        val rows = MutableStateFlow<List<Transaction>>(emptyList())
        var writes = 0

        override fun newId() = "t"
        override suspend fun get(id: String) = AppResult.Success(rows.value.first { it.id == id })
        override suspend fun create(item: Transaction) = AppResult.Success(Unit).also { writes++ }
        override suspend fun update(item: Transaction) = AppResult.Success(Unit).also { writes++ }
        override suspend fun delete(id: String) = AppResult.Success(Unit).also { writes++ }
        override fun observeTransactions(from: Instant, until: Instant) = rows.map { AppResult.Success(it) }
        override fun observeRecent(limit: Int) = rows.map { AppResult.Success(it.take(limit)) }
    }
}
