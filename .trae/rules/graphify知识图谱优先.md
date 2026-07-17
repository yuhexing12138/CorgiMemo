---
alwaysApply: true
---

# Graphify 知识图谱优先规则

本项目已集成 graphify 知识图谱工具（位于 `./graphify/` 子模块，输出在 `./graphify-out/`）。
当回答以下类型问题时，**必须**先读 `graphify-out/GRAPH_REPORT.md` 和 `graphify-out/graph.json`，
再用 `graphify query / path / explain` 做精确查询，**不要**直接 grep 源码或凭记忆回答。

## 适用场景（必须走图谱）

- 架构、模块、调用关系、数据流分析
- "X 是怎么实现的？" / "哪些类调用了 W？" / "Y 和 Z 之间有什么关系？"
- 跨模块依赖、循环依赖、God Node（高耦合枢纽）识别
- 重构影响范围评估（"如果改 X 会影响哪些？"）
- 任何"全局视角"问题

## 工作流

```
1. 读 graphify-out/GRAPH_REPORT.md  （获取 God Nodes / Communities / Surprising Connections）
2. 按问题选命令：
   - 宽泛问题  →  graphify query "<问题>"
   - 路径问题  →  graphify path "节点A" "节点B"
   - 节点详情  →  graphify explain "节点名"
3. 基于图谱输出回答，引用 source_location
4. 必要时用 graph.json 二次验证
```

## 关键命令速查

| 命令 | 用途 | 成本 |
|---|---|---|
| `graphify . --code-only` | 首次全量建图 | 0 token（纯本地 AST） |
| `graphify update .` | 增量更新（代码改完后） | 0 token |
| `graphify query "<问题>"` | 广度优先查询 | 0 token |
| `graphify path "A" "B"` | 最短路径追溯 | 0 token |
| `graphify explain "X"` | 节点详细解释 | 0 token |
| `graphify watch .` | 持续监控自动重建 | 0 token（独立进程） |
| `graphify cluster-only .` | 重新聚类 | 0 token |

## 重要约束

- **graphify-out/ 已被 .gitignore 排除**，不要尝试 `git add graphify-out/`
- **本项目使用 `--code-only` 模式**（首次试运行 2026-07-17：4266 节点 / 7580 边 / 276 社区 / 0 token）
- 社区命名因未配 LLM backend 暂时是 "Community N" 占位符，不要误以为是最终名称
- 不读项目中的图片（柯基动画帧、SVG 等），图谱基于代码 AST
- 路径用正斜杠：`app/src/main/java/com/corgimemo/app/...`

## 当图谱不存在时

如果 `graphify-out/graph.json` 不存在，先跑：
```bash
.venv\Scripts\graphify.exe . --code-only
.venv\Scripts\graphify.exe cluster-only .
```
（首次约 90 秒，之后增量秒级）

## 优先级

- 图谱查询 > 源码 grep > 凭记忆推断
- 图谱结果如有矛盾（INFERRED vs EXTRACTED），优先信任 EXTRACTED
- 跨子模块（graphify、compose-rich-editor、Reorderable、tyme4kt）的边**不在**本项目图谱中
