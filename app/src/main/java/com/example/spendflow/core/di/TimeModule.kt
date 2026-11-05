package com.example.spendflow.core.di

import com.example.spendflow.domain.usecase.ClockCurrentMonth
import com.example.spendflow.domain.usecase.CurrentMonth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun provideCurrentMonth(impl: ClockCurrentMonth): CurrentMonth = impl
}
