# CorgiMemo 语音备注功能 - 实现计划

## 📋 功能概述

为 CorgiMemo 待办应用添加语音备注功能，允许用户录制和播放语音备注，增强待办事项的表达能力。

### 用户确认的需求范围：
- ✅ **完整实现**：包含录制、播放、数据存储、UI 交互全部功能
- ✅ **实时波形动画**：录制时显示音频波形可视化
- ✅ **前台服务**：使用 Foreground Service 确保后台播放不中断

---

## 🏗️ 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer (Compose)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ TodoEditScreen│  │ VoiceRecord  │  │ VoicePlayer          │   │
│  │ (底部工具栏)  │  │ BottomSheet  │  │ Component            │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
│         │                 │                      │              │
└─────────┼─────────────────┼──────────────────────┼──────────────┘
          │                 │                      │
┌─────────┼─────────────────┼──────────────────────┼──────────────┐
│         ▼                 ▼                      ▼              │
│                    ViewModel Layer                              │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              TodoEditViewModel                           │    │
│  │  - voiceNotePath: StateFlow<String?>                     │    │
│  │  - voiceDuration: StateFlow<Int?>                        │    │
│  │  - 录制/播放状态管理                                      │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                             ▼                                   │
│                      Utility Layer                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │  VoiceRecorder  │  │  VoicePlayer    │  │ AudioVisualizer│  │
│  │  (MediaRecorder)│  │  (MediaPlayer)  │  │  (Canvas)      │  │
│  └─────────────────┘  └─────────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                             ▼                                   │
│                      Service Layer                              │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │         VoicePlaybackService (Foreground Service)        │    │
│  │  - 后台播放控制                                          │    │
│  │  - 通知栏控制                                            │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                             ▼                                   │
│                       Data Layer                                │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Room Database                         │    │
│  │  TodoItem: +voiceNotePath, +voiceDuration               │    │
│  │  Migration: 11 → 12                                     │    │
│  └─────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              File Storage                               │    │
│  │  /data/data/com.corgimemo.app/files/voice_notes/       │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 模块分解与实现步骤

### 阶段一：基础架构（数据层 + 工具类）

#### 1.1 数据模型扩展
**文件**: `app/src/main/java/com/corgimemo/app/data/model/TodoItem.kt`

```kotlin
// 新增字段
val voiceNotePath: String? = null,      // 语音文件路径
val voiceDuration: Int? = null           // 语音时长（秒）
```

#### 1.2 数据库迁移
**文件**: `app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt`

- 版本升级：11 → 12
- 新增迁移脚本 `MIGRATION_11_12`:
  - `ALTER TABLE todo_items ADD COLUMN voiceNotePath TEXT`
  - `ALTER TABLE todo_items ADD COLUMN voiceDuration INTEGER`

#### 1.3 VoiceRecorder 类
**新文件**: `app/src/main/java/com/corgimemo/app/util/VoiceRecorder.kt`

**功能特性**：
| 特性 | 实现细节 |
|------|----------|
| 录制格式 | AAC (.m4a) |
| 采样率 | 44100 Hz |
| 声道 | 单声道 |
| 最大时长 | 30 秒（自动停止） |
| 存储路径 | `/files/voice_notes/{timestamp}.m4a` |
| 状态管理 | IDLE → RECORDING → STOPPED |

**核心 API**：
```kotlin
class VoiceRecorder(private val context: Context) {
    // 状态枚举
    enum class RecordingState { IDLE, RECORDING, STOPPED }
    
    // 状态流
    val recordingState: StateFlow<RecordingState>
    val amplitude: StateFlow<Float>  // 实时音量 (0-1)
    val duration: StateFlow<Long>     // 录制时长（毫秒）
    val filePath: StateFlow<String?>  // 文件路径
    
    // 方法
    fun startRecording(): Result<Unit>
    fun stopRecording(): Result<String>  // 返回文件路径
    fun cancelRecording()
    fun release()
}
```

#### 1.4 VoicePlayer 类
**新文件**: `app/src/main/java/com/corgimemo/app/util/VoicePlayer.kt`

**功能特性**：
| 特性 | 实现细节 |
|------|----------|
| 播放控制 | 播放、暂停、恢复、停止 |
| 进度控制 | 可拖动进度条 |
| 状态流 | 播放状态、当前进度、总时长 |
| 自动重置 | 播放完成后回到起始位置 |

**核心 API**：
```kotlin
class VoicePlayer(private val context: Context) {
    // 状态枚举
    enum class PlaybackState { IDLE, PLAYING, PAUSED, STOPPED }
    
    // 状态流
    val playbackState: StateFlow<PlaybackState>
    val currentPosition: StateFlow<Int>    // 当前位置（毫秒）
    val duration: StateFlow<Int>           // 总时长（毫秒）
    val isPlaying: StateFlow<Boolean>
    
    // 方法
    fun prepare(filePath: String): Result<Unit>
    fun play(): Result<Unit>
    fun pause(): Result<Unit>
    fun resume(): Result<Unit>
    fun stop(): Result<Unit>
    fun seekTo(position: Int): Result<Unit>
    fun release()
}
```

#### 1.5 音频可视化器
**新文件**: `app/src/main/java/com/corgimemo/app/ui/components/AudioWaveform.kt`

**实现方式**：
- 使用 Compose Canvas 绘制实时波形
- 接收音量振幅数据 (0-1)
- 绘制动态柱状波形或平滑曲线
- 支持录制和播放两种模式

---

### 阶段二：服务层

#### 2.1 VoicePlaybackService
**新文件**: `app/src/main/java/com/corgimemo/app/service/VoicePlaybackService.kt`

**功能**：
- 继承自 `Service`，使用 `startForeground()`
- 显示常驻通知栏（带播放/暂停按钮）
- 通过 Binder 或 EventBus 与 UI 通信
- 处理音频焦点（AudioFocus）

**通知栏设计**：
```
┌─────────────────────────────────────┐
│ 🎤 正在播放: 待办标题                │
│ [⏸] [━━━━●━━━━━━] 00:12 / 00:25   │
└─────────────────────────────────────┘
```

---

### 阶段三：UI 层

#### 3.1 录制界面 - BottomSheet
**修改文件**: `TodoEditScreen.kt`
**新组件**: `VoiceRecordBottomSheet.kt`

**布局结构**：
```
┌─────────────────────────────────────┐
│        语音备注录制                  │
│                                     │
│  ┌─────────────────────────────┐   │
│  │  ▁▂▃▅▇▃▂▁▂▃▅▇▅▃▂▁▂▃▅▇▃  │   │  ← 波形动画
│  │  (实时音频波形)              │   │
│  └─────────────────────────────┘   │
│                                     │
│         ● (录制按钮)                │  ← 红色脉动动画
│                                     │
│      00:05 / 00:30                 │  ← 计时器
│                                     │
│  [🔄 重录]    [✓ 保存]             │  ← 操作按钮
└─────────────────────────────────────┘
```

**交互流程**：
1. 点击"🎤 语音备注"按钮 → 展开 BottomSheet
2. 点击红色圆形按钮 → 开始录制 → 显示波形动画
3. 到达 30 秒或手动停止 → 显示"重录"和"保存"
4. 点击"保存" → 关联到当前待办

#### 3.2 播放界面组件
**新文件**: `VoicePlayerComponent.kt`

**布局结构**（在待办详情页）：
```
┌─────────────────────────────────────┐
│  🎤 语音备注                        │
│                                     │
│  [▶] ━━━━━━━━●━━━━━━━━  00:12/00:25│
│                                     │
│                            [🗑 删除] │
└─────────────────────────────────────┘
```

**列表项展示**：
- 有语音备注的待办右侧显示 🎤 图标

---

### 阶段四：集成与权限处理

#### 4.1 权限处理
**修改文件**: `MainActivity.kt` 或新建 `PermissionHandler.kt`

- 使用 Accompanist Permissions 库
- 首次使用请求 `RECORD_AUDIO` 权限
- 被拒绝时显示引导弹窗：
  ```
  ┌─────────────────────────────────┐
  │  🔊 需要录音权限                 │
  │                                 │
  │  语音备注功能需要访问麦克风才能   │
  │  录制您的语音。                  │
  │                                 │
  │  [取消]    [去设置开启]          │
  └─────────────────────────────────┘
  ```

#### 4.2 ViewModel 扩展
**修改文件**: `TodoEditViewModel.kt`

新增字段：
```kotlin
// 语音备注相关
private val _voiceNotePath = MutableStateFlow<String?>(null)
val voiceNotePath: StateFlow<String?> = _voiceNotePath.asStateFlow()

private val _voiceDuration = MutableStateFlow<Int?>(null)
val voiceDuration: StateFlow<Int?> = _voiceDuration.asStateFlow()

private val _isRecording = MutableStateFlow(false)
val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
```

新增方法：
```kotlin
fun setVoiceNote(path: String?, duration: Int?)
fun clearVoiceNote()
```

#### 4.3 数据持久化集成
**修改文件**: `TodoEditViewModel.kt` - `performSave()` 方法

在保存逻辑中添加语音备注字段：
```kotlin
val todo = existingTodo!!.copy(
    // ... 其他字段
    voiceNotePath = _voiceNotePath.value,
    voiceDuration = _voiceDuration.value
)
```

---

## 🗂️ 文件变更清单

### 新增文件（8 个）

| 文件路径 | 用途 |
|----------|------|
| `util/VoiceRecorder.kt` | 录制封装类 |
| `util/VoicePlayer.kt` | 播放封装类 |
| `service/VoicePlaybackService.kt` | 前台播放服务 |
| `ui/components/AudioWaveform.kt` | 波形可视化组件 |
| `ui/components/VoiceRecordBottomSheet.kt` | 录制面板 |
| `ui/components/VoicePlayerComponent.kt` | 播放组件 |
| `ui/components/VoicePermissionDialog.kt` | 权限引导弹窗 |
| `di/VoiceModule.kt` | Hilt 依赖注入模块 |

### 修改文件（6 个）

| 文件路径 | 变更内容 |
|----------|----------|
| `data/model/TodoItem.kt` | +voiceNotePath, +voiceDuration 字段 |
| `data/local/db/CorgiMemoDatabase.kt` | +Migration 11→12 |
| `viewmodel/TodoEditViewModel.kt` | +语音备注状态和方法 |
| `ui/screens/todo/TodoEditScreen.kt` | +语音备注按钮和集成 |
| `ui/components/TodoListItem.kt` | +语音图标显示 |
| `AndroidManifest.xml` | +RECORD_AUDIO 权限 + Service 声明 |

---

## ⚠️ 技术要点与风险提示

### 关键技术点

1. **MediaRecorder 生命周期**
   - 必须按顺序调用：prepare() → start() → stop() → release()
   - 异常处理：IOException, IllegalStateException

2. **MediaPlayer 内存管理**
   - 及时释放资源避免内存泄漏
   - 使用 lifecycle-aware 的释放机制

3. **前台服务限制**
   - Android 8.0+ 必须使用 NotificationChannel
   - Android 11+ 后台启动限制需处理

4. **存储空间管理**
   - 录制前检查可用空间
   - 删除待办时同步删除语音文件

### 性能优化建议

- 使用对象池复用 Canvas 绘制对象
- 波形采样率降低到 20-30fps（无需 60fps）
- MediaPlayer 使用单个实例复用

---

## 📅 实施顺序建议

### 第一步：数据层基础（预计工作量：中等）
1. ✅ 扩展 TodoItem 数据模型
2. ✅ 编写数据库迁移脚本
3. ✅ 创建 VoiceRecorder 工具类
4. ✅ 创建 VoicePlayer 工具类

### 第二步：UI 核心组件（预计工作量：较大）
5. ✅ 实现 AudioWaveform 波形可视化
6. ✅ 创建 VoiceRecordBottomSheet 录制面板
7. ✅ 创建 VoicePlayerComponent 播放组件

### 第三步：服务层与集成（预计工作量：中等）
8. ✅ 实现 VoicePlaybackService 前台服务
9. ✅ 权限处理与引导弹窗
10. ✅ 集成到 TodoEditScreen 和 TodoListItem

### 第四步：测试与优化（预计工作量：较小）
11. ✅ 功能测试（录制/播放/删除）
12. ✅ 边界情况处理（权限拒绝/空间不足/中断恢复）
13. ✅ 性能优化（内存/CPU/电池）

---

## ✅ 验收标准

### 功能完整性
- [ ] 可以成功录制 0-30 秒的语音备注
- [ ] 录制过程中显示实时波形动画
- [ ] 录制超时 30 秒自动停止
- [ ] 可以正常播放语音备注（支持暂停/恢复/拖动进度条）
- [ ] 应用切到后台后播放不中断（前台服务生效）
- [ ] 语音文件正确关联到待办事项
- [ ] 删除待办时同步清理语音文件

### 用户体验
- [ ] 首次使用时有清晰的权限说明
- [ ] 权限被拒绝时有引导到设置的选项
- [ ] 录制界面操作直观，反馈及时
- [ ] 播放控件符合 Material Design 规范
- [ ] 删除操作有二次确认保护

### 技术质量
- [ ] 无内存泄漏（MediaPlayer/MediaRecorder 正确释放）
- [ ] 数据库迁移无损（旧版本数据兼容）
- [ ] 异常情况有友好的错误提示
- [ ] 代码遵循项目现有架构模式

---

## 🎯 下一步行动

**请审阅此实现计划，确认后我将开始逐步实施。**

如有任何疑问或需要调整的地方，请随时提出！
