package com.example.spendflow.ui.categories

import app.cash.turbine.test
import com.example.spendflow.R
import com.example.spendflow.core.common.*
import com.example.spendflow.data.repository.*
import com.example.spendflow.data.remote.mapper.*
import com.example.spendflow.domain.model.*
import com.example.spendflow.testing.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExpenseCategoriesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val food = Category("food", "Food", CategoryType.EXPENSE, iconName = "food", classification = "NEEDS")
    private val rent = Category("rent", "Rent", CategoryType.EXPENSE, sortOrder = 1)
    private val salary = Category("salary", "Salary", CategoryType.INCOME)
    private val repository = FakeCategoryRepository(listOf(food, rent, salary))
    private val budgets = object : BudgetRepository {
        val items = MutableStateFlow<List<Budget>>(emptyList())
        override fun newId() = "cap"
        override fun observeBudgets() = items.map { AppResult.Success(it) }
        override suspend fun get(id: String) = AppResult.Success(items.value.first { it.id == id })
        override suspend fun create(item: Budget): AppResult<Unit> { items.value += item; return AppResult.Success(Unit) }
        override suspend fun update(item: Budget): AppResult<Unit> { items.value = items.value.map { if (it.id == item.id) item else it }; return AppResult.Success(Unit) }
        override suspend fun delete(id: String) = AppResult.Success(Unit)
    }
    private val users = object : UserRepository {
        override fun observeProfile() = flowOf(AppResult.Success(User("u", null, null, "LKR")))
        override suspend fun createProfile(user: User) = AppResult.Success(Unit)
        override suspend fun createProfileIfMissing(user: User) = AppResult.Success(Unit)
        override suspend fun updateProfile(user: User) = AppResult.Success(Unit)
    }
    private fun model() = ExpenseCategoriesViewModel(repository, budgets, users, com.example.spendflow.domain.usecase.GetMonthlySummary(FakeTransactionRepository(), users))

    @Test fun `add and rename validate names inline and preserve metadata`() = runTest {
        val model = model()
        assertEquals(R.string.categories_error_name_required, model.save(null, "  ", "food", "NEEDS"))
        assertEquals(R.string.category_duplicate, model.save(null, " FOOD ", "home", "WANTS"))
        assertEquals(R.string.category_duplicate, model.save(rent.id, "food", "home", "NEEDS"))
        model.events.test {
            assertNull(model.save(null, " Travel ", "transport", "WANTS"))
            assertEquals(AppResult.Success(Unit), awaitItem())
            val added = repository.categories.single { it.name == "Travel" }
            assertEquals("transport", added.iconName)
            assertEquals("WANTS", added.classification)
            assertNull(model.save(food.id, "Groceries", "food", "NEEDS"))
            awaitItem()
            assertEquals(food.copy(name = "Groceries"), repository.categories.single { it.id == food.id })
        }
    }

    @Test fun `archive excludes Quick Add but retains history lookup and can restore`() = runTest {
        val model = model()
        model.events.test {
            model.archive(food, true)
            assertEquals(AppResult.Success(Unit), awaitItem())
            val active = (repository.observeCategories().first() as AppResult.Success).data
            assertFalse(active.any { it.id == food.id })
            assertEquals(food.copy(isArchived = true), (repository.get(food.id) as AppResult.Success).data)
            assertEquals(R.string.category_duplicate, model.nameError("Food"))
            model.archive(food, false)
            awaitItem()
            assertTrue((repository.observeCategories().first() as AppResult.Success).data.any { it.id == food.id })
        }
    }

    @Test fun `reordering persists Quick Add order and leaves income sources unchanged`() = runTest {
        val model = model()
        model.events.test {
            model.reorder(listOf("rent", "food"))
            assertEquals(AppResult.Success(Unit), awaitItem())
            assertEquals(listOf("rent", "food"), (repository.observeCategories(CategoryType.EXPENSE).first() as AppResult.Success).data.map { it.id })
            assertEquals(salary, repository.categories.single { it.id == salary.id })
            model.reorder(listOf("food"))
            expectNoEvents()
        }
    }

    @Test fun `write failure retains category and reports error`() = runTest {
        val model = model()
        repository.writeError = AppError.PermissionDenied
        model.events.test {
            model.archive(food, true)
            assertEquals(AppResult.Error(AppError.PermissionDenied), awaitItem())
            assertEquals(food, repository.categories.single { it.id == food.id })
            assertFalse(model.saving.value)
        }
    }

    @Test fun `monthly cap validates input and updates existing budget`() = runTest {
        val model = model()
        assertEquals(R.string.category_cap_invalid, model.saveCap(food, "-1"))
        model.events.test {
            assertNull(model.saveCap(food, "100.50"))
            awaitItem()
            assertEquals(Money(10050, "LKR"), budgets.items.value.single().limit)
            assertNull(model.saveCap(food, "200"))
            awaitItem()
            assertEquals(1, budgets.items.value.size)
            assertEquals(Money(20000, "LKR"), budgets.items.value.single().limit)
        }
    }

    @Test fun `classification icon order and archive round trip and legacy data remains valid`() {
        val category = food.copy(isArchived = true, sortOrder = 5)
        assertEquals(category, categoryFromFirestore(category.id, category.toFirestore()))
        assertNull(categoryFromFirestore(food.id, food.toFirestore() - "classification").classification)
    }
}
