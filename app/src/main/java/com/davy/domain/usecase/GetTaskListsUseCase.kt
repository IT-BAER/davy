package com.davy.domain.usecase

import com.davy.data.repository.TaskListRepository
import com.davy.domain.model.TaskList
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for retrieving task lists for an account.
 */
class GetTaskListsUseCase @Inject constructor(
    private val taskListRepository: TaskListRepository
) {
    
    /**
     * Get all task lists for an account.
     * 
     * @param accountId The account ID
     * @return List of task lists
     */
    suspend operator fun invoke(accountId: Long): List<TaskList> {
        return taskListRepository.getByAccountId(accountId).first()
    }
}
