package com.corgimemo.app.data.seed

import android.util.Log
import com.corgimemo.app.data.local.db.InspirationDao
import com.corgimemo.app.data.model.Inspiration
import org.json.JSONArray

/**
 * 灵感种子数据注入器
 *
 * 负责 7 条灵感（I1-I7）的数据库注入。
 * 数据内容取材于参赛贴的实际开发过程，覆盖：
 * - 0-5 个不重复标签（23 个唯一标签）
 * - 过去/当前/未来创建时间
 * - 1-5 张图片附件
 * - content 字数梯度：10 → 30 → 80 → 200 → 400 → 700 → 1000
 * - 每条灵感同步设置 contentFormat（纯文本作为合法 Markdown），确保编辑页可正常显示
 * - I7 为诗意文案，语言优美富有意境
 *
 * @param inspirationDao 灵感 DAO
 */
class InspirationSeedData(
    private val inspirationDao: InspirationDao
) {
    private val tag = "DemoSeeder"

    /**
     * 注入灵感种子数据
     *
     * @param categoryIds 分类 ID 映射（type → categoryId）
     * @param imagePaths 图片路径映射（数据编号 → 路径列表）
     * @return 灵感 ID 映射（编号 I1-I7 → inspirationId）
     */
    suspend fun seed(
        categoryIds: Map<Int, Long>,
        imagePaths: Map<String, List<String>>
    ): Map<String, Long> {
        val inspirationIds = mutableMapOf<String, Long>()

        // ========== I1：柯基陪伴初心（约 10 字） ==========
        val i1Content = "柯基相伴，记录温暖每一刻。"
        val i1 = Inspiration(
            title = "柯基陪伴初心",
            content = i1Content,
            contentFormat = i1Content,
            tags = toJsonArray(listOf("灵感", "陪伴")),
            imagePaths = toJsonArray(imagePaths["I1"] ?: emptyList()),
            createdAt = timeStamp(2026, 6, 15, 20, 0),
            updatedAt = timeStamp(2026, 6, 15, 20, 0),
            isPinned = true,
            categoryId = categoryIds[1]!!, // 工作
            priority = 3, // 高
            status = 0 // 进行中
        )
        inspirationIds["I1"] = inspirationDao.insert(i1)

        // ========== I2：拖拽排序的思考（约 30 字） ==========
        val i2Content = "让用户拖动卡片就能排序，像整理桌上的便签一样自然。简单直觉，最好用。"
        val i2 = Inspiration(
            title = "拖拽排序的思考",
            content = i2Content,
            contentFormat = i2Content,
            tags = toJsonArray(listOf("交互", "拖拽")),
            imagePaths = toJsonArray(imagePaths["I2"] ?: emptyList()),
            createdAt = timeStamp(2026, 6, 25, 22, 0),
            updatedAt = timeStamp(2026, 6, 25, 22, 0),
            isPinned = false,
            categoryId = categoryIds[0]!!, // 学习
            priority = 2, // 中
            status = 1 // 已完成
        )
        inspirationIds["I2"] = inspirationDao.insert(i2)

        // ========== I3：跨模块关联的灵感（约 80 字） ==========
        val i3Content = "待办、灵感、日期三个模块之间应该能互相链接。比如一个灵感可以关联到某个待办，一个待办可以关联到某个日期。就像把便签用线连起来，形成一个完整的思路网络，不再孤立存在。"
        val i3 = Inspiration(
            title = "跨模块关联的灵感",
            content = i3Content,
            contentFormat = i3Content,
            tags = toJsonArray(listOf("关联", "设计", "思路")),
            imagePaths = toJsonArray(imagePaths["I3"] ?: emptyList()),
            createdAt = timeStamp(2026, 7, 14, 21, 40),
            updatedAt = timeStamp(2026, 7, 14, 21, 40),
            isPinned = false,
            categoryId = categoryIds[0]!!, // 学习
            priority = 2, // 中
            status = 0 // 进行中
        )
        inspirationIds["I3"] = inspirationDao.insert(i3)

        // ========== I4：统一提示条方案（约 200 字） ==========
        val i4Content = "之前用系统的 Toast 做提示，风格不统一，有时显示位置不一样，有时样式也不一样。后来想到做一个自己的提示条组件，左边是柯基小头像，中间是文字，整体是圆角背景。所有需要提示用户的地方都走这一个组件，视觉上保持一致，体验也更舒服。这样做的好处是，改一处全局生效，维护成本低，还能加一些小动画让提示更有趣。用户看到统一的提示风格，会觉得这个 app 做得很用心，细节到位。"
        val i4 = Inspiration(
            title = "统一提示条方案",
            content = i4Content,
            contentFormat = i4Content,
            tags = toJsonArray(listOf("UI", "提示", "统一")),
            imagePaths = toJsonArray(imagePaths["I4"] ?: emptyList()),
            createdAt = timeStamp(2026, 7, 14, 16, 15),
            updatedAt = timeStamp(2026, 7, 14, 16, 15),
            isPinned = false,
            categoryId = categoryIds[1]!!, // 工作
            priority = 2, // 中
            status = 1 // 已完成
        )
        inspirationIds["I4"] = inspirationDao.insert(i4)

        // ========== I5：演示数据的设计思路（约 400 字） ==========
        val i5Content = "做演示数据不能随便编，要像一个真实用户的使用场景。这次以参赛过程本身为主题，把开发中遇到的真实任务、灵感和重要日期都做成数据。比如开发数据库时遇到的架构设计变成了一个待办，想到的柯基陪伴方案变成了灵感，参赛截止日变成了日期记录。这样数据之间自然有关联：灵感为待办提供思路，日期记录待办的截止时间。用户看到这些数据，就像看到一个人在真实使用这个 app，而不是一堆假数据拼在一起。\n\n为了让数据更真实，还加了不同优先级、不同状态、不同时间的分布。有的待办已完成，有的还在进行；有的灵感是几天前写的，有的是刚想到的。这样展示出来才有生活的气息，才能让人感受到 app 在日常使用中的样子。每一张图片、每一段语音都是精心安排的，让演示效果更丰富。"
        val i5 = Inspiration(
            title = "演示数据的设计思路",
            content = i5Content,
            contentFormat = i5Content,
            tags = toJsonArray(listOf("产品", "演示", "数据", "真实", "参赛")),
            imagePaths = toJsonArray(imagePaths["I5"] ?: emptyList()),
            createdAt = timeStamp(2026, 7, 15, 9, 0),
            updatedAt = timeStamp(2026, 7, 15, 9, 0),
            isPinned = true,
            categoryId = categoryIds[1]!!, // 工作
            priority = 3, // 高
            status = 0 // 进行中
        )
        inspirationIds["I5"] = inspirationDao.insert(i5)

        // ========== I6：参赛贴的写作策略（约 700 字） ==========
        val i6Content = "写参赛贴不是列功能清单，而是讲故事。评委看那么多作品，干巴巴的功能列表根本记不住。但如果是一个有起承转合的故事，就完全不一样了。这次打算从灵感来源讲起：为什么想做这个 app？因为发现市面上的待办工具都太冷冰冰了，缺少温度。然后讲遇到了什么问题：功能越做越多，代码越来越复杂，怎么管理？接着讲怎么用工具解决：用了 AI 辅助编程，从架构设计到细节实现，一步步把想法变成现实。最后讲收获了什么：不只是做了一个 app，更学会了怎么把一个大目标拆成小任务，怎么在开发中保持质量。\n\n整个叙事保留真实的开发过程记录，包括每一次对话的编号，让评委能看到这是真的在用工具协作，而不是事后编的。另外，文字要通俗，不要堆术语。技术细节该有的地方有，但要让非技术背景的人也能看懂你在做什么、为什么这么做。比如不说'实现了状态机'，而说'做了一个拖拽排序的系统，让卡片不会跳来跳去'。这样读者更容易产生共鸣。\n\n图片也要配好，关键功能都要有截图，让文字和图片互相补充。一段文字配一张图，比纯文字效果好得多。最后检查格式，确保排版整齐，不要有的地方字大有的地方字小，看起来不专业。写完之后放一放，第二天再回头看，往往能发现昨天没注意到的问题。好文章是改出来的，不是一遍写成的。"
        val i6 = Inspiration(
            title = "参赛贴的写作策略",
            content = i6Content,
            contentFormat = i6Content,
            tags = toJsonArray(listOf("写作", "叙事", "策略", "表达")),
            imagePaths = toJsonArray(imagePaths["I6"] ?: emptyList()),
            createdAt = timeStamp(2026, 7, 14, 19, 25),
            updatedAt = timeStamp(2026, 7, 14, 19, 25),
            isPinned = false,
            categoryId = categoryIds[2]!!, // 生活
            priority = 1, // 低
            status = 0 // 进行中
        )
        inspirationIds["I6"] = inspirationDao.insert(i6)

        // ========== I7：时间与记忆的诗（约 1000 字，诗意文案） ==========
        val i7Content = """时间像一条安静的河，从不停歇地向前流去。我们想抓住它，却只能看着它从指缝间滑落。于是人类发明了日历，把流动的时间切成一片一片，标记上生日、纪念日、节日，让某些日子变得与众不同。

刻记+就是这样一个容器，用来盛放那些不想被时间冲走的瞬间。你记下一个生日，它就每年提醒你；你写下一个灵感，它就安静地等在那里，直到你回头看它；你列下一个待办，它就陪你一步步完成。

柯基是这个容器里的小精灵。它不说话，只是安静地陪着你。你完成任务时，它摇摇尾巴；你写灵感时，它趴在旁边打盹；你翻看日期时，它歪着头看你。它不会催促你，也不会评判你，只是让你知道，在这个屏幕的另一端，有一个小小的生命在陪着记录你的每一天。

有时候觉得，记录这件事本身就有意义。不一定是为了完成什么目标，而是为了在未来的某一天，翻开这些文字和图片，能想起当时的自己。那个在深夜写代码的自己，那个突然有了好点子的自己，那个为截止日期焦虑的自己。这些瞬间组成了我们，而刻记+帮我们把它们留住。

三个模块，像三种不同的记忆方式。待办是未来的记忆，写下要做的事，等未来去完成；灵感是当下的记忆，捕捉此刻脑海中的火花；日期是过去的记忆，标记那些已经发生但不想忘记的日子。它们互相缠绕，待办可能源于某个灵感，灵感可能关联某个日期，日期又可能催生新的待办。像一张网，把时间的碎片编织在一起。

开发这个 app 的过程，本身也成了一段记忆。从最初的想法，到一行行代码，到一个个功能成型，再到写下这篇参赛贴。每一步都有记录，每一次对话都保留了编号。如果有一天回头看，会发现这段旅程本身，就是刻记+最好的演示数据。

所以，这不只是一个工具，更是一个陪伴者。它陪你记录，陪你完成，陪你记住。而那只柯基，永远在那里，安静地看着你，等待你写下下一条。"""
        val i7 = Inspiration(
            title = "时间与记忆的诗",
            content = i7Content,
            contentFormat = i7Content,
            tags = toJsonArray(listOf("诗意", "时间", "记忆", "河流")),
            imagePaths = toJsonArray(imagePaths["I7"] ?: emptyList()),
            createdAt = timeStamp(2026, 7, 14, 22, 0),
            updatedAt = timeStamp(2026, 7, 14, 22, 0),
            isPinned = true,
            categoryId = categoryIds[2]!!, // 生活
            priority = 2, // 中
            status = 0 // 进行中
        )
        inspirationIds["I7"] = inspirationDao.insert(i7)

        Log.d(tag, "✅ 步骤 5/7 灵感注入完成（7 条，含 contentFormat + 字数梯度 + 诗意文案）")
        return inspirationIds
    }

    /**
     * 将字符串列表转为 JSON 数组字符串
     */
    private fun toJsonArray(items: List<String>): String {
        val jsonArray = JSONArray()
        items.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    /**
     * 生成时间戳（毫秒）
     */
    private fun timeStamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, month - 1, day, hour, minute, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
