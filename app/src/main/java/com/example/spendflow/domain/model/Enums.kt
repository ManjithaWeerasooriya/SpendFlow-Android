package com.example.spendflow.domain.model

enum class AccountType { CASH, BANK, CREDIT_CARD, SAVINGS, OTHER }

enum class CategoryType { EXPENSE, INCOME }

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

enum class BudgetPeriod { WEEKLY, MONTHLY, YEARLY }

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
