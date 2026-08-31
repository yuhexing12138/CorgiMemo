# CorgiMemo 项目长期记忆

## 项目约定
- **不需要自动编译**：完成 Kotlin/代码改动后，不要主动运行 `./gradlew` 编译或构建验证（Gradle 在本机还常因文件写权限 `AccessDeniedException` 失败）；除非用户明确要求，跳过构建。
