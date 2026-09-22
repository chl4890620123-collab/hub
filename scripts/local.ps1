param([ValidateSet("start","stop","status","check","doctor","test","build")][string]$Action = "start")
$ErrorActionPreference = "Stop"
$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $Utf8NoBom
[Console]::OutputEncoding = $Utf8NoBom
$OutputEncoding = $Utf8NoBom
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
if ($env:JAVA_TOOL_OPTIONS -notmatch "file.encoding=UTF-8") {
    $env:JAVA_TOOL_OPTIONS = (($env:JAVA_TOOL_OPTIONS + " -Dfile.encoding=UTF-8 -Duser.language=ko -Duser.country=KR").Trim())
}

$Root = Split-Path -Parent $PSScriptRoot
$Runtime = Join-Path $Root ".runtime"
$EnvFile = Join-Path $Root ".env"
$AiDir = Join-Path $Root "ai-service"
$BackendDir = Join-Path $Root "backend"
$FrontendDir = Join-Path $Root "frontend"
$WebDir = Join-Path $Root "web"
$VenvPython = Join-Path $AiDir ".venv\Scripts\python.exe"
New-Item -ItemType Directory -Force -Path $Runtime | Out-Null

function Get-SystemPython {
    $cmd = Get-Command python -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "Python 3.9-3.13 x64가 필요합니다." }
    return $cmd.Source
}

function Get-GradleCommand {
    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $lines = @(& "$PSScriptRoot\bootstrap-gradle.ps1")
    if ($LASTEXITCODE -ne 0 -or $lines.Count -eq 0) { throw "Gradle 준비에 실패했습니다." }
    $gradle = $lines[-1]
    if (-not (Test-Path $gradle)) { throw "Gradle 실행 파일을 찾을 수 없습니다: $gradle" }
    return $gradle
}

function Import-DotEnv([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) { return }
        $name, $value = $line.Split("=", 2)
        $name = $name.Trim(); $value = $value.Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ([string]::IsNullOrEmpty($value)) { [Environment]::SetEnvironmentVariable($name, $null, "Process") }
        else { [Environment]::SetEnvironmentVariable($name, $value, "Process") }
    }
}

function Assert-Env([string]$Mode = "local") {
    if (-not (Test-Path $EnvFile)) { return }
    $python = Get-SystemPython
    & $python (Join-Path $PSScriptRoot "validate_env.py") --file $EnvFile --mode $Mode
    if ($LASTEXITCODE -ne 0) { throw ".env 검증에 실패했습니다." }
}

function Test-Url([string]$Url) {
    try { Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2 | Out-Null; return $true } catch { return $false }
}

function Wait-Url([string]$Url, [int]$Seconds, [string]$Name) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Url $Url) { Write-Host "[UP] $Name - $Url"; return }
        Start-Sleep -Milliseconds 700
    }
    throw "$Name 시작 시간 초과: $Url"
}

function Test-PythonImport([string]$Python, [string]$ImportStatement) {
    # A native command that writes anything to stderr (a traceback, or even a benign library
    # INFO line) becomes a terminating NativeCommandError under $ErrorActionPreference = "Stop",
    # even when redirected to $null. try/catch is required so a successful-but-noisy import
    # doesn't get treated as a missing package.
    try {
        & $Python -c $ImportStatement 2>$null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Stop-Tree([string]$PidFile) {
    if (-not (Test-Path $PidFile)) { return }
    $pidValue = (Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($pidValue -match '^\d+$') { try { & taskkill /PID $pidValue /T /F *> $null } catch { } }
    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
}

function Ensure-PythonRuntime {
    $systemPython = Get-SystemPython
    if (-not (Test-Path $VenvPython)) {
        Write-Host "[SETUP] Python 가상환경 생성"
        & $systemPython -m venv (Join-Path $AiDir ".venv")
        if ($LASTEXITCODE -ne 0) { throw "Python 가상환경 생성 실패" }
    }

    if (-not (Test-PythonImport $VenvPython "import fastapi,uvicorn,httpx,pydantic,multipart")) {
        Write-Host "[SETUP] AI 기본 패키지 설치 (최초 1회)"
        & $VenvPython -m pip install --disable-pip-version-check -r (Join-Path $AiDir "requirements.txt")
        if ($LASTEXITCODE -ne 0) { throw "AI 기본 패키지 설치 실패" }
    }

    if ($env:HUB_EMBED_MODE -eq "e5" -or $env:HUB_AI_MODE -eq "gemini") {
        if (-not (Test-PythonImport $VenvPython "import sentence_transformers; import paddle; import paddleocr")) {
            Write-Host "[SETUP] 로컬 E5/업로드 문서 OCR 패키지 설치 (최초 1회)"
            & $VenvPython -m pip install --disable-pip-version-check -r (Join-Path $AiDir "requirements-local.txt")
            if ($LASTEXITCODE -ne 0) { throw "로컬 AI 의존성 설치 실패: ai-service/requirements-local.txt" }
        }
    }
}

function Ensure-FrontendRuntime {
    $npm = Get-Command npm -ErrorAction SilentlyContinue
    if (-not $npm) { throw "프론트 검증/빌드에는 Node.js 22+ 와 npm이 필요합니다." }
    $tsc = Join-Path $FrontendDir "node_modules\typescript\bin\tsc"
    if (-not (Test-Path $tsc)) {
        Write-Host "[SETUP] 프론트 개발 의존성 설치 (최초 1회)"
        Push-Location $FrontendDir
        try {
            & $npm.Source ci --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { throw "npm ci 실패" }
        } finally { Pop-Location }
    }
}

function Ensure-WebRuntime {
    $npm = Get-Command npm -ErrorAction SilentlyContinue
    if (-not $npm) { throw "React 앱 빌드에는 Node.js 22+ 와 npm이 필요합니다." }
    $vite = Join-Path $WebDir "node_modules\.bin\vite.cmd"
    if (-not (Test-Path $vite)) {
        Write-Host "[SETUP] web/ 의존성 설치 (최초 1회)"
        Push-Location $WebDir
        try {
            & $npm.Source ci --no-audit --no-fund
            if ($LASTEXITCODE -ne 0) { throw "web/ npm ci 실패" }
        } finally { Pop-Location }
    }
}

function Invoke-Gradle([string[]]$GradleArgs) {
    $gradle = Get-GradleCommand
    Push-Location $BackendDir
    try {
        & $gradle --no-daemon @GradleArgs
        if ($LASTEXITCODE -ne 0) { throw "Gradle 실패: $($GradleArgs -join ' ')" }
    } finally { Pop-Location }
}

function Run-RepositoryCheck {
    $python = Get-SystemPython
    & $python (Join-Path $PSScriptRoot "validate_env.py") --file (Join-Path $Root ".env.example") --mode example
    if ($LASTEXITCODE -ne 0) { throw ".env.example 검증 실패" }
    if (Test-Path $EnvFile) { Assert-Env }
    & $python (Join-Path $PSScriptRoot "verify.py")
    if ($LASTEXITCODE -ne 0) { throw "저장소 구조 검증 실패" }
}

function Run-Doctor {
    Write-Host "=== Hub v2.34 VS Code / Windows 환경 점검 ==="
    $prevEAP = $ErrorActionPreference; $ErrorActionPreference = "Continue"
    $javaText = (& java -version 2>&1 | Out-String)
    $ErrorActionPreference = $prevEAP
    if ($LASTEXITCODE -eq 0 -and $javaText -match 'version "21[\.]') { Write-Host "[OK] Java 21" }
    else { Write-Host "[FAIL] Java 21 필요"; Write-Host $javaText }

    try {
        $python = Get-SystemPython
        $prevEAP = $ErrorActionPreference; $ErrorActionPreference = "Continue"
        $pyVer = (& $python --version 2>&1 | Out-String).Trim()
        $ErrorActionPreference = $prevEAP
        Write-Host "[OK] $pyVer"
    } catch { Write-Host "[FAIL] $($_.Exception.Message)" }

    $node = Get-Command node -ErrorAction SilentlyContinue
    if ($node) { Write-Host ("[OK] Node " + ((& $node.Source --version) -join "")) }
    else { Write-Host "[INFO] Node 없음 - 앱 실행은 가능하지만 TypeScript 검증/빌드는 불가" }

    if (Test-Path $EnvFile) { Write-Host "[OK] .env 존재 (값은 출력하지 않음)" }
    else { Write-Host "[INFO] .env 없음 - mock/hash 로컬 모드로 실행 가능" }

    if (Get-Command code -ErrorAction SilentlyContinue) { Write-Host "[OK] VS Code CLI 감지" }
    else { Write-Host "[INFO] VS Code CLI(code)가 PATH에 없어도 Hub.code-workspace를 직접 열면 됩니다." }

    Run-RepositoryCheck
    Write-Host "[OK] 프로젝트 구조/설정 검증 완료"
}

function Run-Tests {
    Run-RepositoryCheck

    Ensure-FrontendRuntime
    Push-Location $FrontendDir
    try {
        Write-Host "[TEST] TypeScript"
        & npm run typecheck
        if ($LASTEXITCODE -ne 0) { throw "TypeScript typecheck 실패" }
    } finally { Pop-Location }

    Write-Host "[TEST] FastAPI / Python"
    $oldAi = $env:HUB_AI_MODE; $oldEmbed = $env:HUB_EMBED_MODE; $oldPythonPath = $env:PYTHONPATH
    $env:HUB_AI_MODE = "mock"; $env:HUB_EMBED_MODE = "hash"; $env:PYTHONPATH = $AiDir
    Ensure-PythonRuntime
    Push-Location $AiDir
    try {
        & $VenvPython -m pytest -q
        if ($LASTEXITCODE -ne 0) { throw "Python 테스트 실패" }
    } finally {
        Pop-Location
        $env:HUB_AI_MODE = $oldAi; $env:HUB_EMBED_MODE = $oldEmbed; $env:PYTHONPATH = $oldPythonPath
    }

    Write-Host "[TEST] Spring Boot"
    Invoke-Gradle @("clean", "test")
    Write-Host "[PASS] 전체 테스트 완료"
}

function Run-Build {
    Run-RepositoryCheck
    Ensure-FrontendRuntime
    Push-Location $FrontendDir
    try {
        Write-Host "[BUILD] TypeScript"
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "TypeScript build 실패" }
    } finally { Pop-Location }

    $staticJs = Join-Path $BackendDir "src\main\resources\static\js"
    Copy-Item (Join-Path $FrontendDir "dist\hub-runtime.js") (Join-Path $staticJs "hub-runtime.js") -Force
    Copy-Item (Join-Path $FrontendDir "dist\login.js") (Join-Path $staticJs "login.js") -Force

    Ensure-WebRuntime
    Push-Location $WebDir
    try {
        Write-Host "[BUILD] React 앱 (web/)"
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "web/ build 실패" }
    } finally { Pop-Location }
    $staticRoot = Join-Path $BackendDir "src\main\resources\static"
    Copy-Item (Join-Path $WebDir "dist\index.html") (Join-Path $staticRoot "index.html") -Force
    Copy-Item (Join-Path $WebDir "dist\assets") (Join-Path $staticRoot "assets") -Recurse -Force

    Write-Host "[BUILD] Spring Boot JAR"
    Invoke-Gradle @("clean", "test", "bootJar")
    Write-Host "[PASS] backend\build\libs\hub-backend.jar 생성 완료"
}

function Show-Status {
    $port = if ($env:HUB_PORT) { $env:HUB_PORT } else { "8080" }
    $ai = Test-Url "http://localhost:8000/health"
    $backend = Test-Url "http://localhost:$port/actuator/health"
    Write-Host ("AI service : " + $(if ($ai) { "UP" } else { "DOWN" }))
    Write-Host ("Backend    : " + $(if ($backend) { "UP" } else { "DOWN" }))
    if ($backend) { Write-Host "Web        : http://localhost:$port/login" }
}

if ($Action -eq "stop") {
    Stop-Tree (Join-Path $Runtime "backend.pid")
    Stop-Tree (Join-Path $Runtime "ai.pid")
    Write-Host "Hub 로컬 프로세스를 종료했습니다. H2 데이터는 유지됩니다."
    exit 0
}

if (Test-Path $EnvFile) { Assert-Env; Import-DotEnv $EnvFile }
else {
    Write-Host "[INFO] .env 없음: H2 + mock AI + hash embedding으로 빠른 로컬 실행을 사용합니다."
    Write-Host "[INFO] 실제 AI를 쓸 때만 .env.example을 .env로 복사해 값을 채우세요."
}

if (-not $env:HUB_PORT) { $env:HUB_PORT = "8080" }
if (-not $env:HUB_BIND_ADDRESS) { $env:HUB_BIND_ADDRESS = "localhost" }
if (-not $env:HUB_AI_BASE_URL) { $env:HUB_AI_BASE_URL = "http://localhost:8000" }
if (-not $env:HUB_AI_MODE) { $env:HUB_AI_MODE = "mock" }
if (-not $env:HUB_EMBED_MODE) { $env:HUB_EMBED_MODE = "hash" }
$env:SPRING_PROFILES_ACTIVE = "local"

if ($Action -eq "status") { Show-Status; exit 0 }
if ($Action -eq "check") { Run-RepositoryCheck; Show-Status; exit 0 }
if ($Action -eq "doctor") { Run-Doctor; exit 0 }
if ($Action -eq "test") { Run-Tests; exit 0 }
if ($Action -eq "build") { Run-Build; exit 0 }

$prevEAP = $ErrorActionPreference; $ErrorActionPreference = "Continue"
$javaText = (& java -version 2>&1 | Out-String)
$ErrorActionPreference = $prevEAP
if ($LASTEXITCODE -ne 0 -or $javaText -notmatch 'version "21[\.]') {
    throw "Java 21이 필요합니다. 현재 java -version:`n$javaText"
}

Ensure-PythonRuntime
if (-not (Test-Url "http://localhost:8000/health")) {
    $aiOut = Join-Path $Runtime "ai.out.log"; $aiErr = Join-Path $Runtime "ai.err.log"
    $p = Start-Process -FilePath $VenvPython -ArgumentList @("-m","uvicorn","app.main:app","--host","localhost","--port","8000") -WorkingDirectory $AiDir -RedirectStandardOutput $aiOut -RedirectStandardError $aiErr -PassThru
    Set-Content (Join-Path $Runtime "ai.pid") $p.Id
}
try { Wait-Url "http://localhost:8000/health" 90 "AI service" } catch {
    Get-Content (Join-Path $Runtime "ai.err.log") -Tail 100 -ErrorAction SilentlyContinue
    throw
}

$staticIndex = Join-Path $BackendDir "src\main\resources\static\index.html"
if (-not (Test-Path $staticIndex)) {
    Write-Host "[SETUP] React 앱(web/) static/index.html이 없어 최초 1회 빌드합니다 (이후 재시작은 건너뜁니다 - 코드를 바꿨다면 scripts/local.ps1 -Action build 로 다시 빌드하세요)"
    Ensure-WebRuntime
    Push-Location $WebDir
    try {
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "web/ build 실패" }
    } finally { Pop-Location }
    $staticRoot = Join-Path $BackendDir "src\main\resources\static"
    Copy-Item (Join-Path $WebDir "dist\index.html") $staticIndex -Force
    Copy-Item (Join-Path $WebDir "dist\assets") (Join-Path $staticRoot "assets") -Recurse -Force
}

$gradleCmd = Get-GradleCommand
if (-not (Test-Url "http://localhost:$($env:HUB_PORT)/actuator/health")) {
    $backendOut = Join-Path $Runtime "backend.out.log"; $backendErr = Join-Path $Runtime "backend.err.log"
    if ($gradleCmd -match '\.(bat|cmd)$') {
        $cmdLine = ('"{0}" --no-daemon bootRun' -f $gradleCmd)
        $p = Start-Process -FilePath "cmd.exe" -ArgumentList @("/d","/s","/c",$cmdLine) -WorkingDirectory $BackendDir -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr -PassThru
    } else {
        $p = Start-Process -FilePath $gradleCmd -ArgumentList @("--no-daemon","bootRun") -WorkingDirectory $BackendDir -RedirectStandardOutput $backendOut -RedirectStandardError $backendErr -PassThru
    }
    Set-Content (Join-Path $Runtime "backend.pid") $p.Id
}
try { Wait-Url "http://localhost:$($env:HUB_PORT)/actuator/health" 180 "Spring Boot" } catch {
    Write-Host "--- backend stderr ---"; Get-Content (Join-Path $Runtime "backend.err.log") -Tail 120 -ErrorAction SilentlyContinue
    Write-Host "--- backend stdout ---"; Get-Content (Join-Path $Runtime "backend.out.log") -Tail 120 -ErrorAction SilentlyContinue
    throw
}

Write-Host ""
Write-Host "Hub v2.34 로컬 실행 완료"
Write-Host "Web       : http://localhost:$($env:HUB_PORT)/login"
Write-Host "AI health : http://localhost:8000/health"
Write-Host "Stop      : HUB.bat stop"
Write-Host "실제 비밀값은 표시하지 않습니다."
if ($env:HUB_BIND_ADDRESS -eq "0.0.0.0") {
    Write-Host "LAN 공개 상태입니다. 다른 PC의 브라우저 직접 녹음은 HTTPS 사용을 권장합니다."
}
if ($env:HUB_LOCAL_AUTO_OPEN -ne "false") { Start-Process "http://localhost:$($env:HUB_PORT)/login" }
