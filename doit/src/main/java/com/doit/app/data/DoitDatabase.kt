package com.doit.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    /** 业务查询一律排除墓碑，界面永远看不到已删除的记录 */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    suspend fun all(): List<TaskEntity>

    /** 含墓碑：判断「本地与远程是否一致」也要看墓碑，删除记录要随快照传给另一台设备 */
    @Query("SELECT * FROM tasks ORDER BY id ASC")
    fun observeAllIncludingDeleted(): Flow<List<TaskEntity>>

    /** 含墓碑：只有同步快照需要，删除记录要随快照传给另一台设备 */
    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun allIncludingDeleted(): List<TaskEntity>

    /** 月视图用：所有已完成的待办（带完成时间），按完成时间倒序 */
    @Query("SELECT * FROM tasks WHERE done = 1 AND deletedAt IS NULL AND completedAt IS NOT NULL ORDER BY completedAt DESC")
    fun observeDone(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id AND deletedAt IS NULL")
    suspend fun byId(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE uid = :uid LIMIT 1")
    suspend fun byUid(uid: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE parentId IS :parentId AND deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    suspend fun childrenOf(parentId: Long?): List<TaskEntity>

    @Query("SELECT MIN(sortOrder) FROM tasks WHERE parentId IS :parentId AND deletedAt IS NULL")
    suspend fun minSortOf(parentId: Long?): Long?

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Update
    suspend fun update(task: TaskEntity)

    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET parentId = :parentId WHERE id = :id")
    suspend fun setParentId(id: Long, parentId: Long?)

    @Query("UPDATE tasks SET uid = :uid WHERE id = :id")
    suspend fun setUid(id: Long, uid: String)

    /** 改分类也是一次编辑，updatedAt 必须跟着走，否则同步会认为这条没变过 */
    @Query("UPDATE tasks SET category = :category, updatedAt = :now WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun setCategory(ids: List<Long>, category: String, now: Long)

    /** 同步落地时物理清理；日常删除走软删，别直接调它 */
    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT id FROM tasks WHERE uid = '' OR updatedAt = 0 LIMIT 200")
    suspend fun rowsMissingSyncMeta(): List<Long>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, name ASC")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAllIncludingDeleted(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun allIncludingDeleted(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun delete(name: String)

    @Query("SELECT COUNT(*) FROM categories WHERE deletedAt IS NULL")
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
    version = 2,
    exportSchema = true,
)
abstract class DoitDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun logDao(): CompletionLogDao

    companion object {
        const val NAME = "doit.db"

        /**
         * v1 → v2：补齐同步元信息。
         * 老数据没有 uid，用 randomblob 现场生成全局唯一值；updatedAt 退化成 createdAt
         * （老记录最后一次编辑时间已不可考，合并时按「时间戳较旧」处理，不会被误判为新改动）。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE tasks SET uid = lower(hex(randomblob(16))) WHERE uid = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tasks_uid ON tasks (uid)")
                db.execSQL("ALTER TABLE tasks ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER")
                db.execSQL("UPDATE tasks SET updatedAt = createdAt WHERE updatedAt = 0")

                db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN deletedAt INTEGER")
            }
        }
    }
}
