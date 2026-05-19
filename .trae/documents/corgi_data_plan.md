# CorgiData 柯基数据实体实现计划

## 当前项目分析

**已有资源：**
- ✅ Room 依赖已配置
- ✅ CorgiMemoDatabase 已创建
- ✅ KSP 已启用

**需要创建/修改的文件：**
```
data/model/
└── CorgiData.kt (新建 - 柯基数据实体类)
data/local/db/
└── CorgiMemoDatabase.kt (修改 - 注册新实体，更新版本)
data/local/db/
└── CorgiDao.kt (新建 - 柯基数据访问对象)
data/repository/
└── CorgiRepository.kt (新建 - 柯基数据仓库)
di/
└── DatabaseModule.kt (修改 - 添加 CorgiDao 提供器)
```

---

## 实现步骤

### 1. 创建 CorgiData 实体类
**文件：** `data/model/CorgiData.kt`

**字段说明：**

| 字段名 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| id | Long | 0 (自增) | 主键 |
| name | String | - | 柯基名字 |
| level | Int | 1 | 等级 |
| experience | Int | 0 | 经验值 |
| currentOutfit | String? | null | 当前装扮 |
| unlockedOutfits | String | "[]" | 已解锁装扮(JSON数组) |
| moodValue | Int | 50 | 情绪值 |
| lastActiveDate | String | - | 最后活跃日期 |
| totalCompleted | Int | 0 | 累计完成任务数 |
| consecutiveDays | Int | 0 | 连续完成天数 |

---

### 2. 创建 CorgiDao 接口
**文件：** `data/local/db/CorgiDao.kt`

**功能：**
- 插入/更新柯基数据
- 获取柯基数据
- 删除柯基数据
- 更新经验值、等级、情绪值等

---

### 3. 更新 CorgiMemoDatabase
**文件：** `data/local/db/CorgiMemoDatabase.kt`

**修改内容：**
- 将 CorgiData 加入 entities 列表
- 更新数据库版本从 1 到 2
- 添加 CorgiDao 访问方法

---

### 4. 更新 DatabaseModule
**文件：** `di/DatabaseModule.kt`

**修改内容：**
- 添加 CorgiDao 提供器

---

### 5. 创建 CorgiRepository
**文件：** `data/repository/CorgiRepository.kt`

**功能：**
- 封装柯基数据的业务逻辑
- 提供经验值增加、等级提升等方法
- 使用 IoDispatcher 进行后台操作

---

## 修改的文件

| 文件路径 | 修改内容 |
|----------|----------|
| `data/model/CorgiData.kt` | 新建 |
| `data/local/db/CorgiDao.kt` | 新建 |
| `data/local/db/CorgiMemoDatabase.kt` | 修改 - 添加实体、更新版本 |
| `data/repository/CorgiRepository.kt` | 新建 |
| `di/DatabaseModule.kt` | 修改 - 添加 CorgiDao 提供器 |
