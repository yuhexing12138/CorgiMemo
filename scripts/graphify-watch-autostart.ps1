# ============================================================
# graphify-watch-autostart.ps1 — graphify watch 自启动管理
# ------------------------------------------------------------
# 作用：把 graphify watch 注册到当前用户的 Run 注册表，
#       用户登录时自动启动 .\scripts\graphify-watch.ps1 start
#
# 用法（在 PowerShell 中，仓库根目录下）：
#   .\scripts\graphify-watch-autostart.ps1 install     注册 Run 启动项
#   .\scripts\graphify-watch-autostart.ps1 uninstall   删除 Run 启动项
#   .\scripts\graphify-watch-autostart.ps1 status      查看启动项 + watch 状态
# ------------------------------------------------------------
# 实现方式：HKCU\Software\Microsoft\Windows\CurrentVersion\Run
#   - 不需要管理员权限
#   - 用户登录时自动执行
#   - PowerShell 用 -WindowStyle Hidden 避免窗口闪烁
# ============================================================

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('install', 'uninstall', 'status')]
    [string]$Action
)

# 路径解析
$ScriptDir           = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot         = (Resolve-Path "$ScriptDir\..").Path
$GraphifyWatchScript = Join-Path $ScriptDir "graphify-watch.ps1"
$RunKey              = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
$ValueName           = "CorgiMemoGraphifyWatch"
$ValueData           = "powershell.exe -ExecutionPolicy Bypass -NoProfile -WindowStyle Hidden -File `"$GraphifyWatchScript`" start"

# ----------------------------------------------------------
# install
# ----------------------------------------------------------
function Install-Autostart {
    if (-not (Test-Path $GraphifyWatchScript)) {
        Write-Host ("ERROR: 找不到 " + $GraphifyWatchScript) -ForegroundColor Red
        exit 1
    }

    $existing = Get-ItemProperty -Path $RunKey -Name $ValueName -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host ("Run 启动项已存在：" + $RunKey + "\" + $ValueName) -ForegroundColor Yellow
        Write-Host "如需重装：先 uninstall 再 install"
        return
    }

    Write-Host "注册 Run 启动项..." -ForegroundColor Cyan
    Set-ItemProperty -Path $RunKey -Name $ValueName -Value $ValueData -Type String

    Write-Host "Run 启动项已注册" -ForegroundColor Green
    Write-Host ("  注册表项: " + $RunKey + "\" + $ValueName)
    Write-Host ("  执行命令: " + $ValueData)
    Write-Host ""
    Write-Host "下次登录时会自动启动 watch。" -ForegroundColor Cyan
    Write-Host "立即测试（不重启）：手动执行注册表项里的命令。" -ForegroundColor Gray
    Write-Host ""
    Write-Host "卸载：.\scripts\graphify-watch-autostart.ps1 uninstall" -ForegroundColor Gray
}

# ----------------------------------------------------------
# uninstall
# ----------------------------------------------------------
function Uninstall-Autostart {
    $existing = Get-ItemProperty -Path $RunKey -Name $ValueName -ErrorAction SilentlyContinue
    if (-not $existing) {
        Write-Host "Run 启动项未注册" -ForegroundColor Yellow
        return
    }

    Remove-ItemProperty -Path $RunKey -Name $ValueName -ErrorAction SilentlyContinue
    Write-Host ("已删除 Run 启动项: " + $RunKey + "\" + $ValueName) -ForegroundColor Green
}

# ----------------------------------------------------------
# status
# ----------------------------------------------------------
function Get-Status {
    $value = Get-ItemProperty -Path $RunKey -Name $ValueName -ErrorAction SilentlyContinue

    Write-Host ""
    Write-Host "graphify watch Run 启动项状态" -ForegroundColor Cyan
    Write-Host "----------------------------------------"

    if (-not $value) {
        Write-Host ("[NOT INSTALLED] 注册表项 " + $RunKey + "\" + $ValueName + " 不存在") -ForegroundColor Yellow
        Write-Host ""
        Write-Host "安装：.\scripts\graphify-watch-autostart.ps1 install"
    } else {
        Write-Host ("[INSTALLED] " + $RunKey + "\" + $ValueName) -ForegroundColor Green
        Write-Host ("  值        : " + $value.$ValueName)
        Write-Host ("  类型      : " + $value.$ValueName.GetType().Name)
    }
    Write-Host ""

    # 同时报告 watch 进程当前是否在跑
    $watchScript = $GraphifyWatchScript
    if (Test-Path $watchScript) {
        Write-Host "当前 watch 进程状态："
        & $watchScript status 2>&1 | Select-String -Pattern "RUNNING|STOPPED|PID" | ForEach-Object { Write-Host ("  " + $_) }
    }
}

# ----------------------------------------------------------
# 入口
# ----------------------------------------------------------
switch ($Action) {
    'install'   { Install-Autostart }
    'uninstall' { Uninstall-Autostart }
    'status'    { Get-Status }
}
