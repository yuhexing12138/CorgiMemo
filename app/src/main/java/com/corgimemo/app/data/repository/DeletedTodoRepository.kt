package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.DeletedTodoDao
import com.corgimemo.app.data.model.DeletedTodo
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletedTodoRepository @Inject constructor(
    private val deletedTodoDao: DeletedTodoDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun insertDeletedTodo(todo: TodoItem) = withContext(ioDispatcher) {
        deletedTodoDao.insert(DeletedTodo.fromTodoItem(todo))
    }

    suspend fun insertDeletedTodos(todos: List<TodoItem>) = withContext(ioDispatcher) {
        deletedTodoDao.insertAll(todos.map { DeletedTodo.fromTodoItem(it) })
    }

    fun getAllDeletedTodos(): Flow<List<DeletedTodo>> = deletedTodoDao.getAllDeletedTodos()

    suspend fun getAllDeletedTodosBlocking(): List<DeletedTodo> = withContext(ioDispatcher) {
        deletedTodoDao.getAllDeletedTodosBlocking()
    }

    fun getDeletedCount(): Flow<Int> = deletedTodoDao.getDeletedCount()

    suspend fun getDeletedCountBlocking(): Int = withContext(ioDispatcher) {
        deletedTodoDao.getDeletedCountBlocking()
    }

    suspend fun restoreDeletedTodo(todoId: Long): DeletedTodo? = withContext(ioDispatcher) {
        deletedTodoDao.getDeletedTodoById(todoId)
    }

    suspend fun permanentlyDelete(todoId: Long) = withContext(ioDispatcher) {
        deletedTodoDao.deleteById(todoId)
    }

    suspend fun permanentlyDeleteAll() = withContext(ioDispatcher) {
        deletedTodoDao.deleteAll()
    }

    suspend fun cleanUpOldDeletedTodos(threshold: Long) = withContext(ioDispatcher) {
        deletedTodoDao.deleteOlderThan(threshold)
    }
}