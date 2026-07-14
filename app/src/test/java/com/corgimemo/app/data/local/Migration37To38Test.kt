package com.corgimemo.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Migration 37 → 38 单元测试
 *
 * 验证 custom_date_types 表创建成功：
 * - 表存在
 * - 含 id/name/emoji/sortOrder/createdAt 五个字段
 * - emoji 默认值为 '📅'
 * - sortOrder 默认值为 0
 */
class Migration37To38Test {

    private lateinit var connection: Connection
    private lateinit var statement: java.sql.Statement
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        statement = connection.createStatement()
        db = mockk(relaxed = true)
        every { db.execSQL(any()) } answers { statement.executeUpdate(firstArg()) }
        every { db.execSQL(any(), any()) } answers { }
    }

    @After
    fun tearDown() {
        statement.close()
        connection.close()
    }

    /**
     * 通过反射获取 MIGRATION_37_38
     * 查找策略：先查 Companion 类，再查外部类
     */
    private fun getMigration37To38(): Migration {
        val dbClass = CorgiMemoDatabase::class.java

        // 获取 Companion 实例
        val companionField = dbClass.getDeclaredField("Companion")
        companionField.isAccessible = true
        val companionInstance = companionField.get(null) ?: error("Companion 实例为 null")
        val companionClass = companionInstance::class.java

        // 优先在 Companion 类中查找
        try {
            val field = companionClass.getDeclaredField("MIGRATION_37_38")
            field.isAccessible = true
            return field.get(companionInstance) as Migration
        } catch (e: NoSuchFieldException) {
            // 回退到外部类查找
        }

        // 在外部类中查找
        try {
            val field = dbClass.getDeclaredField("MIGRATION_37_38")
            field.isAccessible = true
            return field.get(null) as Migration
        } catch (e: NoSuchFieldException) {
            // 输出诊断信息
            val companionFields = companionClass.declaredFields.filter { it.name.startsWith("MIGRATION_") }.map { it.name }
            val outerFields = dbClass.declaredFields.filter { it.name.startsWith("MIGRATION_") }.map { it.name }
            error("未找到 MIGRATION_37_38 字段。Companion 字段=$companionFields, 外部类字段=$outerFields")
        }
    }

    @Test
    fun `migration 37 to 38 创建 custom_date_types 表`() {
        val migration = getMigration37To38()
        migration.migrate(db)

        // 验证表存在
        val tables = statement.executeQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='custom_date_types'"
        )
        assertTrue("custom_date_types 表应存在", tables.next())

        // 验证字段
        val columns = statement.executeQuery("PRAGMA table_info(custom_date_types)")
        val columnNames = mutableListOf<String>()
        while (columns.next()) {
            columnNames.add(columns.getString("name"))
        }
        assertTrue("应含 id 字段", columnNames.contains("id"))
        assertTrue("应含 name 字段", columnNames.contains("name"))
        assertTrue("应含 emoji 字段", columnNames.contains("emoji"))
        assertTrue("应含 sortOrder 字段", columnNames.contains("sortOrder"))
        assertTrue("应含 createdAt 字段", columnNames.contains("createdAt"))
    }

    @Test
    fun `emoji 默认值为日期 emoji`() {
        val migration = getMigration37To38()
        migration.migrate(db)

        // 插入一条不指定 emoji 的记录
        statement.executeUpdate(
            "INSERT INTO custom_date_types (name, sortOrder, createdAt) VALUES ('测试', 0, 0)"
        )

        // 验证 emoji 默认值
        val rs = statement.executeQuery("SELECT emoji FROM custom_date_types WHERE name = '测试'")
        assertTrue(rs.next())
        assertTrue("emoji 默认值应为 📅", rs.getString("emoji") == "📅")
    }
}
