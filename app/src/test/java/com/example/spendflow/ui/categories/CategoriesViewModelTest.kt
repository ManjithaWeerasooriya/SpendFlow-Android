package com.example.spendflow.ui.categories

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.filter
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

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) =
        CategoriesViewModel(repository, writeFailures, state)

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
    fun `income tab shows only active income sources and reacts to default currency changes`() = runTest {
        val salary = Category("salary", "Salary", CategoryType.INCOME, defaultCurrency = "USD")
        val freelance = Category("freelance", "Freelance", CategoryType.INCOME)
        repository.create(salary)
        repository.create(freelance)
        repository.create(Category("old", "Old job", CategoryType.INCOME, isArchived = true))
        val handle = SavedStateHandle()
        val model = viewModel(handle)

        model.screenState.filter { it.content != UiState.Loading }.test {
            val categories = awaitItem()
            assertEquals(CategoriesTab.CATEGORIES, categories.tab)
            val categoryRows = (categories.content as UiState.Success).data
            assertEquals(listOf("Food", "Freelance", "rent", "Salary"), categoryRows.map { it.name })

            model.selectTab(CategoriesTab.INCOME_SOURCES)
            assertEquals(CategoriesScreenState(CategoriesTab.INCOME_SOURCES, UiState.Success(listOf(freelance, salary))), awaitItem())
            assertEquals(CategoriesTab.INCOME_SOURCES, viewModel(handle).selectedTab.value)

            val updatedSalary = salary.copy(defaultCurrency = "EUR")
            repository.update(updatedSalary)
            assertEquals(CategoriesScreenState(CategoriesTab.INCOME_SOURCES, UiState.Success(listOf(freelance, updatedSalary))), awaitItem())

            model.selectTab(CategoriesTab.CATEGORIES)
            val restored = awaitItem()
            assertEquals(CategoriesTab.CATEGORIES, restored.tab)
            assertEquals(categoryRows.map { it.id }, (restored.content as UiState.Success).data.map { it.id })
        }
    }

    @Test
    fun `income tab distinguishes loading empty and errors and recovers live`() = runTest {
        val model = viewModel()
        model.selectTab(CategoriesTab.INCOME_SOURCES)
        assertEquals(UiState.Loading, model.uiState.value)
        model.screenState.filter { it.content != UiState.Loading }.test {
            assertEquals(CategoriesScreenState(CategoriesTab.INCOME_SOURCES, UiState.Success(emptyList())), awaitItem())
            repository.observeError = AppError.Network
            assertEquals(CategoriesScreenState(CategoriesTab.INCOME_SOURCES, UiState.Error(AppError.Network)), awaitItem())
            repository.observeError = null
            assertEquals(CategoriesScreenState(CategoriesTab.INCOME_SOURCES, UiState.Success(emptyList())), awaitItem())

            val source = Category("gift", "Gifts", CategoryType.INCOME)
            repository.create(source)
            assertEquals(CategoriesScreenState(CategoriesTab.INCOME_SOURCES, UiState.Success(listOf(source))), awaitItem())
            repository.delete(source.id)
            assertEquals(CategoriesScreenState(CategoriesTab.INCOME_SOURCES, UiState.Success(emptyList())), awaitItem())
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
    fun `income source form rejects blank oversized and unsupported input before writing`() {
        val model = viewModel()

        assertEquals(
            IncomeSourceFormErrors(R.string.income_sources_name_required, R.string.income_sources_currency_required),
            model.saveIncomeSource(null, "  ", null),
        )
        assertEquals(
            IncomeSourceFormErrors(R.string.income_sources_name_too_long, R.string.income_sources_currency_required),
            model.saveIncomeSource(null, "x".repeat(101), "ZZZ"),
        )
        assertEquals(2, repository.categories.size)
        assertEquals(false, model.incomeSourceSaving.value)
    }

    @Test
    fun `adding a source keeps the selected currency and follows the category duplicate convention`() = runTest {
        repository.create(Category("existing", "Salary", CategoryType.INCOME, defaultCurrency = "LKR"))
        val model = viewModel()

        model.events.test {
            assertEquals(IncomeSourceFormErrors(), model.saveIncomeSource(null, "  Salary  ", "USD"))
            assertEquals(CategoriesEvent.IncomeSourceSaved, awaitItem())
        }
        val added = repository.categories.single { it.defaultCurrency == "USD" }
        assertEquals("Salary", added.name)
        assertEquals(CategoryType.INCOME, added.type)
        assertEquals(2, repository.categories.count { it.name == "Salary" })
    }

    @Test
    fun `rename and archive retain the original ID and currency and remove the source only from the active list`() = runTest {
        val original = Category("salary", "Salary", CategoryType.INCOME, sortOrder = 2, defaultCurrency = "EUR")
        repository.create(original)
        val model = viewModel()
        model.selectTab(CategoriesTab.INCOME_SOURCES)

        turbineScope {
            val states = model.screenState.filter { it.content != UiState.Loading }.testIn(backgroundScope)
            val events = model.events.testIn(backgroundScope)
            assertEquals(UiState.Success(listOf(original)), states.awaitItem().content)

            assertEquals(IncomeSourceFormErrors(), model.saveIncomeSource(original.id, "  Day job ", null))
            assertEquals(CategoriesEvent.IncomeSourceSaved, events.awaitItem())
            val renamed = original.copy(name = "Day job")
            assertEquals(UiState.Success(listOf(renamed)), states.awaitItem().content)

            model.archiveIncomeSource(original.id)
            assertEquals(CategoriesEvent.IncomeSourceSaved, events.awaitItem())
            assertEquals(UiState.Success(emptyList<Category>()), states.awaitItem().content)
            assertEquals(renamed.copy(isArchived = true), repository.categories.single { it.id == original.id })
            assertEquals(3, repository.categories.size)
        }
    }

    @Test
    fun `income source actions reject expense records and report repository write failures`() = runTest {
        val model = viewModel()
        repository.writeError = AppError.PermissionDenied

        turbineScope {
            val states = model.uiState.testIn(backgroundScope)
            val events = model.events.testIn(backgroundScope)
            states.awaitSettled()
            val expense = repository.categories.single { it.id == "1" }
            model.saveIncomeSource(expense.id, "Renamed expense", null)
            assertEquals(CategoriesEvent.IncomeSourceWriteFailed(AppError.NotFound), events.awaitItem())
            model.archiveIncomeSource("1")
            assertEquals(CategoriesEvent.IncomeSourceWriteFailed(AppError.NotFound), events.awaitItem())
            assertEquals(expense, repository.categories.single { it.id == expense.id })

            model.saveIncomeSource(null, "Salary", "USD")
            assertEquals(CategoriesEvent.IncomeSourceWriteFailed(AppError.PermissionDenied), events.awaitItem())
            assertEquals(false, model.incomeSourceSaving.value)
            assertEquals(2, repository.categories.size)
            states.expectNoEvents()
        }
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
