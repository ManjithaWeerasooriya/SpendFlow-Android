package com.example.spendflow.core.di

import com.example.spendflow.data.repository.AccountRepository
import com.example.spendflow.data.repository.AuthRepository
import com.example.spendflow.data.repository.BudgetRepository
import com.example.spendflow.data.repository.CategoryRepository
import com.example.spendflow.data.repository.FirebaseAuthRepository
import com.example.spendflow.data.repository.FirestoreAccountRepository
import com.example.spendflow.data.repository.FirestoreBudgetRepository
import com.example.spendflow.data.repository.FirestoreCategoryRepository
import com.example.spendflow.data.repository.FirestoreRecurringTransactionRepository
import com.example.spendflow.data.repository.FirestoreTransactionRepository
import com.example.spendflow.data.repository.FirestoreUserRepository
import com.example.spendflow.data.repository.RecurringTransactionRepository
import com.example.spendflow.data.repository.TransactionRepository
import com.example.spendflow.data.repository.UserRepository
import com.example.spendflow.data.repository.WriteFailureReporter
import com.example.spendflow.data.repository.WriteFailures
import com.example.spendflow.data.session.FirebaseUserSession
import com.example.spendflow.data.session.UserSession
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindExchangeRates(impl: com.example.spendflow.data.repository.ApiExchangeRates): com.example.spendflow.data.repository.ExchangeRates
    @Binds abstract fun bindUserSession(impl: FirebaseUserSession): UserSession
    @Binds abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
    @Binds abstract fun bindWriteFailures(impl: WriteFailureReporter): WriteFailures
    @Binds abstract fun bindUserRepository(impl: FirestoreUserRepository): UserRepository
    @Binds abstract fun bindAccountRepository(impl: FirestoreAccountRepository): AccountRepository
    @Binds abstract fun bindCategoryRepository(impl: FirestoreCategoryRepository): CategoryRepository
    @Binds abstract fun bindTransactionRepository(impl: FirestoreTransactionRepository): TransactionRepository
    @Binds abstract fun bindBudgetRepository(impl: FirestoreBudgetRepository): BudgetRepository

    @Binds
    abstract fun bindRecurringTransactionRepository(
        impl: FirestoreRecurringTransactionRepository,
    ): RecurringTransactionRepository
}
