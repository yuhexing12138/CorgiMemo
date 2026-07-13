package com.corgimemo.app.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration 34 → 35 测试
 *
 * 验证:
 * 1. 老用户 34 → 35 升级后,所有现有记录 cardStyle = "ORANGE_TEAR_OFF"
 * 2. 升级后的表结构可正常读写
 */
@RunWith(AndroidJUnit4::class)
class Migration34to35Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CorgiMemoDatabase::class.java,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate_34_to_35_existingRows_getDefaultCardStyle() {
        // 创建版本 34 的数据库
        helper.createDatabase(TEST_DB_NAME, 34).apply {
            execSQL("""
                INSERT INTO special_dates (id, title, targetDate, category, countMode, repeatType,
                    reminderDays, content, tags, imagePaths, imageUrls, isPinned, isArchived,
                    createdAt, updatedAt)
                VALUES (1, '测试', 1721260800000, 'OTHER', 0, 0, 0, '', '', '', '', 0, 0,
                    1721260800000, 1721260800000)
            """.trimIndent())
            close()
        }

        // 升级到 35
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 35, true, CorgiMemoDatabase.MIGRATION_34_35)

        // 验证 cardStyle 字段存在且默认为 ORANGE_TEAR_OFF
        val cursor = db.query("SELECT cardStyle FROM special_dates WHERE id = 1")
        assert(cursor.moveToFirst())
        val cardStyle = cursor.getString(0)
        assert(cardStyle == "ORANGE_TEAR_OFF") { "Expected ORANGE_TEAR_OFF, got $cardStyle" }
        cursor.close()

        db.close()
    }

    @Test
    fun migrate_34_to_35_tableSchema_hasCardStyleColumn() {
        helper.createDatabase(TEST_DB_NAME, 34).close()
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 35, true, CorgiMemoDatabase.MIGRATION_34_35)

        val cursor = db.query("PRAGMA table_info(special_dates)")
        var hasCardStyle = false
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (name == "cardStyle") {
                hasCardStyle = true
                break
            }
        }
        cursor.close()
        assert(hasCardStyle) { "cardStyle 列不存在" }

        db.close()
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test.db"
    }
}
