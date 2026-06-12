package com.corgimemo.app.animation

/**
 * 节气日期（公历固定范围）
 * 由于每年节气日期略有不同，使用范围判断
 *
 * @property month 月份（1-12）
 * @property dayRange 日期范围（如立春：2月3-5日 -> 3..5）
 */
data class SolarTermDate(
    val month: Int,
    val dayRange: IntRange
)

/**
 * 节气数据类
 *
 * @property id 唯一ID
 * @property name 英文标识
 * @property displayName 中文名称（用于与 tyme4kt 返回值匹配）
 * @property date 节气日期范围（保留作为 fallback，主判断由 tyme4kt 负责）
 * @property proverb 节气谚语
 * @property knowledge 科普知识（100字以内）
 * @property corgiSays 柯基的温馨话语
 * @property iconEmoji 图标表情
 */
data class SolarTerm(
    val id: String,
    val name: String,
    val displayName: String,
    val date: SolarTermDate,
    val proverb: String,
    val knowledge: String,
    val corgiSays: String,
    val iconEmoji: String
)

/**
 * 节气 ID 常量
 */
object SolarTermId {
    // 春季
    const val LICHUN = "lichun"
    const val YUSHUI = "yushui"
    const val JINGZHE = "jingzhe"
    const val CHUNFEN = "chunfen"
    const val QINGMING = "qingming"
    const val GUYU = "guyu"

    // 夏季
    const val LIXIA = "lixia"
    const val XIAOMAN = "xiaoman"
    const val MANGZHONG = "mangzhong"
    const val XIAZHI = "xiazhi"
    const val XIAOSHU = "xiaoshu"
    const val DASHU = "dashu"

    // 秋季
    const val LIQIU = "liqiu"
    const val CHUSHU = "chushu"
    const val BAILU = "bailu"
    const val QIUFEN = "qiufen"
    const val HANLU = "hanlu"
    const val SHUANGJIANG = "shuangjiang"

    // 冬季
    const val LIDONG = "lidong"
    const val XIAOXUE = "xiaoxue"
    const val DAXUE = "daxue"
    const val DONGZHI = "dongzhi"
    const val XIAOHAN = "xiaohan"
    const val DAHAN = "dahan"
}

/**
 * 24 节气完整数据
 */
object SolarTermData {

    /**
     * 所有节气列表
     */
    val allSolarTerms: List<SolarTerm> = listOf(
        // ==================== 春季（6个节气） ====================

        // 立春 - 2月3-5日
        SolarTerm(
            id = SolarTermId.LICHUN,
            name = "LICHUN",
            displayName = "立春",
            date = SolarTermDate(2, 3..5),
            proverb = "一年之计在于春，一生之计在于勤",
            knowledge = "立春是二十四节气之首，标志着春季开始。此时气温回升，大地回暖，万物开始复苏。",
            corgiSays = "春天来啦！新的一年，让我们一起加油吧 🌸",
            iconEmoji = "🌱"
        ),

        // 雨水 - 2月18-20日
        SolarTerm(
            id = SolarTermId.YUSHUI,
            name = "YUSHUI",
            displayName = "雨水",
            date = SolarTermDate(2, 18..20),
            proverb = "春雨贵如油，润物细无声",
            knowledge = "雨水时节气温回升，冰雪融化，降水增多。此时要注意保暖，预防感冒。",
            corgiSays = "下雨啦！出门记得带伞哦 ☔",
            iconEmoji = "💧"
        ),

        // 惊蛰 - 3月5-7日
        SolarTerm(
            id = SolarTermId.JINGZHE,
            name = "JINGZHE",
            displayName = "惊蛰",
            date = SolarTermDate(3, 5..7),
            proverb = "春雷惊百虫，春风送暖入屠苏",
            knowledge = "惊蛰时节春雷始鸣，蛰伏的昆虫被惊醒，桃花开始盛开。",
            corgiSays = "哇，春雷响啦！小动物们都醒了 🐛",
            iconEmoji = "⚡"
        ),

        // 春分 - 3月20-22日
        SolarTerm(
            id = SolarTermId.CHUNFEN,
            name = "CHUNFEN",
            displayName = "春分",
            date = SolarTermDate(3, 20..22),
            proverb = "春分昼夜平分，阴阳平衡",
            knowledge = "春分日昼夜等长，是春季九十天的中分点，之后气温回升加快。",
            corgiSays = "昼夜平分啦！天气越来越暖和了 ☀️",
            iconEmoji = "🌸"
        ),

        // 清明 - 4月4-6日
        SolarTerm(
            id = SolarTermId.QINGMING,
            name = "QINGMING",
            displayName = "清明",
            date = SolarTermDate(4, 4..6),
            proverb = "清明时节雨纷纷，路上行人欲断魂",
            knowledge = "清明既是节气也是节日，是扫墓祭祖、踏青郊游的日子。",
            corgiSays = "清明踏青去！记得带上好吃的 🧺",
            iconEmoji = "🌿"
        ),

        // 谷雨 - 4月19-21日
        SolarTerm(
            id = SolarTermId.GUYU,
            name = "GUYU",
            displayName = "谷雨",
            date = SolarTermDate(4, 19..21),
            proverb = "谷雨前后，种瓜点豆",
            knowledge = "谷雨雨水增多，利于谷物生长，是播种的好时节。",
            corgiSays = "谷雨到啦！农民伯伯在种庄稼 🌾",
            iconEmoji = "🌾"
        ),

        // ==================== 夏季（6个节气） ====================

        // 立夏 - 5月5-7日
        SolarTerm(
            id = SolarTermId.LIXIA,
            name = "LIXIA",
            displayName = "立夏",
            date = SolarTermDate(5, 5..7),
            proverb = "立夏不下，桑老麦罢",
            knowledge = "立夏标志着夏季开始，气温明显升高，雷雨增多。",
            corgiSays = "夏天来啦！可以吃西瓜啦 🍉",
            iconEmoji = "🌞"
        ),

        // 小满 - 5月20-22日
        SolarTerm(
            id = SolarTermId.XIAOMAN,
            name = "XIAOMAN",
            displayName = "小满",
            date = SolarTermDate(5, 20..22),
            proverb = "小满小满，麦粒渐满",
            knowledge = "小满时节，麦类等夏熟作物籽粒开始饱满，但尚未成熟。",
            corgiSays = "麦粒都饱满了，很快就能收割啦 🌾",
            iconEmoji = "🌾"
        ),

        // 芒种 - 6月5-7日
        SolarTerm(
            id = SolarTermId.MANGZHONG,
            name = "MANGZHONG",
            displayName = "芒种",
            date = SolarTermDate(6, 5..7),
            proverb = "芒种忙忙种，夏至谷怀胎",
            knowledge = "芒种是农忙时节，有芒作物成熟，夏播作物开始播种。",
            corgiSays = "忙种时节，大家都在忙碌呢 👨‍🌾",
            iconEmoji = "🌻"
        ),

        // 夏至 - 6月21-22日
        SolarTerm(
            id = SolarTermId.XIAZHI,
            name = "XIAZHI",
            displayName = "夏至",
            date = SolarTermDate(6, 21..22),
            proverb = "夏至不过不热，冬至不过不冷",
            knowledge = "夏至是北半球白昼最长的一天，太阳直射北回归线。",
            corgiSays = "白天最长啦！可以玩久一点 🎮",
            iconEmoji = "☀️"
        ),

        // 小暑 - 7月6-8日
        SolarTerm(
            id = SolarTermId.XIAOSHU,
            name = "XIAOSHU",
            displayName = "小暑",
            date = SolarTermDate(7, 6..8),
            proverb = "小暑不算热，大暑三伏天",
            knowledge = "小暑天气开始炎热，但还没到最热的时候。",
            corgiSays = "有点热了，记得多喝水哦 💧",
            iconEmoji = "🌡️"
        ),

        // 大暑 - 7月22-24日
        SolarTerm(
            id = SolarTermId.DASHU,
            name = "DASHU",
            displayName = "大暑",
            date = SolarTermDate(7, 22..24),
            proverb = "大暑热不透，大热在秋后",
            knowledge = "大暑是一年中最热的时期，高温闷热，要注意防暑。",
            corgiSays = "最热的时候来了！吹空调吃西瓜 🍉",
            iconEmoji = "🔥"
        ),

        // ==================== 秋季（6个节气） ====================

        // 立秋 - 8月7-9日
        SolarTerm(
            id = SolarTermId.LIQIU,
            name = "LIQIU",
            displayName = "立秋",
            date = SolarTermDate(8, 7..9),
            proverb = "立秋之日凉风至",
            knowledge = "立秋标志着秋季开始，暑去凉来，但\"秋老虎\"可能还在。",
            corgiSays = "秋天来啦！天气开始转凉了 🍂",
            iconEmoji = "🍂"
        ),

        // 处暑 - 8月22-24日
        SolarTerm(
            id = SolarTermId.CHUSHU,
            name = "CHUSHU",
            displayName = "处暑",
            date = SolarTermDate(8, 22..24),
            proverb = "处暑天不暑，炎热在中午",
            knowledge = "处暑暑气渐消，气温逐渐下降，昼夜温差增大。",
            corgiSays = "处暑啦，终于没那么热了 😎",
            iconEmoji = "☁️"
        ),

        // 白露 - 9月7-9日
        SolarTerm(
            id = SolarTermId.BAILU,
            name = "BAILU",
            displayName = "白露",
            date = SolarTermDate(9, 7..9),
            proverb = "白露秋风夜，一夜凉一夜",
            knowledge = "白露时节天气转凉，清晨草木上有露水凝结。",
            corgiSays = "好冷呀，早上起来都有露水了 💧",
            iconEmoji = "💧"
        ),

        // 秋分 - 9月22-24日
        SolarTerm(
            id = SolarTermId.QIUFEN,
            name = "QIUFEN",
            displayName = "秋分",
            date = SolarTermDate(9, 22..24),
            proverb = "秋分昼夜等长，平分秋色",
            knowledge = "秋分再次昼夜平分，之后夜长昼短，秋意渐浓。",
            corgiSays = "今天又是昼夜平分呢 🌙",
            iconEmoji = "🍁"
        ),

        // 寒露 - 10月8-9日
        SolarTerm(
            id = SolarTermId.HANLU,
            name = "HANLU",
            displayName = "寒露",
            date = SolarTermDate(10, 8..9),
            proverb = "寒露寒露，遍地冷露",
            knowledge = "寒露气温更低，露水更凉，寒意渐浓，注意添衣。",
            corgiSays = "好冷好冷，要穿厚衣服啦 🧥",
            iconEmoji = "❄️"
        ),

        // 霜降 - 10月23-24日
        SolarTerm(
            id = SolarTermId.SHUANGJIANG,
            name = "SHUANGJIANG",
            displayName = "霜降",
            date = SolarTermDate(10, 23..24),
            proverb = "霜降杀百草，立冬地不冻",
            knowledge = "霜降天气渐冷，开始有霜，是秋季最后一个节气。",
            corgiSays = "下霜啦！叶子都变黄了 🍂",
            iconEmoji = "🌫️"
        ),

        // ==================== 冬季（6个节气） ====================

        // 立冬 - 11月7-8日
        SolarTerm(
            id = SolarTermId.LIDONG,
            name = "LIDONG",
            displayName = "立冬",
            date = SolarTermDate(11, 7..8),
            proverb = "立冬立冬，游泳无用",
            knowledge = "立冬标志着冬季开始，万物收藏，注意保暖。",
            corgiSays = "冬天来了！要注意保暖哦 🧣",
            iconEmoji = "❄️"
        ),

        // 小雪 - 11月22-23日
        SolarTerm(
            id = SolarTermId.XIAOXUE,
            name = "XIAOXUE",
            displayName = "小雪",
            date = SolarTermDate(11, 22..23),
            proverb = "小雪不封地，不过三五日",
            knowledge = "小雪时节北方开始降雪，天气更冷，注意保暖。",
            corgiSays = "下雪啦！可以堆雪人了 ⛄",
            iconEmoji = "🌨️"
        ),

        // 大雪 - 12月6-8日
        SolarTerm(
            id = SolarTermId.DAXUE,
            name = "DAXUE",
            displayName = "大雪",
            date = SolarTermDate(12, 6..8),
            proverb = "大雪不冻，惊蛰不开",
            knowledge = "大雪时节降雪量可能增大，天气更冷，窝在家里最舒服。",
            corgiSays = "大雪纷飞！窝在家里最舒服 🛋️",
            iconEmoji = "❄️"
        ),

        // 冬至 - 12月21-23日
        SolarTerm(
            id = SolarTermId.DONGZHI,
            name = "DONGZHI",
            displayName = "冬至",
            date = SolarTermDate(12, 21..23),
            proverb = "冬至不端饺子碗，冻掉耳朵没人管",
            knowledge = "冬至是北半球白昼最短的一天，北方吃饺子，南方吃汤圆。",
            corgiSays = "冬至快乐！记得吃饺子/汤圆 🥟",
            iconEmoji = "❄️"
        ),

        // 小寒 - 1月5-7日
        SolarTerm(
            id = SolarTermId.XIAOHAN,
            name = "XIAOHAN",
            displayName = "小寒",
            date = SolarTermDate(1, 5..7),
            proverb = "小寒大寒，冷成冰团",
            knowledge = "小寒天气寒冷，但还没到最冷的时候，要多添衣服。",
            corgiSays = "好冷啊，来我怀里取暖吧 🐕",
            iconEmoji = "🥶"
        ),

        // 大寒 - 1月20-21日
        SolarTerm(
            id = SolarTermId.DAHAN,
            name = "DAHAN",
            displayName = "大寒",
            date = SolarTermDate(1, 20..21),
            proverb = "大寒到顶点，日后天渐暖",
            knowledge = "大寒是一年中最冷的时期，但之后春天即将到来。",
            corgiSays = "最冷的时候来了，但春天也不远了 🌸",
            iconEmoji = "🌨️"
        )
    )
}
