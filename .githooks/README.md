# .githooks/ — 仓库内 Git Hook 目录

本目录存放**随仓库分发**的 Git Hook。`.git/hooks/` 不被 Git 跟踪，因此把 hook 放这里 + `core.hooksPath` 指向本目录，是让协作者共享 hook 的标准做法。

## 当前包含的 Hook

| 文件 | 触发时机 | 作用 |
| --- | --- | --- |
| `post-commit` | 每次 `git commit` 之后 | 调用 `graphify update .` 增量重建知识图谱 |
| `post-checkout` | `git checkout` / `git switch` 切换分支时 | 同上，确保切分支后图谱与代码一致 |

两个 hook 都由 `graphify hook install` 自动生成；`update` 阶段只扫描代码文件（`--code-only`），不消耗 LLM token。

## 在新机器 / 新协作者 clone 后启用

clone 完成后**只需一行**：

```bash
git config core.hooksPath .githooks
```

之后 `post-commit` / `post-checkout` 就会从本目录读取，无需再手动 `chmod`。

> Windows + Git Bash / WSL 环境下 Git 不严格检查可执行位，但仓库内的 hook 仍以 `100755` 模式提交，保证 Linux/macOS 协作者 clone 后能直接运行。

## 关闭 / 跳过

临时跳过本次 hook：

```bash
GRAPHIFY_SKIP_HOOK=1 git commit -m "..."
```

彻底回退到默认 `.git/hooks/`：

```bash
git config --unset core.hooksPath
```

## 重新安装

若 hook 文件被误删或被 `graphify hook install` 重新覆盖，可用：

```bash
# 重新生成到 .git/hooks/，再拷回本目录
.venv\Scripts\graphify.exe hook install
Copy-Item .git/hooks/post-commit .githooks/post-commit -Force
Copy-Item .git/hooks/post-checkout .githooks/post-checkout -Force
git add --chmod=+x .githooks/post-commit .githooks/post-checkout
```
