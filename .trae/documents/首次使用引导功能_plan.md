# 首次使用引导功能实现计划

## 当前状态分析

### 已有功能
| 功能 | 状态 | 文件 |
|------|------|------|
| 柯基命名对话框 | ✅ 已有 | [CorgiNamerDialog.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CorgiNamerDialog.kt) |
| isFirstLaunch 标志 | ✅ 已有 | [CorgiPreferences.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt#L48-L51) |
| 命名弹窗触发逻辑 | ⚠️ 需修改 | [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L255-L259) |
| 默认分类（工作/学习/生活） | ✅ 已有 | [CategoryRepository.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt#L28-L36) |

### 缺失功能
| 功能 | 状态 | 说明 |
|------|------|------|
| HorizontalPager 滑动引导 | ❌ 缺失 | 需要添加 compose-foundation 依赖 |
| Onboarding 路由 | ❌ 缺失 | 导航系统需要添加 Onboarding 页面 |
| 身份选择（上班族/学生） | ❌ 缺失 | 新增用户类型选择 |
| isOnboardingCompleted 标志 | ❌ 缺失 | 需添加新的 DataStore 字段 |
| userType 存储 | ❌ 缺失 | 需添加用户类型字段 |
| 根据身份的默认分类/问候语 | ❌ 缺失 | 需要根据用户类型定制 |

---

## 实现目标

1. **完整引导流程**：使用 HorizontalPager 实现 5 个引导页面
2. **身份选择**：上班族/学生二选一
3. **柯基命名**：集成现有 CorgiNamerDialog
4. **权限引导**：请求通知权限
5. **跳过功能**：允许用户跳过引导
6. **完成标记**：设置 isOnboardingCompleted = true
7. **个性化定制**：根据身份设置默认分类和问候语

---

## 实现步骤

### 步骤 1：添加 HorizontalPager 依赖

**修改文件：**
- [libs.versions.toml](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/gradle/libs.versions.toml) - 添加 compose-foundation 库定义
- [build.gradle.kts](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/build.gradle.kts) - 添加 compose-foundation 依赖

**实现内容：**
```toml
# libs.versions.toml
[libraries]
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation", version.ref = "compose" }
```

```kotlin
// build.gradle.kts
implementation(libs.androidx.compose.foundation)
```

---

### 步骤 2：修改 DataStore 添加新字段

**修改文件：**
- [CorgiPreferences.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt) - 添加新字段和方法

**实现内容：**
```kotlin
// 新增键
private val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
private val USER_TYPE = stringPreferencesKey("user_type")  // "worker" | "student"

// 新增 Flow
val isOnboardingCompleted: Flow<Boolean>
val userType: Flow<String?>

// 新增方法
suspend fun setOnboardingCompleted()
suspend fun saveUserType(userType: String)
```

**用户类型枚举：**
```kotlin
enum class UserType {
    WORKER,  // 上班族
    STUDENT  // 学生
}
```

---

### 步骤 3：添加 Onboarding 导航路由

**修改文件：**
- [Screen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt) - 添加 Onboarding 路由
- [AppNavHost.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt) - 添加 Onboarding composable

**实现内容：**
```kotlin
// Screen.kt
object Onboarding : Screen("onboarding")

// AppNavHost.kt
// startDestination 改为动态判断
composable(Screen.Onboarding.route) {
    OnboardingScreen(navController = navController)
}
```

---

### 步骤 4：创建 Onboarding ViewModel

**新建文件：**
- `app/src/main/java/com/corgimemo/app/viewmodel/OnboardingViewModel.kt`

**实现内容：**
```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val corgiPreferences: CorgiPreferences,
    private val categoryRepository: CategoryRepository,
    private val corgiRepository: CorgiRepository
) : ViewModel() {
    
    // 引导页面状态
    val currentPage = MutableStateFlow(0)
    
    // 用户输入状态
    val selectedUserType = MutableStateFlow<UserType?>(null)
    val corgiName = MutableStateFlow("")
    
    // 页面控制方法
    fun nextPage()
    fun prevPage()
    fun setUserType(type: UserType)
    fun setCorgiName(name: String)
    
    // 完成引导
    suspend fun completeOnboarding()
}
```

---

### 步骤 5：创建 OnboardingScreen（HorizontalPager 引导页面）

**新建文件：**
- `app/src/main/java/com/corgimemo/app/ui/screens/onboarding/OnboardingScreen.kt`

**实现内容：**
```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(navController: NavController) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    
    HorizontalPager(state = pagerState) { page ->
        when (page) {
            0 -> WelcomePage()
            1 -> UserTypePage(onSelect = { type -> ... })
            2 -> CorgiNamingPage(onNameEntered = { name -> ... })
            3 -> GreetingPage(corgiName = name)
            4 -> PermissionPage(onComplete = { ... })
        }
    }
    
    // 底部指示器和按钮
    HorizontalPagerIndicator(...)
    
    // 跳过按钮
    TextButton(onClick = { ... }) { Text("跳过") }
}
```

---

### 步骤 6：创建各个引导页面组件

**新建文件：**
- `app/src/main/java/com/corgimemo/app/ui/screens/onboarding/WelcomePage.kt`
- `app/src/main/java/com/corgimemo/app/ui/screens/onboarding/UserTypePage.kt`
- `app/src/main/java/com/corgimemo/app/ui/screens/onboarding/CorgiNamingPage.kt`
- `app/src/main/java/com/corgimemo/app/ui/screens/onboarding/GreetingPage.kt`
- `app/src/main/java/com/corgimemo/app/ui/screens/onboarding/PermissionPage.kt`

**页面内容：**

| 页面 | 内容 |
|------|------|
| **WelcomePage** | APP 名称 "柯基待办" + Logo/柯基表情 + 简短介绍 |
| **UserTypePage** | 两个大卡片：上班族 📅 和 学生 📚 |
| **CorgiNamingPage** | 复用 CorgiNamerDialog 样式的输入框 |
| **GreetingPage** | 柯基动画 + "很高兴认识你！" + 名字显示 |
| **PermissionPage** | 通知权限说明 + 请求按钮 + 跳过按钮 |

---

### 步骤 7：修改 MainActivity 动态判断起始路由

**修改文件：**
- `MainActivity.kt` - 读取 isOnboardingCompleted 决定起始页面

**实现内容：**
```kotlin
// 在 MainActivity 中或 AppNavHost 中
val isOnboardingCompleted = corgiPreferences.isOnboardingCompleted.collectAsState(initial = false)

val startDestination = if (isOnboardingCompleted.value) {
    Screen.Home.route
} else {
    Screen.Onboarding.route
}
```

---

### 步骤 8：修改 HomeViewModel 移除旧的命名弹窗逻辑

**修改文件：**
- [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) - 移除 isFirstLaunch 相关的弹窗逻辑

**修改内容：**
```kotlin
// 移除旧逻辑：
// val isFirst = corgiPreferences.isFirstLaunch.first()
// if (isFirst) { _showNamerDialog.value = true }

// 改为直接加载柯基数据
```

---

### 步骤 9：根据用户类型定制默认分类和问候语

**修改文件：**
- `app/src/main/java/com/corgimemo/app/animation/GreetingManager.kt` - 添加根据用户类型的问候语
- [CategoryRepository.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt) - 可根据用户类型添加更多分类

**实现内容：**
```kotlin
// 上班族默认分类：工作、会议、生活
// 学生默认分类：学习、作业、生活

// 上班族问候语："今天工作顺利吗？"
// 学生问候语："今天学习怎么样？"
```

---

## 待修改文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| [libs.versions.toml](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/gradle/libs.versions.toml) | 修改 | 添加 compose-foundation 库定义 |
| [build.gradle.kts](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/build.gradle.kts) | 修改 | 添加 compose-foundation 依赖 |
| [CorgiPreferences.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt) | 修改 | 添加 isOnboardingCompleted、userType 字段 |
| [Screen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt) | 修改 | 添加 Onboarding 路由 |
| [AppNavHost.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt) | 修改 | 动态判断起始路由 + Onboarding composable |
| [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) | 修改 | 移除旧的 isFirstLaunch 弹窗逻辑 |
| OnboardingViewModel.kt | 新建 | 引导流程状态管理 |
| OnboardingScreen.kt | 新建 | HorizontalPager 主页面 |
| WelcomePage.kt | 新建 | 欢迎页 |
| UserTypePage.kt | 新建 | 身份选择页 |
| CorgiNamingPage.kt | 新建 | 柯基命名页 |
| GreetingPage.kt | 新建 | 打招呼页 |
| PermissionPage.kt | 新建 | 权限引导页 |
| UserType.kt | 新建 | 用户类型枚举 |

---

## 引导页面流程图

```
用户首次打开 APP
    ↓
判断 isOnboardingCompleted
    ↓
├─ true → 直接进入首页
└─ false → 进入引导流程
                ↓
        [第1页] 欢迎页
                ↓
        [第2页] 身份选择（上班族/学生）
                ↓
        [第3页] 柯基命名
                ↓
        [第4页] 柯基打招呼
                ↓
        [第5页] 权限引导
                ↓
        完成引导 → 设置 isOnboardingCompleted = true
                ↓
        进入首页
```

---

## 风险与注意事项

| 风险 | 应对措施 |
|------|----------|
| 用户跳过引导 | 设置默认用户类型和默认名字，确保不影响使用 |
| 权限请求失败 | 权限页提供跳过选项，后续可在设置中开启 |
| 已有用户数据 | 保留 isFirstLaunch 用于判断是否创建默认分类，isOnboardingCompleted 用于判断是否完成引导 |
| HorizontalPager 兼容性 | 确保使用 Compose 1.6.0+ 的 foundation 版本 |

---

## 测试方案

### 手动测试
1. **首次启动**：确认显示引导页面而非首页
2. **滑动翻页**：确认 5 个页面可正常滑动
3. **身份选择**：选择上班族/学生，确认后续行为正确
4. **柯基命名**：输入名字，确认保存成功
5. **跳过功能**：点击跳过，确认能进入首页
6. **权限请求**：测试通知权限请求功能
7. **二次启动**：确认直接进入首页不再显示引导
8. **个性化定制**：确认上班族/学生的问候语和分类正确
