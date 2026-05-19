# CorgiMemo Room 数据层实现计划

## 当前项目分析

**已有资源：**
- ✅ MVVM 架构已配置
- ✅ Room 依赖已添加 (Runtime、Ktx、Compiler
- ✅ Hilt 依赖已配置
- ✅ KSP 已启用
- ✅ Coroutines 依赖已添加

**需要创建的文件结构：**
```
data/
├── model/
│   └── TodoItem.kt (实体类)
├── local/
│   └── db/
│       ├── TodoDao.kt (数据访问对象)
│       └── CorgiMemoDatabase.kt (数据库类)
└── repository/
    └── TodoRepository.kt (数据仓库)
di/
└── DatabaseModule.kt (数据库依赖注入模块)
```

---

## 实现步骤

### 1. 创建 TodoItem 实体类
**文件：** `data/model/TodoItem.kt`

**功能：**
- 定义所有字段：id、title、content、categoryId、priority、status、dueDate、reminderTime、repeatType、createdAt、updatedAt、completedAt
- 添加复合索引优化查询性能
  - status + createdAt (按状态和创建时间排序)
  - categoryId + status (按分类和状态查询)
  - priority + dueDate (按优先级和截止时间查询)

---

### 2. 创建 TodoDao 接口
**文件：** `data/local/db/TodoDao.kt`

**功能：**
- 插入单条/批量待办
- 更新待办
- 删除单条/批量待办
- 查询所有待办
- 按状态查询待办
- 按分类查询待办
- 按优先级查询待办
- 查询带提醒的待办
- 按ID查询单条待办
- 流式查询 (Flow)

---

### 3. 创建 CorgiMemoDatabase 数据库类
**文件：** `data/local/db/CorgiMemoDatabase.kt`

**功能：**
- RoomDatabase 抽象类
- 定义数据库版本管理
- 包含 TodoDao 访问方法

---

### 4. 创建 DatabaseModule 依赖注入模块
**文件：** `di/DatabaseModule.kt`

**功能：**
- 提供 DatabaseProvider 提供器
- 提供 TodoDao 提供器
- 使用 @Singleton 注解确保单例

---

### 5. 创建 TodoRepository 数据仓库
**文件：** `data/repository/TodoRepository.kt`

**功能：**
- 封装 TodoDao 的业务逻辑
- 提供 Repository 模式
- 使用 IoDispatcher 进行后台操作
- 提供 Flow 数据流

---

## 索引优化策略

| 索引类型 | 说明 |
|----------|------|
| `status, createdAt` | 优化按状态和时间排序查询 |
| `categoryId, status` | 优化按分类和状态筛选查询 |
| `priority, dueDate` | 优化按优先级和截止时间查询 |

---

## 修改的文件

| 文件路径 | 修改内容 |
|----------|----------|
| `data/model/TodoItem.kt` | 新建 |
| `data/local/db/TodoDao.kt` | 新建 |
| `data/local/db/CorgiMemoDatabase.kt` | 新建 |
| `data/repository/TodoRepository.kt` | 新建 |
| `di/DatabaseModule.kt` | 新建 |
