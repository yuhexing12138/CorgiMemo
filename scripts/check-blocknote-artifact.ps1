<# check-blocknote-artifact.ps1
   校验 BlockNote 编辑器产物（editor.html）是否由当前源码构建。

   背景：
       app/src/main/assets/blocknote-web/editor/editor.html 是 **构建生成物**
       （vite singleFile 打包），不在编辑 JS 源码的同一批文件里。本项目已两次
       踩到「源码改了、也提交了，但产物没重建」——提交里只有源码，真机继续跑旧
       bundle，表现为"改了没生效"，且从提交记录上完全看不出来。

   原理：
       构建时 vite.editor.config.ts 会把 blocknote-probe 的
       「editor.html + src/editor/ 全量内容」算成 sha256 前 12 位，
       以字面量 `bn-src:xxxxxxxxxxxx` 注入产物（见 bridge.ts 的 SRC_HASH）。
       本脚本用**完全相同**的口径现算一次，与产物里的值比对：
         一致   → 产物是最新的
         不一致 → 产物落后于源码，需要重新执行构建
         未找到 → 产物是加哈希之前构建的（或构建失败），同样视为需要重建

   用法（PowerShell）：
       .\scripts\check-blocknote-artifact.ps1              # 校验，不一致时 exit 1
       .\scripts\check-blocknote-artifact.ps1 -WarnOnly   # 只告警，恒 exit 0
       .\scripts\check-blocknote-artifact.ps1 -StagedOnly # 仅当暂存区含 src 改动时才校验
       .\scripts\check-blocknote-artifact.ps1 -PrintOnly  # 只打印当前源码哈希

   接 pre-commit：钩子文件是仓库内的 .githooks/pre-commit（本仓库已配置
   core.hooksPath = .githooks），它以 -StagedOnly 调用本脚本。

   哈希口径必须与 vite.editor.config.ts 的 collectSrcHash() 逐条对齐，任何一处
      改动都要两边同步，否则会出现"恒等不一致"的假警报：
        ① 只算文件原始字节（与换行符 / 编码无关）；
        ② 范围 = editor.html + src/editor/ 全量，相对 blocknote-probe 的路径；
        ③ 路径分隔符统一为 `/`、无前导斜杠、按 **码元序（Ordinal）** 升序排列；
        ④ 每条记录按「相对路径(UTF-8) + 文件字节」顺序喂进同一个 sha256。
#>
[CmdletBinding()]
param(
    # 只告警不阻断（不一致也返回 0）
    [switch]$WarnOnly,
    # 仅当暂存区里含 blocknote-probe/src/ 的改动时才校验；否则直接跳过（供 pre-commit 使用，零误伤）
    [switch]$StagedOnly,
    # 只打印当前源码哈希，不做校验（便于手动与 logcat 的 src= 比对）
    [switch]$PrintOnly
)

$ErrorActionPreference = 'Stop'

<#
   统一的失败出口。
   不用 Write-Error：在 $ErrorActionPreference='Stop' 下它会**抛异常中断脚本**，
   后续的 exit 1 根本不会执行，输出也会被包成异常记录（git hook 里刷一屏栈）。
   这里显式打红字 + exit 1，退出码确定、输出干净。
#>
function Fail([string]$Message) {
    Write-Host "[blocknote-artifact] [FAIL] $Message" -ForegroundColor Red
    exit 1
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $ScriptDir '..'))
$ProbeDir = Join-Path $RepoRoot 'blocknote-probe'
$Artifact = Join-Path $RepoRoot 'app\src\main\assets\blocknote-web\editor\editor.html'
$ArtifactRel = 'app/src/main/assets/blocknote-web/editor/editor.html'
$HashMarker = 'bn-src:'

# ---------- pre-commit 场景：本次提交没碰编辑器源码就跳过 ----------
if ($StagedOnly) {
    $staged = & git -C $RepoRoot diff --cached --name-only 2>$null
    $touchesSrc = @($staged | Where-Object { $_ -like 'blocknote-probe/src/*' }).Count -gt 0
    if (-not $touchesSrc) {
        Write-Host '[blocknote-artifact] 本次提交未涉及 blocknote-probe/src，跳过产物校验。'
        exit 0
    }
}

# ---------- 计算当前源码哈希（与 vite.editor.config.ts 同口径）----------
$fileList = New-Object System.Collections.Generic.List[object]

<#
   递归收集文件并保序追加。
   @param AbsPath  待处理的绝对路径（文件或目录）
   排序用 [StringComparer]::Ordinal —— 对齐 JS `Array.prototype.sort()` 的码元序，
   文化相关的默认排序在含非 ASCII 文件名时会与 Node 侧产生不同顺序 → 哈希不等。
#>
function Collect-Files([string]$AbsPath) {
    if (Test-Path -LiteralPath $AbsPath -PathType Container) {
        <#
           排序必须与 Node 的 `readdirSync(dir).sort()` 一致 = **UTF-16 码元序（Ordinal）**。

           这里不能用 `[Array]::Sort($names, [System.StringComparer]::Ordinal)`：
           `Get-ChildItem | Select -ExpandProperty Name` 得到的是 **PSObject[]**，
           重载解析会挑中 `Sort(Array)`（不带比较器那个），comparer **静默失效**，
           实际按文化敏感规则排序 —— 结果 `bridge.ts < editor.css < EditorApp.tsx`
           （大小写被忽略），而 Node 是 `EditorApp.tsx < bridge.ts < editor.css`。
           顺序不同 → 哈希恒不相等 → 脚本会一直误报"产物过期"（已实测踩到）。

           改用 `List[string].Sort(IComparer<string>)`：泛型实例方法，
           Add 时即完成字符串转换，不走 PSObject 包装，比较器确定生效。
        #>
        $nameList = New-Object 'System.Collections.Generic.List[string]'
        foreach ($item in (Get-ChildItem -LiteralPath $AbsPath -Force)) {
            $nameList.Add([string]$item.Name)
        }
        $nameList.Sort([System.StringComparer]::Ordinal)
        foreach ($n in $nameList) { Collect-Files (Join-Path $AbsPath $n) }
        return
    }
    $rel = $AbsPath.Substring($ProbeDir.Length).Replace('\', '/').TrimStart('/')
    $fileList.Add([pscustomobject]@{ Rel = $rel; Full = $AbsPath })
}

foreach ($entry in @('editor.html', 'src\editor')) { Collect-Files (Join-Path $ProbeDir $entry) }

# 把所有「相对路径 + 内容」拼成一个字节流后一次性摘要（Node 侧是流式 update，结果等价）
$stream = New-Object System.Collections.Generic.List[byte]
$utf8 = [System.Text.Encoding]::UTF8
foreach ($f in $fileList) {
    $stream.AddRange($utf8.GetBytes($f.Rel))
    $stream.AddRange([System.IO.File]::ReadAllBytes($f.Full))
}
$sha = [System.Security.Cryptography.SHA256]::Create()
try {
    $digest = ($sha.ComputeHash($stream.ToArray()) | ForEach-Object { $_.ToString('x2') }) -join ''
}
finally { $sha.Dispose() }
$current = "$HashMarker$($digest.Substring(0, 12))"

Write-Host "[blocknote-artifact] 源码哈希：$current（$($fileList.Count) 个文件）"

if ($PrintOnly) { exit 0 }

# ---------- 从产物中取出构建时写入的哈希 ----------
if (-not (Test-Path -LiteralPath $Artifact)) {
    $msg = "未找到产物：$Artifact —— 请先执行构建（npm run build:editor 或 gradlew :app:buildBlockNoteEditor）"
    if ($WarnOnly) { Write-Warning "[blocknote-artifact] $msg"; exit 0 } else { Fail $msg }
}

$html = [System.IO.File]::ReadAllText($Artifact, [System.Text.Encoding]::UTF8)
# 变量名不用 $matches：那是 -match 的自动变量，同名会把它冲掉
$hashHits = [regex]::Matches($html, [regex]::Escape($HashMarker) + '([0-9a-f]{12})')
$embedded = if ($hashHits.Count -gt 0) { $hashHits[0].Value } else { $null }

if (-not $embedded) {
    $msg = @(
        "产物中未找到源码哈希标记（$HashMarker），说明它是加哈希之前构建的旧产物。",
        "请重新构建：npm run build:editor（或 gradlew :app:buildBlockNoteEditor --rerun-tasks）"
    ) -join ' '
    if ($WarnOnly) { Write-Warning "[blocknote-artifact] $msg"; exit 0 } else { Fail $msg }
}

if ($embedded -eq $current) {
    Write-Host "[blocknote-artifact] [OK] 产物与源码一致（$embedded）"
} else {
    $msg = @(
        "产物已过期：产物内为 $embedded，当前源码算出 $current。",
        "即「源码改了但产物没重建」——真机将继续运行旧 bundle。",
        "请执行：npm run build:editor（产物输出到 app/src/main/assets/blocknote-web/editor/editor.html）"
    ) -join ' '
    if ($WarnOnly) { Write-Warning "[blocknote-artifact] $msg"; exit 0 } else { Fail $msg }
}

# ---------- 顺带提醒：产物重建了但没加进暂存区（pre-commit 场景常见）----------
if ($StagedOnly) {
    $stagedArtifact = @(& git -C $RepoRoot diff --cached --name-only -- $ArtifactRel 2>$null)
    if ($stagedArtifact -notcontains $ArtifactRel) {
        $null = & git -C $RepoRoot diff --quiet -- $ArtifactRel 2>$null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "[blocknote-artifact] 产物已在工作区重建，但**未加入暂存区**，本次提交不会带上它（记得 git add）。"
        }
    }
}
