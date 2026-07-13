package com.corgimemo.app.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration 35 → 36 测试
 *
 * 验证:
 * 1. 老用户 35 → 36 升级后,所有现有记录 cardColor = "DEFAULT"
 * 2. 升级后的表结构包含 cardColor 列
 * 3. 升级后写入 cardColor='BLUE' 正确持久化
 */
@RunWith(AndroidJUnit4::class)
class Migration35to36Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CorgiMemoDatabase::class.java,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate_35_to_36_existingRows_getDefaultCardColor() {
        // 创建版本 35 的数据库(包含 cardStyle='ORANGE_TEAR_OFF' 模拟已有数据)
        helper.createDatabase(TEST_DB_NAME, 35).apply {
            execSQL("""
                INSERT INTO special_dates (id, title, targetDate, category, countMode, repeatType,
                    reminderDays, content, tags, imagePaths, imageUrls, isPinned, isArchived,
                    cardStyle, createdAt, updatedAt)
                VALUES (1, '测试', 1721260800000, 'OTHER', 0, 0, 0, '', '', '', '', 0, 0,
                    'ORANGE_TEAR_OFF', 1721260800000, 1721260800000)
            """.trimIndent())
            close()
        }

        // 升级到 36
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 36, true, CorgiMemoDatabase.MIGRATION_35_36)

        // 验证 cardColor 字段存在且默认为 DEFAULT
        val cursor = db.query("SELECT cardColor FROM special_dates WHERE id = 1")
        assert(cursor.moveToFirst())
        val cardColor = cursor.getString(0)
        assert(cardColor == "DEFAULT") { "Expected DEFAULT, got $cardColor" }
        cursor.close()

        // 同时验证 cardStyle 字段未被破坏
        val cursor2 = db.query("SELECT cardStyle FROM special_dates WHERE id = 1")
        assert(cursor2.moveToFirst())
        val cardStyle = cursor2.getString(0)
        assert(cardStyle == "ORANGE_TEAR_OFF") { "Expected ORANGE_TEAR_OFF (unchanged), got $cardStyle" }
        cursor2.close()

        db.close()
    }

    @Test
    fun migrate_35_to_36_tableSchema_hasCardColorColumn() {
        helper.createDatabase(TEST_DB_NAME, 35).close()
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 36, true, CorgiMemoDatabase.MIGRATION_35_36)

        val cursor = db.query("PRAGMA table_info(special_dates)")
        var hasCardColor = false
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (name == "cardColor") {
                hasCardColor = true
                break
            }
        }
        cursor.close()
        assert(hasCardColor) { "cardColor 列不存在" }

        db.close()
    }

    @Test
    fun migrate_35_to_36_writeAndReadNonDefaultCardColor() {
        // 1. 从 35 起一个空库
        helper.createDatabase(TEST_DB_NAME, 35).close()
        // 2. 升级到 36
        val db = helper.runMigrationsAndValidate(TEST_DB_NAME, 36, true, CorgiMemoDatabase.MIGRATION_35_36)
        // 3. 写入一个 cardColor='BLUE' 的记录
        db.execSQL("""
            INSERT INTO special_dates (title, targetDate, category, countMode, repeatType,
                reminderDays, content, tags, imagePaths, imageUrls, isPinned, isArchived,
                cardStyle, cardColor, createdAt, updatedAt)
            VALUES ('生日', 1721260800000, 'BIRTHDAY', 0, 0, 0, '', '', '', '', 0, 0,
                'CALENDAR_TEAR_OFF', 'BLUE', 1721260800000, 1721260800000)
        """.trimIndent())
        // 4. 读回
        val cursor = db.query("SELECT cardColor FROM special_dates WHERE title = '生日'")
        assert(cursor.moveToFirst())
        val cardColor = cursor.getString(0)
        assert(cardColor == "BLUE") { "Expected BLUE, got $cardColor" }
        cursor.close()
        db.close()
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test-35-36.db"
    }
}
