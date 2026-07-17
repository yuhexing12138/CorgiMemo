# ============================================================
# graphify-watch.ps1 — graphify watch 后台进程管理
# ------------------------------------------------------------
# 作用：让 `graphify watch .` 在后台持续运行，代码改动时
#       自动触发增量图谱重建。
#
# 用法（在 PowerShell 中、仓库根目录下）：
#   .\scripts\graphify-watch.ps1 start     启动 watch（写入 .watch_active flag）
#   .\scripts\graphify-watch.ps1 stop      停止 watch（移除 flag）
#   .\scripts\graphify-watch.ps1 status    查看是否在跑 / 进程信息
#   .\scripts\graphify-watch.ps1 logs      查看最近 50 行日志
#   .\scripts\graphify-watch.ps1 restart   stop + start
# ------------------------------------------------------------
# 持久化文件：
#   graphify-out/.watch_active   flag 文件（存在 = watch 在跑）
#   graphify-out/.watch.pid      进程 PID
#   graphify-out/watch.log       watch 进程的 stdout + stderr
# ============================================================

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('start', 'stop', 'status', 'logs', 'restart', 'help')]
    [string]$Action,

    [int]$Tail = 50
)

# 路径解析
$ScriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path "$ScriptDir\..").Path
$GraphifyBin = Join-Path $ProjectRoot ".venv\Scripts\graphify.exe"
$OutDir      = Join-Path $ProjectRoot "graphify-out"
$FlagFile    = Join-Path $OutDir   ".watch_active"
$PidFile     = Join-Path $OutDir   ".watch.pid"
$LogFile    = Join-Path $OutDir   "watch.log"
$StdOutLog  = Join-Path $OutDir   "watch.out.log"
$StdErrLog  = Join-Path $OutDir   "watch.err.log"

# ----------------------------------------------------------
# 帮助
# ----------------------------------------------------------
function Show-Help {
    Write-Host ""
    Write-Host "graphify-watch.ps1 - 让 graphify watch 在后台持续运行" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "用法：" -ForegroundColor White
    Write-Host "  .\scripts\graphify-watch.ps1 start      启动 watch（写入 .watch_active flag）"
    Write-Host "  .\scripts\graphify-watch.ps1 stop       停止 watch（移除 flag）"
    Write-Host "  .\scripts\graphify-watch.ps1 restart    stop + start"
    Write-Host "  .\scripts\graphify-watch.ps1 status     查看运行状态 / 进程信息"
    Write-Host "  .\scripts\graphify-watch.ps1 logs       查看最近 50 行日志"
    Write-Host "  .\scripts\graphify-watch.ps1 logs -Tail 200   查看最近 200 行"
    Write-Host ""
    Write-Host "依赖：" -ForegroundColor White
    Write-Host "  - .venv\Scripts\graphify.exe 必须存在"
    Write-Host "  - graphify-out/ 目录必须存在（先跑过一次 graphify update .）"
    Write-Host ""
    Write-Host "提示：" -ForegroundColor White
    Write-Host "  启动后只要 graphify-out/.watch_active 存在，就代表 watch 在跑；"
    Write-Host "  CI / 其他脚本可以通过 Test-Path 判断是否启用。"
    Write-Host ""
}

# ----------------------------------------------------------
# 工具函数
# ----------------------------------------------------------
function Test-WatchRunning {
    if (-not (Test-Path $PidFile)) { return $false }
    $pidVal = Get-Content $PidFile -ErrorAction SilentlyContinue
    if (-not $pidVal) { return $false }
    $proc = Get-Process -Id $pidVal -ErrorAction SilentlyContinue
    return ($null -ne $proc)
}

function Get-WatchProcessInfo {
    if (-not (Test-Path $PidFile)) { return $null }
    $pidVal = Get-Content $PidFile -ErrorAction SilentlyContinue
    if (-not $pidVal) { return $null }
    $proc = Get-Process -Id $pidVal -ErrorAction SilentlyContinue
    if ($null -eq $proc) { return $null }
    $obj = New-Object PSObject -Property @{
        Pid          = $proc.Id
        Name         = $proc.ProcessName
        StartTime    = $proc.StartTime
        WorkingSetMB = [math]::Round($proc.WorkingSet64 / 1MB, 1)
    }
    return $obj
}

# ----------------------------------------------------------
# start
# ----------------------------------------------------------
function Start-Watch {
    if (-not (Test-Path $GraphifyBin)) {
        Write-Host ("ERROR: 找不到 " + $GraphifyBin + "，请先安装 graphify。") -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path $OutDir)) {
        Write-Host ("ERROR: 找不到 " + $OutDir + "，请先跑一次 graphify update .") -ForegroundColor Red
        exit 1
    }
    if (Test-WatchRunning) {
        $info = Get-WatchProcessInfo
        Write-Host ("watch 已经在跑（PID " + $info.Pid + "）。无需重复启动。") -ForegroundColor Yellow
        return
    }

    Write-Host "启动 graphify watch（后台进程）..." -ForegroundColor Cyan

    # 清理残留
    Remove-Item $FlagFile -ErrorAction SilentlyContinue
    Remove-Item $PidFile  -ErrorAction SilentlyContinue

    # 启动后台进程（PowerShell 不允许 stdout / stderr 指向同一文件，分开两个）
    $proc = Start-Process `
        -FilePath $GraphifyBin `
        -ArgumentList @('watch', '.') `
        -WorkingDirectory $ProjectRoot `
        -RedirectStandardOutput $StdOutLog `
        -RedirectStandardError  $StdErrLog `
        -WindowStyle Hidden `
        -PassThru

    # 写入 PID 与 flag
    $proc.Id | Out-File -FilePath $PidFile  -Encoding ascii -NoNewline
    "" | Out-File -FilePath $FlagFile -Encoding ascii

    # 启动校验
    Start-Sleep -Seconds 1
    if (Test-WatchRunning) {
        $info = Get-WatchProcessInfo
        Write-Host "watch 已启动" -ForegroundColor Green
        Write-Host ("  PID        : " + $info.Pid) -ForegroundColor Green
        Write-Host ("  进程名     : " + $info.Name) -ForegroundColor Green
        Write-Host ("  flag 文件  : " + $FlagFile) -ForegroundColor Green
        Write-Host ("  日志文件   : " + $LogFile) -ForegroundColor Green
        Write-Host ("  工作目录   : " + $ProjectRoot) -ForegroundColor Green
        Write-Host ""
        Write-Host "停止：  .\scripts\graphify-watch.ps1 stop" -ForegroundColor Gray
        Write-Host "状态：  .\scripts\graphify-watch.ps1 status" -ForegroundColor Gray
    } else {
        Write-Host "ERROR: watch 启动失败，请检查日志" -ForegroundColor Red
        Write-Host ("  日志: " + $LogFile) -ForegroundColor Red
        exit 1
    }
}

# ----------------------------------------------------------
# stop
# ----------------------------------------------------------
function Stop-Watch {
    if (-not (Test-WatchRunning)) {
        Write-Host "watch 没在跑（清理残留文件）..." -ForegroundColor Yellow
        Remove-Item $FlagFile -ErrorAction SilentlyContinue
        Remove-Item $PidFile  -ErrorAction SilentlyContinue
        return
    }

    $info = Get-WatchProcessInfo
    $pidVal = $info.Pid
    Write-Host ("停止 watch（PID " + $pidVal + "）...") -ForegroundColor Cyan

    try {
        Stop-Process -Id $pidVal -Force -ErrorAction Stop
    } catch {
        Write-Host ("WARN: Stop-Process 失败: " + $_.Exception.Message) -ForegroundColor Yellow
    }

    # 兜底再杀一次
    Get-Process -Name "graphify" -ErrorAction SilentlyContinue |
        Where-Object { $_.Id -eq $pidVal } |
        ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }

    Start-Sleep -Milliseconds 500

    Remove-Item $FlagFile -ErrorAction SilentlyContinue
    Remove-Item $PidFile  -ErrorAction SilentlyContinue

    Write-Host "watch 已停止" -ForegroundColor Green
}

# ----------------------------------------------------------
# status
# ----------------------------------------------------------
function Get-Status {
    $running = Test-WatchRunning
    $flagExists = Test-Path $FlagFile

    Write-Host ""
    Write-Host "graphify watch 状态" -ForegroundColor Cyan
    Write-Host "----------------------------------------"
    Write-Host ("flag 文件 : " + $FlagFile)
    if ($flagExists) {
        Write-Host "  存在 = 是"
    } else {
        Write-Host "  存在 = 否"
    }
    Write-Host ("PID 文件  : " + $PidFile)
    if (Test-Path $PidFile) {
        $pidVal = Get-Content $PidFile -ErrorAction SilentlyContinue
        Write-Host ("  PID = " + $pidVal)
    } else {
        Write-Host "  PID = (无)"
    }
    Write-Host ("日志文件  : " + $StdOutLog + " / " + $StdErrLog)
    Write-Host ""

    if ($running) {
        $info = Get-WatchProcessInfo
        Write-Host "[RUNNING]  watch 正在运行" -ForegroundColor Green
        Write-Host ("   PID        : " + $info.Pid) -ForegroundColor Green
        Write-Host ("   进程名     : " + $info.Name) -ForegroundColor Green
        Write-Host ("   启动时间   : " + $info.StartTime) -ForegroundColor Green
        Write-Host ("   内存占用   : " + $info.WorkingSetMB + " MB") -ForegroundColor Green
    } else {
        Write-Host "[STOPPED]  watch 未运行" -ForegroundColor Yellow
    }
}

# ----------------------------------------------------------
# logs
# ----------------------------------------------------------
function Show-Logs {
    if (-not (Test-Path $LogFile)) {
        Write-Host ("日志文件不存在: " + $LogFile) -ForegroundColor Yellow
        return
    }
    Get-Content $LogFile -Tail $Tail -ErrorAction SilentlyContinue
}

# ----------------------------------------------------------
# 入口
# ----------------------------------------------------------
switch ($Action) {
    'start'   { Start-Watch }
    'stop'    { Stop-Watch }
    'restart' { Stop-Watch; Start-Sleep -Seconds 1; Start-Watch }
    'status'  { Get-Status }
    'logs'    { Show-Logs }
    'help'    { Show-Help }
}
