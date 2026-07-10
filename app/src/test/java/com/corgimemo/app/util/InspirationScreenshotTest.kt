// app/src/test/java/com/corgimemo/app/util/InspirationScreenshotTest.kt
package com.corgimemo.app.util

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 灵感截图工具单元测试
 *
 * 真正的 saveToGallery 需要 Android Context（instrumented 测试），
 * 这里仅验证工具类可加载（防止被错误删除或重命名）。
 */
class InspirationScreenshotTest {

    /**
     * 烟雾测试：工具类 object 能正常加载
     */
    @Test
    fun `InspirationScreenshot object 可加载`() {
        // 当
        val instance = InspirationScreenshot

        // 那么：实例非空
        assertNotNull(instance)
    }
}
