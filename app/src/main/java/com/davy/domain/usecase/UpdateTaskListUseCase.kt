package com.davy.domain.usecase

import com.davy.data.repository.TaskListRepository
import com.davy.domain.model.TaskList
import javax.inject.Inject

/**
 * Use case for updating task list settings.
 * 
 * Updates task list properties such as sync enabled, visibility, etc.
 */
class UpdateTaskListUseCase @Inject constructor(
    private val taskListRepository: TaskListRepository
) {
    
    /**
     * Update a task list.
     * 
     * @param taskList The task list with updated properties
     */
    suspend operator fun invoke(taskList: TaskList) {
        taskListRepository.update(taskList)
    }
}
