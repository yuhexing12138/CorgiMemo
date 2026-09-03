# free-font（收录商用免费字体）调研报告

> 调研对象：`jaywcjlove/free-font` —— GitHub 上最大的可商用中英文字体合集之一
> 调研日期：2026-09-03
> 本地位置：`C:\Users\EDY\Desktop\CorgiMemo\free-font\`

---

## 一、仓库概览

| 项目 | 内容 |
| --- | --- |
| 仓库 | `jaywcjlove/free-font` |
| 简介 | Collection of Free English/Chinese Fonts for Commercial Use（收录可商用的免费英文/汉字字体） |
| 作者 | Kenny Wong（jaywcjlove） |
| Star / Fork | **4,036 / 260** |
| 仓库协议 | **MIT**（注意：仅指仓库代码，字体各有独立授权） |
| 主语言 | JavaScript（静态站生成） |
| 创建时间 | 2024-07-16 |
| 最近推送 | 2026-08-04 |
| 默认分支 | `main` |
| 官网 | https://wangchujiang.com/free-font/ |
| 镜像站 | haoziku.com、font.icu、Vercel、GitHub Pages、Githack、ittools.cc |

**起源**：基于已停止维护的「字集」项目（[wordshub/free-font](https://github.com/wordshub/free-font)）重建。作者新增了自动生成字体预览封面的脚本，并补充了大量中文字体与开源英文字体，最初是为了服务其《字帖宝宝》应用。

**仓库本身是 MIT 协议，但这不等于里面的字体可商用**——每个字体有自己的授权条款，这是使用该库的第一注意事项。

---

## 二、本地拉取情况

### 拉取结果

| 项 | 数值 |
| --- | --- |
| 路径 | `C:\Users\EDY\Desktop\CorgiMemo\free-font\` |
| 方式 | `git clone --depth=1`（浅克隆，只取最新快照） |
| 耗时 | **25 分 51 秒** |
| 文件数 | **4,272** 个 |
| 磁盘占用 | **13 GB**（`.git` 4.2 GB + 字体工作区 8.39 GB） |
| HEAD | `8602c6e4` |

### 踩坑记录：环境代理导致 GitHub 不可达

本机环境变量注入了代理 `http://127.0.0.1:56997`，该代理对 `github.com` 的 CONNECT 隧道返回 **502**，导致 `git clone` 直接失败：

```
fatal: unable to access 'https://github.com/...': CONNECT tunnel failed, response 502
```

**解决办法**：克隆时临时剥离代理环境变量即可直连成功：

```bash
env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY \
  git clone --depth=1 https://github.com/jaywcjlove/free-font.git free-font
```

**为什么必须用 `--depth=1`**：仓库完整历史达 4.74 GB，全量克隆会再翻倍。浅克隆后 `.git` 仅 4.2 GB。若日后需要完整历史，可 `git fetch --unshallow`。

### ⚠️ 重要：建议加入 .gitignore

拉取后主仓库 `git status` 显示为 `?? free-font/`（未跟踪）。**13 GB 绝对不应纳入 CorgiMemo 主仓库**——无论是作为普通目录还是子模块（项目现有子模块 `compose-rich-editor` / `Reorderable` / `tyme4kt` 都是轻量代码库）。

建议在主仓库 `.gitignore` 末尾追加：

```gitignore
# ============================================================
# 字体素材库（13GB，本地参考用，不纳入版本跟踪）
# ============================================================
/free-font/
```

---

## 三、项目组织方式

### 目录结构

```
free-font/
├── README.md               # 项目说明（16 KB）
├── package.json            # npm 脚本入口
├── .ejscrc.mjs             # EJS 模板配置（9.7 KB）
├── scripts/                # ★ 构建与数据源
│   ├── data.json           # ★★ 字体元信息库（631 KB，1,048 条）
│   ├── main.mjs            # 主流程（6.9 KB）
│   ├── utils.mjs           # 字体解析工具（12 KB）
│   ├── olddata.js          # 继承字集项目的旧数据（100 KB）
│   ├── poster.html         # 预览海报模板
│   ├── preview.html        # 预览页模板
│   └── rename-images.mjs   # 图片重命名
├── templates/              # EJS 页面模板
├── docs/                   # ★ 生成的静态站（GitHub Pages 源）
│   ├── fonts/              # ★★ 字体文件（81 个系列，8.39 GB）
│   ├── index.html          # 首页（584 KB）
│   ├── hei.html            # 黑体（92 KB）
│   ├── song.html           # 宋体（58 KB）
│   ├── kai.html            # 楷体（33 KB）
│   ├── art.html            # 艺术体（110 KB）
│   ├── handwriting.html    # 手绘体（54 KB）
│   ├── english.html        # 英文字体（207 KB）
│   ├── open-source.html    # 开源字体（418 KB）
│   ├── preview.html / preview.en.html  # 预览页
│   ├── css/ js/ images/ icons/ appicon/ details/
│   └── sitemap.txt         # 站点地图（263 KB）
├── Dockerfile              # Docker 部署（镜像 wcjiang/free-font）
└── LICENSE                 # MIT
```

### 数据流向

```
字体文件放入 docs/fonts/<系列>/<字族>/
        ↓  scripts/main.mjs 扫描解析（提取 psName / 字形数 / 版本 / 体积）
scripts/data.json   ← 自动生成字段（byte/size/ctime/postscriptName/version…）
        ↓  人工补充字段（type 分类 / license 授权 / home 官网 / baidu 网盘）
        ↓  EJS 模板渲染
docs/*.html 静态站（按分类生成页面）
```

### data.json 字段说明

| 字段 | 说明 | 来源 |
| --- | --- | --- |
| `name` | 字体名（取自文件名） | 自动 |
| `path` | 相对 `docs/` 的路径 | 自动 |
| `byte` / `size` | 字节数 / 人类可读体积 | 自动 |
| `ctime` | 创建时间戳 | 自动 |
| `postscriptName` / `fullName` / `familyName` / `subfamilyName` | 字体内部名称元数据 | 自动 |
| `version` / `numGlyphs` / `copyright` | 版本 / **字形数** / 版权声明 | 自动 |
| `type` | **分类**：黑体/宋体/楷体/艺术体/手绘体 | 人工 |
| `license` | **授权**：商免 / OFL-1.1 / IPA-1.0 / MIT … | 人工 |
| `home` / `baidu` / `officialDownload` | 官网 / 百度网盘码 / 是否官网直下 | 人工 |

**分类规则**：`type` 字段决定中文字体归类；**英文字体不需要填 type**——放入 `docs/fonts/english/` 目录即自动归为「英文字体」；`open-source.html` 页面则筛选开源协议的字体。

### 本地构建命令

```bash
npm install
npm run one  -- ./docs/fonts/english/Prima/Prima-Regular.otf  # 增量生成单个预览海报（推荐）
npm run all                                                   # 全量生成（很慢，不推荐）
npm run dev                                                   # 监听模板变化生成站点
npm run start                                                 # 一次性生成站点
```

Docker 一键预览：

```bash
docker pull wcjiang/free-font:latest
docker run --name reference --rm -d -p 9677:3000 wcjiang/free-font:latest
```

---

## 四、收录内容统计

### 总量

| 指标 | 数值 |
| --- | --- |
| 字体文件数 | **1,048** |
| data.json 记录数 | **1,048**（与实际文件**一一对应，数据一致性良好**） |
| 字体总体积 | **8.39 GB** |
| 字体系列目录 | **81** 个 |
| 单字体最大 | 全字庫正楷體-98_1（49.7 MB） |

### 格式分布

| 格式 | 数量 | 占比 |
| --- | --- | --- |
| `.ttf` | 806 | 76.9% |
| `.otf` | 177 | 16.9% |
| `.ttc` | 65 | 6.2% |

> 全部为**静态字重字体**（每个字重一个独立文件）。**本库不收录可变字体（Variable Font）**。

### 分类分布（`type` 字段）

| 分类 | 数量 | 占比 |
| --- | --- | --- |
| **（未分类）** | **479** | **45.7%** |
| 艺术体 | 185 | 17.7% |
| 黑体 | 156 | 14.9% |
| 宋体 | 94 | 9.0% |
| 手绘体 | 84 | 8.0% |
| 楷体 | 50 | 4.8% |

> 「未分类」占近一半，主因是**英文字体不需要填 type**（365 个英文文件靠 `english/` 目录自动归类），加上部分中文字体漏标。

### 授权分布（`license` 字段）

| 授权 | 数量 | 占比 | 商用友好度 |
| --- | --- | --- | --- |
| **OFL-1.1** | **690** | 65.8% | ✅ 最佳（不传染使用作品） |
| **（未标注）** | **302** | 28.8% | ⚠️ 需自行核实 |
| IPA-1.0 | 21 | 2.0% | ✅ 开源合规 |
| AGPL-3.0 | 12 | 1.1% | ⚠️ 强传染性 |
| MIT | 10 | 1.0% | ✅ |
| 个人免费 | 4 | 0.4% | ❌ 不可商用 |
| GPL-2.0 | 3 | 0.3% | ⚠️ 传染性（有字体例外条款） |
| 需要授权 | 2 | 0.2% | ❌ |
| GPL-3.0 | 1 | 0.1% | ⚠️ 传染性 |
| CC BY-NC 4.0 | 1 | 0.1% | ❌ 禁止商业使用 |
| 个人非商业 | 1 | 0.1% | ❌ 禁止商业使用 |
| BSD-3-Clause | 1 | 0.1% | ✅ |

**授权口径**：合计 **OFL-1.1 + IPA + MIT + BSD = 722 条（68.9%）确认为开源友好**；**约 8 条明确不可商用**；**302 条（28.8%）无标注，商用前必须逐个回到官方源核实**。

### 下载渠道覆盖

- 含官网链接（`home`）：**895** 条（85.4%）
- 含百度网盘（`baidu`）：**908** 条（86.6%）—— 针对国内网络优化的镜像下载
- 官网直下标记（`officialDownload`）：仅 1 条（该字段基本未启用）

### 文件数 TOP 系列

| 字体系列 | 文件数 |
| --- | --- |
| `english`（英文字体） | 365 |
| `其他字体` | 138 |
| `思源字体系列` | 56 |
| `梦源字体` | 54 |
| `寒蝉字型` | 39 |
| `ButKo` | 25 |
| `仓耳字体` | 22 |
| `明体系列` | 18 |

81 个系列中包含：思源系列、阿里巴巴普惠体、小米 MiSans、站酷系列、方正系列、汉仪字库、霞鹜、文泉驿、全字库系列、花园明朝、寒蝉字型、狮尾半月、秋空黑體、內海字體、霞鹜臻楷等主流开源中文厂商与社区字体。

---

## 五、数据质量观察（使用时需注意的坑）

1. **`subfamilyName` 不可靠，字重信息散落在文件名里**
   例如思源系列：`familyName = "Gen Shin Gothic P Medium"`，而 `subfamilyName` 恒为 `Regular` 或 `Bold`。若按 `subfamilyName` 判断字重会得到错误结论。**正确做法是从文件名（或 `familyName`）提取字重 token。**

2. **带字重后缀的 `familyName` 会被误拆成多个字族**
   如「梦源宋体-W3/W4/…/W9」各自的 `familyName` 不同，直接按 `familyName` 归组会把同一字族的 10 个档位拆成 10 个「字族」。**建议按文件所在目录归组。**

3. **近三成字体无授权标注（302 条）**
   `data.json` 的 `license` 是人工维护的，缺失率高。**商用前必须回到官方源核实原始授权**，不能只信该字段。

4. **超大字体缺失**
   README 明确声明：**超过 50 MB 的字体文件提交会被拒绝**（GitHub LFS 存储收费，项目无捐赠支持）。因此全字库 Ext-B 等超大字体（如全字庫正宋體-Ext-B-98_1.ttf）虽在 LFS track 示例中提及，实际不在仓库内。

5. **预览功能已关闭**
   字体文件超 1 GB 导致流量费用达几十美元且无捐赠收入，作者**移除了页面下方的字体预览功能**，只保留下载。文件中转仍可正常使用。

6. **无可变字体**
   全部 1,048 个文件均为静态字重。若需要可变字体（单文件含全字重轴），需去各字体官方仓库另行获取。

---

## 六、对 CorgiMemo 的实用价值

### 6.1 直击痛点：中文字体真实多字重

当前 CorgiMemo 灵感编辑页的 B1/B2/B3 字重档位受限于**系统默认字体只有 400/700/900 三个字面**，500 被量化合并进 700 导致 B1 与 B2 视觉相同，只能靠运行时像素探测把无独立字面的档位置灰。

**根本解法是内置一款提供完整字重阶梯的中文字体**。本库正好提供了现成的选型池。

### 6.2 多字重中文字体候选榜

按「文件所在目录」归组、从文件名提取字重后，中文字体中字重档数最多的前几名：

| 字族 | 档数 | 字形数 | 全套体积 | 授权字段 | 字重清单 |
| --- | --- | --- | --- | --- | --- |
| 梦源宋体 | **10** | 65,535 | 994.2 MB | OFL-1.1 | W1–W9, W27 |
| 梦源黑体 | **10** | 65,535 | 643.1 MB | OFL-1.1 | W1–W9, W27 |
| 小米 MiSans | **8** | 29,601 | 76.9 MB | 未标注 | Thin/ExtraLight/Light/Regular/Medium/SemiBold/Bold/Heavy |
| 獅尾半月字體 | 7 | 44,474 | 182.1 MB | OFL-1.1 | Thin/Light/DemiLight/Regular/Medium/Bold/Black |
| 初夏明朝體 | 7 | 35,547 | 161.8 MB | OFL-1.1 | ExtraLight/Light/Regular/Medium/SemiBold/Bold/Heavy |
| 源样明体 | 7 | 31,156 | 84.6 MB | 未标注 | ExtraLight/Light/Regular/Medium/SemiBold/Bold/Heavy |
| 思源宋体 | 7 | 30,938 | 97.2 MB | 未标注 | ExtraLight/Light/Regular/Medium/SemiBold/Bold/Heavy |
| 源音黑體 | 6 | 65,535 | 120.6 MB | OFL-1.1 | ExtraLight/Light/Regular/Medium/Bold/Heavy |
| 秋空黑體 | 6 | 50,713 | 161.4 MB | OFL-1.1 | ExtraLight/Light/Regular/Medium/Bold/Heavy |
| **思源黑体** | 6 | 30,888 | **55.8 MB** | 未标注 | ExtraLight/Light/Regular/**Medium**/**Bold**/**Heavy** |
| **阿里巴巴普惠体** | 5 | 28,987 | **38.6 MB** | 未标注 | Light/Regular/**Medium**/**Bold**/**Heavy** |

### 6.3 首选推荐：思源黑体 / 阿里巴巴普惠体

这两款在**档位匹配度、体积、字形覆盖**三者上取得最佳平衡。

**思源黑体**（`docs/fonts/思源字体系列/思源黑体/`，已核对实际文件）：

| 文件 | 体积 |
| --- | --- |
| 思源黑体-ExtraLight.otf | 7.4 MB |
| 思源黑体-Light.otf | 8.0 MB |
| 思源黑体-Normal.otf | 8.0 MB |
| 思源黑体-Regular.otf | 8.0 MB |
| **思源黑体-Medium.otf** | **8.1 MB** |
| **思源黑体-Bold.otf** | **8.3 MB** |
| **思源黑体-Heavy.otf** | **8.4 MB** |

**关键结论**：`Medium(500)` / `Bold(700)` / `Heavy(900)` **正好精确对应 CorgiMemo 的 B1/B2/B3 三档**，且每档约 8 MB——只内置这 3 档约 **24.8 MB**，全套 6 档也仅 55.8 MB。

若追求更小体积，**阿里巴巴普惠体** 5 档全套仅 38.6 MB，同样具备 Medium/Bold/Heavy 三档，是 APK 体积敏感时的更优解。

**小米 MiSans** 则是档数最多（8 档）且体积可控（77 MB）的选择，含完整的 Thin→Heavy 阶梯，适合未来做更细的字重分级。

### 6.4 集成到 CorgiMemo 的路径

结合项目现有架构（`Type.kt` 集中管理字体定义与字重常量）：

1. **拷贝而非子模块**：从 `free-font/docs/fonts/<系列>/` 只拷贝需要的 3–6 个 `.otf/.ttf` 到 `app/src/main/res/font/`（**不要把 13 GB 目录作为子模块**）。
2. **建 FontFamily**：在 `app/src/main/res/font/` 建 `font` 资源族（或 `font_certs` + 可下载字体）。
3. **改 `Type.kt`**：把各 `TextStyle` 的 `fontFamily = FontFamily.Default` 换成自定义族。
4. **同步字重常量**：更新字重可用集合常量（当前为人工维护的设备字面集合），改为该字体的真实字重集合，档位即自动派生出 500/700/900 三档分明。
5. **像素探测可保留**：`FontWeightProbe` 作为兜底仍然有效——换设备或换字体后仍能自动识别无独立字面的档位并置灰。

### 6.5 体积控制建议

中文全字库单字重普遍 8–30 MB，内置多档会显著推高 APK 体积：

- **只内置必需档位**：3 档（Medium/Bold/Heavy）≈ 24 MB，而非全套 6 档 55 MB。
- **Android App Bundle**：按设备分发，减小用户实际下载体积。
- **可下载字体（Downloadable Fonts）**：Android 8.0+ 支持按需下载，但笔记类 App 对离线可用性要求高，需权衡。
- **子集化**：若只做正文显示，可对字体做子集裁剪（保留常用 3500 字 + GB2312），体积可压到 1/3 以下。

---

## 七、风险与注意事项

1. **商用授权必须以官方为准**：`data.json` 的 `license` 字段为人工维护，302 条缺失，且标注可能有误。上线商用前，对选定的每一款字体都要回到官方发布页确认授权条款原文。
2. **避开传染性协议**：AGPL-3.0（12 条）、GPL-2.0/3.0（4 条）字体若内置进 App，可能触发开源传染义务，集成前务必评估（GPL 字体例外条款可缓解，但文泉驿系列作者明确提示需谨慎）。
3. **明确不可商用的要排除**：`个人免费`（4）、`个人非商业`（1）、`需要授权`（2）、`CC BY-NC 4.0`（1）共 8 条，禁止用于商业产品。
4. **厂商可收回免费商用授权**：免费商用不等于永久授权，厂商有权调整条款，需在产品中保留字体版本与授权快照以便追溯。
5. **仓库自身无担保**：README 明确声明「所有字体版权归原作者所有，本站不承担任何法律问题或风险」「无法完全保证所有收录字体都不涉及商业用途」。

---

## 八、速查附录

**关键路径（本地）**

| 用途 | 路径 |
| --- | --- |
| 字体文件根目录 | `free-font\docs\fonts\` |
| 字体元信息库 | `free-font\scripts\data.json`（631 KB / 1,048 条） |
| 生成的静态站 | `free-font\docs\index.html` 等 9 个分类页 |
| 思源黑体（推荐） | `free-font\docs\fonts\思源字体系列\思源黑体\` |
| 阿里巴巴普惠体 | `free-font\docs\fonts\阿里巴巴普惠体\` |
| 小米 MiSans | `free-font\docs\fonts\小米\小米\` |
| 英文字体 | `free-font\docs\fonts\english\`（365 个） |

**常用命令**

```bash
# 重新拉取（如需重装，注意剥离代理并浅克隆）
env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY \
  git clone --depth=1 https://github.com/jaywcjlove/free-font.git free-font

# 查看某字族的全部字重文件
ls free-font/docs/fonts/思源字体系列/思源黑体/

# 更新到最新（浅克隆仓库内）
cd free-font && env -u http_proxy -u https_proxy git pull --depth=1
```

**按授权筛选可商用字体的思路**：读取 `scripts/data.json`，筛 `license in (OFL-1.1, IPA-1.0, MIT, BSD-3-Clause)`，再按 `type` 与 `numGlyphs` 排序即可快速得到「开源友好 + 中文覆盖好」的候选清单。

---

## 九、结论

`jaywcjlove/free-font` 是目前**中文领域规模领先的可商用字体聚合库**：1,048 款字体、81 个系列、8.39 GB、约 69% 为开源友好授权，且元信息库（`data.json`）与实际文件严格一一对应、字段结构清晰（含字形数、PostScript 名、版权声明等），非常适合作为**字体选型的检索与对比数据源**。

对 CorgiMemo 而言，它的核心价值在于：**提供了解决「中文字重撞档」问题的现成选型池**。推荐优先评估**思源黑体**（Medium/Bold/Heavy 三档精确对应 B1/B2/B3，约 24.8 MB）或**阿里巴巴普惠体**（5 档 38.6 MB，体积更优），拷贝所需档位到 `res/font` 后同步 `Type.kt` 的字体与字重常量即可让三档字重视觉分明。

需要注意：该库**只解决「去哪找」的问题，不解决「能不能用」的问题**——最终商用授权必须逐款回到官方核实。
