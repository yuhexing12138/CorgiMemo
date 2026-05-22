# 语音备注功能集成指南

## 📋 功能概述

语音备注功能已完整实现，包含以下模块：

| 模块 | 文件路径 | 功能说明 |
|------|----------|----------|
| **数据模型** | `data/model/TodoItem.kt` | 新增 `voiceNotePath` 和 `voiceDuration` 字段 |
| **数据库迁移** | `data/local/db/CorgiMemoDatabase.kt` | v11 → v12 迁移脚本 |
| **录制工具** | `util/VoiceRecorder.kt` | MediaRecorder 封装，支持实时音量监测 |
| **播放工具** | `util/VoicePlayer.kt` | MediaPlayer 封装，支持进度控制 |
| **波形可视化** | `ui/components/AudioWaveform.kt` | Canvas 绘制实时波形动画 |
| **录制面板** | `ui/components/VoiceRecordBottomSheet.kt` | BottomSheet 录制界面 |
| **播放组件** | `ui/components/VoicePlayerComponent.kt` | 播放控制界面 |
| **前台服务** | `service/VoicePlaybackService.kt` | 后台播放服务 |
| **权限处理** | `ui/components/VoicePermissionDialog.kt` | 权限请求与引导弹窗 |

---

## 🔧 TodoEditScreen 集成示例

在 `TodoEditScreen.kt` 中添加语音备注按钮和播放组件：

```kotlin
@Composable
fun TodoEditScreen(
    viewModel: TodoEditViewModel = hiltViewModel(),
    // ... 其他参数
) {
    val context = LocalContext.current
    
    // 语音录制器实例
    val voiceRecorder = remember { VoiceRecorder(context) }
    
    // 语音播放器实例
    val voicePlayer = remember { VoicePlayer(context) }
    
    // 是否显示录制面板
    var showRecordSheet by remember { mutableStateOf(false) }
    
    // 收集语音备注状态
    val voiceNotePath by viewModel.voiceNotePath.collectAsState()
    val voiceDuration by viewModel.voiceDuration.collectAsState()
    
    // ... 其他代码
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // ... 标题、内容等输入框
        
        // 语音备注播放组件（如果有语音备注）
        voiceNotePath?.let { path ->
            VoicePlayerComponent(
                voicePlayer = voicePlayer,
                filePath = path,
                totalDuration = voiceDuration,
                onDelete = {
                    // 删除语音文件
                    File(path).delete()
                    viewModel.clearVoiceNote()
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        
        // ... 其他内容
        
        // 底部工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // ... 其他按钮
            
            // 语音备注按钮
            OutlinedButton(
                onClick = { showRecordSheet = true }
            ) {
                Icon(Icons.Default.Mic, contentDescription = "语音备注")
                Spacer(modifier = Modifier.width(4.dp))
                Text("语音备注")
            }
        }
    }
    
    // 录制面板
    if (showRecordSheet) {
        RecordAudioPermissionHandler(
            permissionGranted = {
                VoiceRecordBottomSheet(
                    voiceRecorder = voiceRecorder,
                    onSaved = { path, duration ->
                        viewModel.setVoiceNote(path, duration)
                    },
                    onDismiss = { showRecordSheet = false }
                )
            },
            permissionDenied = {
                // 打开系统设置
                context.startActivity(openAppSettingsIntent(context))
                showRecordSheet = false
            }
        )
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.release()
            voicePlayer.release()
        }
    }
}
```

---

## 🎯 核心功能使用示例

### 1. 录制语音

```kotlin
val voiceRecorder = VoiceRecorder(context)

// 开始录制
val result = voiceRecorder.startRecording()
if (result.isFailure) {
    // 处理错误
    println("录制失败: ${result.exceptionOrNull()?.message}")
}

// 监听录制状态
LaunchedEffect(Unit) {
    voiceRecorder.recordingState.collect { state ->
        when (state) {
            VoiceRecorder.RecordingState.RECORDING -> println("正在录制...")
            VoiceRecorder.RecordingState.STOPPED -> println("录制完成")
            else -> {}
        }
    }
}

// 停止录制
val stopResult = voiceRecorder.stopRecording()
if (stopResult.isSuccess) {
    val filePath = stopResult.getOrNull()
    println("文件保存至: $filePath")
}

// 释放资源
voiceRecorder.release()
```

### 2. 播放语音

```kotlin
val voicePlayer = VoicePlayer(context)

// 准备播放
voicePlayer.prepare(filePath)

// 开始播放
voicePlayer.play()

// 暂停播放
voicePlayer.pause()

// 恢复播放
voicePlayer.resume()

// 跳转到指定位置（毫秒）
voicePlayer.seekTo(5000)

// 监听播放进度
LaunchedEffect(Unit) {
    voicePlayer.currentPosition.collect { position ->
        println("当前进度: ${position}ms")
    }
}

// 释放资源
voicePlayer.release()
```

### 3. 使用前台服务播放

```kotlin
// 启动前台服务
val intent = Intent(context, VoicePlaybackService::class.java).apply {
    action = VoicePlaybackService.ACTION_PLAY_PAUSE
    putExtra(VoicePlaybackService.EXTRA_FILE_PATH, filePath)
    putExtra(VoicePlaybackService.EXTRA_TODO_TITLE, "待办标题")
}

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    context.startForegroundService(intent)
} else {
    context.startService(intent)
}

// 停止服务
val stopIntent = Intent(context, VoicePlaybackService::class.java).apply {
    action = VoicePlaybackService.ACTION_STOP
}
context.startService(stopIntent)
```

---

## ⚠️ 注意事项

### 1. 权限处理

确保在首次使用语音功能时请求 `RECORD_AUDIO` 权限：

```kotlin
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 2. 资源释放

**务必在组件销毁时释放资源**，避免内存泄漏：

```kotlin
DisposableEffect(Unit) {
    onDispose {
        voiceRecorder.release()
        voicePlayer.release()
    }
}
```

### 3. 文件管理

删除待办时，记得同步删除语音文件：

```kotlin
fun deleteTodoWithVoiceNote(todo: TodoItem) {
    // 删除语音文件
    todo.voiceNotePath?.let { path ->
        File(path).delete()
    }
    // 删除待办
    todoRepository.deleteTodo(todo)
}
```

### 4. 前台服务类型

Android 14+ 需要声明前台服务类型：

```xml
<service
    android:name=".service.VoicePlaybackService"
    android:foregroundServiceType="mediaPlayback" />
```

---

## 📱 UI 效果预览

### 录制界面

```
┌─────────────────────────────────────┐
│        🎤 语音备注                   │
│                                     │
│  ▁▂▃▅▇▃▂▁▂▃▅▇▅▃▂▁▂▃▅▇▃▂▁  │  ← 实时波形
│                                     │
│           ● (录制按钮)              │  ← 红色脉动动画
│                                     │
│        00:05 / 00:30               │  ← 计时器
│                                     │
│    [🔄 重录]    [✓ 保存]           │
└─────────────────────────────────────┘
```

### 播放界面

```
┌─────────────────────────────────────┐
│  🎤 语音备注                   [🗑]  │
│                                     │
│  [▶] ━━━━━━━━●━━━━━━━━  00:12/00:25│
└─────────────────────────────────────┘
```

### 列表项显示

```
┌─────────────────────────────────────┐
│  ☐ 完成项目报告                     │
│    📋 工作  🎤 15s                  │  ← 语音图标
└─────────────────────────────────────┘
```

---

## ✅ 验收清单

- [x] 数据模型扩展完成
- [x] 数据库迁移脚本完成
- [x] VoiceRecorder 录制工具完成
- [x] VoicePlayer 播放工具完成
- [x] AudioWaveform 波形可视化完成
- [x] VoiceRecordBottomSheet 录制面板完成
- [x] VoicePlayerComponent 播放组件完成
- [x] VoicePlaybackService 前台服务完成
- [x] 权限处理与引导弹窗完成
- [x] TodoEditViewModel 集成完成
- [x] TodoListItem 语音图标显示完成
- [x] AndroidManifest 权限和服务声明完成
- [x] 通知图标资源创建完成

---

## 🚀 下一步

1. 在 `TodoEditScreen.kt` 中按照上述示例集成语音备注按钮
2. 在待办详情页添加 `VoicePlayerComponent` 播放组件
3. 测试录制、播放、删除功能
4. 测试权限请求流程
5. 测试后台播放（前台服务）

如有问题，请参考各组件的详细注释或联系开发者。
