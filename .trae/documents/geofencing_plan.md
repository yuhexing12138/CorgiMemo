# CorgiMemo 地理位置提醒功能实现计划

## 当前项目分析

**已有资源：**
- ✅ 待办 CRUD 功能已实现
- ✅ TodoItem 实体已有提醒时间字段

**需要实现的内容：**
1. 集成高德地图 SDK（地图显示、位置搜索、地理围栏）
2. 在 TodoEditScreen 添加位置提醒选项
3. 实现地理围栏监听服务
4. 处理位置权限请求
5. 添加通知提醒

---

## 实现步骤

### 1. 集成高德地图 SDK
**文件：** `build.gradle.kts`（模块级）、`AndroidManifest.xml`

**功能：**
- 添加高德地图依赖
- 配置 API Key
- 添加必要权限（ACCESS_FINE_LOCATION、ACCESS_BACKGROUND_LOCATION）

### 2. 更新 TodoItem 实体
**文件：** `data/model/TodoItem.kt`

**功能：**
- 添加地理围栏相关字段：
  - geofenceLat: Double?（纬度）
  - geofenceLng: Double?（经度）
  - geofenceRadius: Float?（触发半径，单位米）
  - geofenceType: Int（触发类型：0到达/1离开）
  - geofenceEnabled: Boolean（是否启用地理围栏）

### 3. 创建位置选择组件
**文件：** `ui/components/LocationPicker.kt`

**功能：**
- 显示地图界面
- 支持搜索位置
- 选择触发半径
- 设置到达/离开触发类型

### 4. 创建地理围栏服务
**文件：** `service/GeofenceService.kt`

**功能：**
- 使用 WorkManager 或 JobScheduler 监听位置
- 管理地理围栏注册
- 触发位置提醒时显示通知

### 5. 更新 TodoEditScreen
**文件：** `ui/screens/todo/TodoEditScreen.kt`

**功能：**
- 添加位置提醒开关
- 集成 LocationPicker 组件
- 保存地理围栏配置

### 6. 创建 GeofenceRepository
**文件：** `data/repository/GeofenceRepository.kt`

**功能：**
- 管理地理围栏的注册和取消注册
- 与高德地图 API 交互

---

## 技术实现

### 地理围栏工作流程
```
用户设置位置提醒 → 保存待办时注册地理围栏 → 系统后台监听 → 到达/离开触发点 → 显示通知提醒
```

### 触发类型
| 类型 | 值 | 说明 |
|------|-----|------|
| 到达 | 0 | 进入围栏区域时触发 |
| 离开 | 1 | 离开围栏区域时触发 |

### 权限需求
- **ACCESS_FINE_LOCATION** - 精确定位权限
- **ACCESS_COARSE_LOCATION** - 粗略定位权限（备用）
- **ACCESS_BACKGROUND_LOCATION** - 后台定位权限（Android 10+）

---

## 修改的文件

| 文件路径 | 修改内容 |
|----------|----------|
| `app/build.gradle.kts` | 添加高德地图依赖 |
| `AndroidManifest.xml` | 添加位置权限、高德 API Key |
| `data/model/TodoItem.kt` | 添加地理围栏字段 |
| `data/local/db/TodoDao.kt` | 更新数据库操作 |
| `data/repository/TodoRepository.kt` | 更新仓库方法 |
| `ui/components/LocationPicker.kt` | 新建 - 位置选择组件 |
| `service/GeofenceService.kt` | 新建 - 地理围栏服务 |
| `ui/screens/todo/TodoEditScreen.kt` | 添加位置提醒选项 |

---

## 高德地图配置

### 依赖配置
```kotlin
// 高德地图基础 SDK
implementation 'com.amap.api:map2d:latest'
implementation 'com.amap.api:search:latest'
implementation 'com.amap.api:location:latest'
```

### Manifest 配置
```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="YOUR_API_KEY" />
```

---

## 设计规范

- 位置提醒作为待办的可选功能
- 使用高德地图显示地图和搜索位置
- 支持设置触发半径（50-500米）
- 到达/离开两种触发模式
- 后台监听使用 WorkManager
- 触发时显示系统通知

---

## 注意事项

1. **API Key 获取**：需要在高德地图开发者平台注册获取 API Key
2. **后台定位权限**：Android 10+ 需要特殊处理
3. **电池优化**：需要引导用户将应用加入电池优化白名单
4. **兼容性**：考虑低版本 Android 的兼容性处理
