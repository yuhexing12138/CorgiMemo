package com.corgimemo.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Migration 31 → 32 单元测试
 *
 * 验证 sortOrder 按 zone 分段重算逻辑：
 * - PINNED_PENDING  (isPinned=1, status=0): 0      ~ 9999
 * - PENDING         (isPinned=0, status=0): 10000  ~ 19999
 * - PINNED_COMPLETED(isPinned=1, status=1): 20000  ~ 29999
 * - COMPLETED       (isPinned=0, status=1): 30000  ~ 39999
 *
 * **测试方案**:
 * - 使用 sqlite-jdbc 在 JVM 内存中创建真实 SQLite 数据库
 * - 创建 v31 schema 的 todo_items 表（已含 sortOrder 字段）
 * - 用 mockk 包装 SupportSQLiteDatabase，将 execSQL 转发到 sqlite-jdbc
 * - 通过反射获取 CorgiMemoDatabase 的 private MIGRATION_31_32 并调用 migrate()
 * - 直接查询 sqlite-jdbc 验证结果
 *
 * 这样测试的是源代码中实际的 Migration SQL，而非复制品。
 */
class MigrationTest {

    private lateinit var connection: Connection
    private lateinit var statement: java.sql.Statement
    private lateinit var db: SupportSQLiteDatabase

    /**
     * 测试前置：创建内存 SQLite 数据库，建立 v31 schema 的 todo_items 表
     *
     * v31 schema 已包含 sortOrder 字段（由 MIGRATION_30_31 添加）
     */
    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        statement = connection.createStatement()

        // 创建 v31 schema 的 todo_items 表（字段与 TodoItem Entity 对应）
        statement.executeUpdate("""
            CREATE TABLE todo_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                content TEXT,
                categoryId INTEGER NOT NULL,
                priority INTEGER NOT NULL,
                status INTEGER NOT NULL,
                startDate INTEGER,
                dueDate INTEGER,
                estimatedDurationMinutes INTEGER,
                reminderTime INTEGER,
                repeatType INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                completedAt INTEGER,
                geofenceLat REAL,
                geofenceLng REAL,
                geofenceRadius REAL,
                geofenceType INTEGER NOT NULL DEFAULT 0,
                geofenceEnabled INTEGER NOT NULL DEFAULT 0,
                geofenceAddress TEXT,
                hasSubTasks INTEGER NOT NULL DEFAULT 0,
                voiceNotePath TEXT,
                voiceDuration INTEGER,
                imagePaths TEXT NOT NULL DEFAULT '',
                backgroundColor INTEGER NOT NULL DEFAULT 16777215,
                contentFormat TEXT NOT NULL DEFAULT '',
                isPinned INTEGER NOT NULL DEFAULT 0,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        // mock SupportSQLiteDatabase：将 execSQL 转发到 sqlite-jdbc 执行
        db = mockk<SupportSQLiteDatabase>(relaxed = true)
        every { db.execSQL(any<String>()) } answers {
            statement.executeUpdate(firstArg())
        }
    }

    @After
    fun tearDown() {
        statement.close()
        connection.close()
    }

    /**
     * 通过反射获取 CorgiMemoDatabase.Companion 中私有的 MIGRATION_31_32
     *
     * 直接测试源代码中的 Migration 实例，而非 SQL 复制品，保证测试与实现一致。
     *
     * 反射查找策略（Kotlin companion object 的 private val 字段位置不确定）：
     * 1. 优先在 Companion 类中按精确名查找
     * 2. 回退到外部类 CorgiMemoDatabase 中查找（Kotlin 可能将 private 成员编译到外部类）
     * 3. 找不到时输出所有 MIGRATION_* 字段名便于诊断
     */
    private fun getMigration31To32(): Migration {
        val dbClass = CorgiMemoDatabase::class.java

        // 获取 Companion 实例
        val companionField = dbClass.getDeclaredField("Companion")
        companionField.isAccessible = true
        val companionInstance = companionField.get(null) ?: error("Companion 实例为 null")
        val companionClass = companionInstance::class.java

        // 收集两个位置的字段
        val companionMigrationFields = companionClass.declaredFields
            .filter { it.name.startsWith("MIGRATION_") }
        val outerMigrationFields = dbClass.declaredFields
            .filter { it.name.startsWith("MIGRATION_") }

        // 按精确名查找（优先 Companion 类，其次外部类）
        val (field, owner) = companionMigrationFields.firstOrNull { it.name == "MIGRATION_31_32" }
            ?.let { it to companionInstance }
            ?: outerMigrationFields.firstOrNull { it.name == "MIGRATION_31_32" }
                ?.let { it to null }  // 外部类静态字段，传 null
            ?: companionMigrationFields.firstOrNull { it.name.startsWith("MIGRATION_31") }
                ?.let { it to companionInstance }
            ?: outerMigrationFields.firstOrNull { it.name.startsWith("MIGRATION_31") }
                ?.let { it to null }
            ?: error(
                "未找到 MIGRATION_31_32 字段。" +
                    "Companion 类=${companionClass.name}, " +
                    "Companion 字段=${companionMigrationFields.map { it.name }}, " +
                    "外部类字段=${outerMigrationFields.map { it.name }}"
            )

        field.isAccessible = true
        return field.get(owner) as Migration
    }

    /**
     * 插入测试数据行
     *
     * @param id 主键
     * @param isPinned 是否置顶（0/1）
     * @param status 状态（0=PENDING, 1=COMPLETED）
     * @param createdAt 创建时间戳
     * @param sortOrder 初始 sortOrder（默认 0，迁移前会被覆盖）
     */
    private fun insertTodo(
        id: Long,
        isPinned: Int,
        status: Int,
        createdAt: Long,
        sortOrder: Int = 0
    ) {
        statement.executeUpdate("""
            INSERT INTO todo_items (id, title, content, categoryId, priority, status,
                startDate, dueDate, estimatedDurationMinutes, reminderTime, repeatType,
                createdAt, updatedAt, completedAt, geofenceLat, geofenceLng, geofenceRadius,
                geofenceType, geofenceEnabled, geofenceAddress, hasSubTasks, voiceNotePath,
                voiceDuration, imagePaths, backgroundColor, contentFormat, isPinned, sortOrder)
            VALUES ($id, 'test$id', NULL, 1, 0, $status,
                NULL, NULL, NULL, NULL, 0,
                $createdAt, 0, NULL, NULL, NULL, NULL,
                0, 0, NULL, 0, NULL,
                NULL, '', 16777215, '', $isPinned, $sortOrder)
        """.trimIndent())
    }

    /**
     * 查询指定 id 的 sortOrder
     */
    private fun querySortOrder(id: Long): Int {
        var result = -1
        statement.executeQuery("SELECT sortOrder FROM todo_items WHERE id = $id").use { rs: ResultSet ->
            if (rs.next()) {
                result = rs.getInt("sortOrder")
            }
        }
        return result
    }

    /**
     * 测试 1：迁移后 sortOrder 按 zone 分段重算
     *
     * 验证四个 zone 的 sortOrder 落在对应区段：
     * - PINNED_PENDING: [0, 9999]
     * - PENDING: [10000, 19999]
     * - PINNED_COMPLETED: [20000, 29999]
     * - COMPLETED: [30000, 39999]
     */
    @Test
    fun migration_31_to_32_reassigns_sortOrder_by_zone() {
        // 准备：每个 zone 插入 2 条数据（COMPLETED 除外）
        insertTodo(id = 1, isPinned = 1, status = 0, createdAt = 100)  // PINNED_PENDING
        insertTodo(id = 2, isPinned = 1, status = 0, createdAt = 200)  // PINNED_PENDING
        insertTodo(id = 3, isPinned = 0, status = 0, createdAt = 100)  // PENDING
        insertTodo(id = 4, isPinned = 0, status = 0, createdAt = 200)  // PENDING
        insertTodo(id = 5, isPinned = 1, status = 1, createdAt = 100)  // PINNED_COMPLETED
        insertTodo(id = 6, isPinned = 1, status = 1, createdAt = 200)  // PINNED_COMPLETED
        insertTodo(id = 7, isPinned = 0, status = 1, createdAt = 100)  // COMPLETED
        insertTodo(id = 8, isPinned = 0, status = 1, createdAt = 200)  // COMPLETED

        // 执行迁移
        getMigration31To32().migrate(db)

        // 验证：PINNED_PENDING 落在 [0, 9999]
        val pp1 = querySortOrder(1)
        val pp2 = querySortOrder(2)
        assertTrue("PINNED_PENDING id=1 sortOrder=$pp1 应在 [0,9999]", pp1 in 0..9999)
        assertTrue("PINNED_PENDING id=2 sortOrder=$pp2 应在 [0,9999]", pp2 in 0..9999)

        // 验证：PENDING 落在 [10000, 19999]
        val p1 = querySortOrder(3)
        val p2 = querySortOrder(4)
        assertTrue("PENDING id=3 sortOrder=$p1 应在 [10000,19999]", p1 in 10000..19999)
        assertTrue("PENDING id=4 sortOrder=$p2 应在 [10000,19999]", p2 in 10000..19999)

        // 验证：PINNED_COMPLETED 落在 [20000, 29999]
        val pc1 = querySortOrder(5)
        val pc2 = querySortOrder(6)
        assertTrue("PINNED_COMPLETED id=5 sortOrder=$pc1 应在 [20000,29999]", pc1 in 20000..29999)
        assertTrue("PINNED_COMPLETED id=6 sortOrder=$pc2 应在 [20000,29999]", pc2 in 20000..29999)

        // 验证：COMPLETED 落在 [30000, 39999]
        val c1 = querySortOrder(7)
        val c2 = querySortOrder(8)
        assertTrue("COMPLETED id=7 sortOrder=$c1 应在 [30000,39999]", c1 in 30000..39999)
        assertTrue("COMPLETED id=8 sortOrder=$c2 应在 [30000,39999]", c2 in 30000..39999)
    }

    /**
     * 测试 2：同一 zone 内按 createdAt ASC 排序保持相对顺序
     *
     * 验证 createdAt 较小的项获得较小的 zone 内偏移量（0, 1, 2, ...）
     */
    @Test
    fun migration_preserves_relative_order_within_zone() {
        // 准备：PENDING zone 插入 3 条，createdAt 递增
        insertTodo(id = 10, isPinned = 0, status = 0, createdAt = 300)  // 最早
        insertTodo(id = 11, isPinned = 0, status = 0, createdAt = 200)  // 中间
        insertTodo(id = 12, isPinned = 0, status = 0, createdAt = 100)  // 最晚（但最早创建）

        // 执行迁移
        getMigration31To32().migrate(db)

        // 验证：按 createdAt ASC，id=12 (100) -> 10000, id=11 (200) -> 10001, id=10 (300) -> 10002
        assertEquals("createdAt=100 应得 sortOrder=10000", 10000, querySortOrder(12))
        assertEquals("createdAt=200 应得 sortOrder=10001", 10001, querySortOrder(11))
        assertEquals("createdAt=300 应得 sortOrder=10002", 10002, querySortOrder(10))

        // 准备：PINNED_PENDING zone 同样验证
        insertTodo(id = 20, isPinned = 1, status = 0, createdAt = 50)
        insertTodo(id = 21, isPinned = 1, status = 0, createdAt = 10)

        // 执行迁移（重算）
        getMigration31To32().migrate(db)

        // 验证：createdAt=10 -> 0, createdAt=50 -> 1
        assertEquals("PINNED_PENDING createdAt=10 应得 sortOrder=0", 0, querySortOrder(21))
        assertEquals("PINNED_PENDING createdAt=50 应得 sortOrder=1", 1, querySortOrder(20))
    }

    /**
     * 测试 3：空 zone 场景
     *
     * 验证当某些 zone 没有数据时，迁移不会报错，且非空 zone 仍正确分段。
     * 空 zone 不应产生 sortOrder=-1 的脏数据（因为 UPDATE 的 WHERE 子句过滤了空集）。
     */
    @Test
    fun migration_handles_empty_zones() {
        // 准备：只插入 PINNED_PENDING 和 COMPLETED，留空 PENDING 和 PINNED_COMPLETED
        insertTodo(id = 30, isPinned = 1, status = 0, createdAt = 100)  // PINNED_PENDING
        insertTodo(id = 31, isPinned = 1, status = 0, createdAt = 200)  // PINNED_PENDING
        insertTodo(id = 32, isPinned = 0, status = 1, createdAt = 500)  // COMPLETED（唯一）

        // 执行迁移
        getMigration31To32().migrate(db)

        // 验证：PINNED_PENDING 正常分段
        assertEquals("PINNED_PENDING createdAt=100 应得 sortOrder=0", 0, querySortOrder(30))
        assertEquals("PINNED_PENDING createdAt=200 应得 sortOrder=1", 1, querySortOrder(31))

        // 验证：COMPLETED 唯一一条应得 30000
        assertEquals("COMPLETED 唯一一条应得 sortOrder=30000", 30000, querySortOrder(32))

        // 验证：空 zone 没有产生 sortOrder=-1 的脏数据行
        // （表中只有 3 行，都已验证，不存在多出来的行）
        statement.executeQuery("SELECT COUNT(*) AS cnt FROM todo_items WHERE sortOrder < 0").use { rs ->
            assertTrue("不应存在 sortOrder < 0 的行", rs.next())
            assertEquals("sortOrder < 0 的行数应为 0", 0, rs.getInt("cnt"))
        }
    }
}
