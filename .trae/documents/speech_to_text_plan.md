# CorgiMemo 语音转文字功能实现计划

## 当前项目分析

**已有资源：**
- ✅ TodoEditScreen 待办编辑页面已实现
- ✅ 项目已配置 Android 权限框架

**需要实现的内容：**
1. 创建语音识别工具类
2. 在 TodoEditScreen 添加麦克风按钮
3. 处理录音权限请求
4. 实现语音转文字逻辑

---

## 实现步骤

### 1. 创建语音识别工具类
**文件：** `util/SpeechRecognizerHelper.kt`

**功能：**
- 封装 Android SpeechRecognizer API
- 处理语音识别回调
- 提供开始/停止录音方法
- 处理权限检查和请求

### 2. 创建语音识别状态管理
**文件：** `viewmodel/SpeechViewModel.kt`

**功能：**
- 管理语音识别状态（空闲/录音中/识别中/完成）
- 处理权限状态
- 暴露识别结果

### 3. 更新 TodoEditScreen
**文件：** `ui/screens/todo/TodoEditScreen.kt`

**功能：**
- 在标题输入框右侧添加麦克风按钮
- 录音时显示动画提示
- 将识别结果填入输入框
- 显示错误提示

### 4. 更新 AndroidManifest.xml
**文件：** `src/main/AndroidManifest.xml`

**功能：**
- 添加 RECORD_AUDIO 权限声明

---

## 技术实现

### 语音识别流程
```
用户点击麦克风 → 检查权限 → 请求权限（如需）→ 开始录音 → 语音识别 → 返回结果 → 填入输入框
```

### 状态管理
| 状态 | 说明 |
|------|------|
| IDLE | 空闲状态 |
| LISTENING | 正在录音 |
| PROCESSING | 正在识别 |
| COMPLETED | 识别完成 |
| ERROR | 出错 |

### 权限处理
- Android 6.0+ 需要 RECORD_AUDIO 运行时权限
- 首次请求时显示权限说明
- 拒绝后引导用户到设置页面开启权限

---

## 修改的文件

| 文件路径 | 修改内容 |
|----------|----------|
| `util/SpeechRecognizerHelper.kt` | 新建 - 语音识别工具类 |
| `viewmodel/SpeechViewModel.kt` | 新建 - 语音识别状态管理 |
| `ui/screens/todo/TodoEditScreen.kt` | 修改 - 添加麦克风按钮和录音界面 |
| `AndroidManifest.xml` | 修改 - 添加录音权限 |

---

## 设计规范

- 麦克风按钮放在标题输入框右侧
- 录音时显示波形动画或"正在聆听..."提示
- 识别结果自动填入标题输入框
- 支持中文语音识别
- 处理网络异常和权限拒绝情况
