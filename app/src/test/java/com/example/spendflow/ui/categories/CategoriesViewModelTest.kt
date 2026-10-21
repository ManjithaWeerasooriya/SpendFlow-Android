package com.example.spendflow.ui.categories

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.example.spendflow.R
import com.example.spendflow.core.common.AppError
import com.example.spendflow.core.ui.UiState
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType
import com.example.spendflow.testing.FakeCategoryRepository
import com.example.spendflow.testing.FakeWriteFailures
import com.example.spendflow.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Template for ViewModel tests:
 * - [MainDispatcherRule] so viewModelScope runs on a test dispatcher
 * - fakes from the `testing` package instead of mocks; they are configured per test
 * - Turbine's `test {}` to assert on StateFlow/Flow emissions in order
 */
class CategoriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeCategoryRepository(
        listOf(
            Category(id = "1", name = "rent", type = CategoryType.EXPENSE),
            Category(id = "2", name = "Food", type = CategoryType.EXPENSE),
        ),
    )

    private val writeFailures = FakeWriteFailures()

    private fun viewModel() = CategoriesViewModel(repository, writeFailures)

    /**
     * Skips Loading. With the unconfined dispatcher, StateFlow often settles before Turbine reads it,
     * so asserting on an intermediate Loading would be timing-dependent.
     */
    private suspend fun ReceiveTurbine<UiState<List<Category>>>.awaitSettled(): UiState<List<Category>> {
        var item = awaitItem()
        while (item is UiState.Loading) item = awaitItem()
        return item
    }

    @Test
    fun `shows categories sorted by name`() = runTest {
        viewModel().uiState.test {
            val state = awaitSettled() as UiState.Success
            assertEquals(listOf("Food", "rent"), state.data.map { it.name })
        }
    }

    @Test
    fun `reflects repository changes live`() = runTest {
        viewModel().uiState.test {
            awaitSettled()
            repository.create(Category(id = "3", name = "Bills", type = CategoryType.EXPENSE))
            val state = awaitItem() as UiState.Success
            assertEquals(listOf("Bills", "Food", "rent"), state.data.map { it.name })
        }
    }

    @Test
    fun `repository error is shown as error state`() = runTest {
        repository.observeError = AppError.PermissionDenied

        viewModel().uiState.test {
            assertEquals(UiState.Error(AppError.PermissionDenied), awaitSettled())
        }
    }

    @Test
    fun `adding a category trims the name, saves it and confirms`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.addCategory("  Salary ", CategoryType.INCOME)
            assertEquals(CategoriesEvent.CategoryAdded, awaitItem())
        }
        val saved = repository.categories.single { it.name == "Salary" }
        assertEquals(CategoryType.INCOME, saved.type)
    }

    @Test
    fun `blank name is rejected without saving`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.addCategory("   ", CategoryType.EXPENSE)
            assertEquals(CategoriesEvent.ShowMessage(R.string.categories_error_name_required), awaitItem())
        }
        assertEquals(2, repository.categories.size)
    }

    @Test
    fun `failed save shows the error message and keeps state`() = runTest {
        repository.writeError = AppError.Unauthenticated
        val viewModel = viewModel()

        turbineScope {
            val states = viewModel.uiState.testIn(backgroundScope)
            val events = viewModel.events.testIn(backgroundScope)
            states.awaitSettled()

            viewModel.addCategory("Travel", CategoryType.EXPENSE)

            assertEquals(CategoriesEvent.ShowError(AppError.Unauthenticated), events.awaitItem())
            states.expectNoEvents()
        }
    }

    @Test
    fun `save rejected later by the server is shown as a typed error`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.addCategory("Travel", CategoryType.EXPENSE)
            assertEquals(CategoriesEvent.CategoryAdded, awaitItem())

            writeFailures.reject("users/test-user/categories/category-1", AppError.PermissionDenied)
            assertEquals(CategoriesEvent.ShowError(AppError.PermissionDenied), awaitItem())
        }
    }
}
