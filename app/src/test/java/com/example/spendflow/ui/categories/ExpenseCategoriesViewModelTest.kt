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
    private val budgets = FakeBudgetRepository()
    private val profiles = FakeProfileRepository(User("u", null, null, "LKR"))
    private val transactions = FakeTransactionRepository()
    private val warnings = com.example.spendflow.core.notifications.CapWarnings(
        object : com.example.spendflow.core.notifications.CapAlertHistory {
            val shown = mutableMapOf<String, CapLevel>()
            override fun lastShown(uid: String, month: java.time.YearMonth, budgetId: String) = shown[budgetId] ?: CapLevel.UNDER
            override fun record(uid: String, month: java.time.YearMonth, budgetId: String, level: CapLevel) { shown[budgetId] = level }
        },
        FakeUserSession("u"),
    )
    private val capUsage = com.example.spendflow.domain.usecase.ObserveCapUsage(
        budgets,
        com.example.spendflow.domain.usecase.GetMonthlySummary(transactions, profiles),
        profiles,
        object : com.example.spendflow.domain.usecase.CurrentMonth {
            override val zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
            override fun months() = flowOf(java.time.YearMonth.now(zone))
        },
    )
    private fun model() = ExpenseCategoriesViewModel(repository, budgets, profiles, capUsage, warnings)

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

    @Test fun `cap amount errors are inline and nothing is saved`() = runTest {
        val model = model()
        assertEquals(R.string.category_cap_required, model.saveCap(food, "  "))
        assertEquals(R.string.category_cap_positive, model.saveCap(food, "0"))
        assertEquals(R.string.category_cap_positive, model.saveCap(food, "-500"))
        assertEquals(R.string.category_cap_invalid, model.saveCap(food, "abc"))
        assertEquals(R.string.category_cap_invalid, model.saveCap(food, "1.234"))
        assertTrue(budgets.items.value.isEmpty())
    }

    @Test fun `monthly cap saves alerts and updates the one existing cap`() = runTest {
        val model = model()
        model.events.test {
            assertNull(model.saveCap(food, "100.50", setOf(CapLevel.WARNING, CapLevel.OVER)))
            awaitItem()
            val saved = budgets.items.value.single()
            assertEquals(Money(10050, "LKR"), saved.limit)
            assertEquals(food.id, saved.categoryId)
            assertEquals(BudgetPeriod.MONTHLY, saved.period)
            assertEquals(setOf(CapLevel.WARNING, CapLevel.OVER), saved.alerts)
            assertNull(model.saveCap(food, "200"))
            awaitItem()
            assertEquals(1, budgets.items.value.size)
            assertEquals(Money(20000, "LKR"), budgets.items.value.single().limit)
            assertEquals(CapLevel.ALERTS, budgets.items.value.single().alerts)
        }
    }

    @Test fun `remove cap deletes every cap on the category only`() = runTest {
        val start = java.time.LocalDate.of(2026, 10, 1)
        budgets.items.value = listOf(
            Budget("a", "Food", Money(100, "LKR"), BudgetPeriod.MONTHLY, start, food.id),
            Budget("b", "Food again", Money(200, "LKR"), BudgetPeriod.MONTHLY, start, food.id),
            Budget("c", "Rent", Money(300, "LKR"), BudgetPeriod.MONTHLY, start, rent.id),
        )
        val model = model()
        assertEquals("a", model.cap(food)?.id)
        model.events.test {
            model.removeCap(food)
            assertEquals(AppResult.Success(Unit), awaitItem())
        }
        assertEquals(listOf("c"), budgets.items.value.map { it.id })
    }

    @Test fun `spending past a cap is saved, shown and warned about once`() = runTest {
        val model = model()
        assertNull(model.saveCap(food, "100"))
        // Caps never block saving: the expense is stored like any other (BR-05)
        transactions.create(expense("t", food.id, 12_000, java.time.Instant.now()))
        val usage = (model.capOverview.value as AppResult.Success).data.usages.getValue(food.id)
        assertEquals(Money(2_000, "LKR"), usage.overBy)
        assertEquals(1, transactions.rows.value.size)
        assertEquals(listOf(CapLevel.OVER), model.warnings.value.map { it.level })

        model.dismissWarning(model.warnings.value.single())
        transactions.create(expense("t2", food.id, 100, java.time.Instant.now()))
        assertTrue(model.warnings.value.isEmpty())
    }

    @Test fun `classification icon order and archive round trip and legacy data remains valid`() {
        val category = food.copy(isArchived = true, sortOrder = 5)
        assertEquals(category, categoryFromFirestore(category.id, category.toFirestore()))
        assertNull(categoryFromFirestore(food.id, food.toFirestore() - "classification").classification)
    }
}
