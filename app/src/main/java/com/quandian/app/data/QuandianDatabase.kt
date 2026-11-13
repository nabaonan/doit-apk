package com.quandian.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC, id ASC")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE parentId IS :parentId ORDER BY sortOrder ASC, id ASC")
    suspend fun childrenOf(parentId: Long?): List<TaskEntity>

    @Query("SELECT MIN(sortOrder) FROM tasks WHERE parentId IS :parentId")
    suspend fun minSortOf(parentId: Long?): Long?

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE tasks SET category = :category WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, category: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun all(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun delete(name: String)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface CompletionLogDao {
    @Query("SELECT * FROM completion_log ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<CompletionLogEntity>>

    @Query("SELECT * FROM completion_log WHERE completedAt >= :since ORDER BY completedAt ASC")
    suspend fun since(since: Long): List<CompletionLogEntity>

    @Insert
    suspend fun insert(log: CompletionLogEntity)

    @Insert
    suspend fun insertAll(logs: List<CompletionLogEntity>)

    @Query("DELETE FROM completion_log WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("DELETE FROM completion_log WHERE taskId IN (:taskIds)")
    suspend fun deleteForTasks(taskIds: List<Long>)
}

@Database(
    entities = [TaskEntity::class, CategoryEntity::class, CompletionLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class QuandianDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun logDao(): CompletionLogDao

    companion object {
        const val NAME = "quandian.db"
    }
}
