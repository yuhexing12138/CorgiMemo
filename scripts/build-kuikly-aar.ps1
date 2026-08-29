<# build-kuikly-aar.ps1
   一键重建 Kuikly shared AAR（方案 D：AAR 桥接）

   用法（PowerShell）：
       .\scripts\build-kuikly-aar.ps1

   前提：
       - kuikly-shared 工程（Gradle 8.11.1 / AGP 8.10.1 / Kotlin 2.1.21 / Kuikly 2.26.0-2.1.21）
       - 已配置 Android SDK（local.properties 的 sdk.dir）
   产物：
       - kuikly-shared/shared/build/outputs/aar/shared-release.aar
   说明：
       - 主工程 app/build.gradle.kts 已通过
         files("../kuikly-shared/shared/build/outputs/aar/shared-release.aar")
         直接引用该 AAR，无需拷贝；本脚本仅负责重建。
       - 改完 kuikly-shared 中的页面后运行本脚本，再在 Android Studio 重新编译主工程即可。
#>
$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SharedDir = Join-Path $ScriptDir '..' 'kuikly-shared'

Write-Host "==> 进入 kuikly-shared 工程：$SharedDir"
Set-Location $SharedDir

Write-Host '==> 重建 shared-release.aar (gradlew :shared:assembleRelease)'
& '.\gradlew.bat' :shared:assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Error "Kuikly AAR 构建失败（exit=$LASTEXITCODE）"
    exit $LASTEXITCODE
}

$Aar = Join-Path $SharedDir 'shared\build\outputs\aar\shared-release.aar'
if (-not (Test-Path $Aar)) {
    Write-Error "未找到产物：$Aar"
    exit 1
}

Write-Host "==> 构建成功：$Aar"
Write-Host '主工程已通过 files() 直接引用该 AAR，无需拷贝；在 Android Studio 重新编译/运行主工程即可加载最新 Kuikly 页面。'
