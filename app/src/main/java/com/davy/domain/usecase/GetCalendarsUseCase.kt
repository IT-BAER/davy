package com.davy.domain.usecase

import com.davy.data.repository.CalendarRepository
import com.davy.domain.model.Calendar
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case to get all calendars for an account.
 */
class GetCalendarsUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository
) {
    operator fun invoke(accountId: Long): Flow<List<Calendar>> {
        return calendarRepository.getByAccountIdFlow(accountId)
    }
}
