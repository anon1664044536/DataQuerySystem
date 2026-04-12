param(
    [Parameter(Position=0, Mandatory=$true, HelpMessage="Run mode: --web or --test")]
    [ValidateSet("--web", "--test")]
    [string]$Mode,

    [Parameter(Position=1, Mandatory=$false, HelpMessage="Optional web port, e.g. 8083")]
    [int]$Port = 8080
)

if ($Mode -eq "--test") {
    Write-Host "==============================" -ForegroundColor Cyan
    Write-Host "[INFO] Starting test runner (DatasetEvaluator)..." -ForegroundColor Cyan
    Write-Host "==============================" -ForegroundColor Cyan
    mvn compile exec:java -D"exec.mainClass=org.example.DatasetEvaluator"
} elseif ($Mode -eq "--web") {
    $selectedPort = $Port
    $inUse = Get-NetTCPConnection -LocalPort $selectedPort -ErrorAction SilentlyContinue
    if ($null -ne $inUse) {
        Write-Host "[WARN] Port $selectedPort is in use, trying 8083..." -ForegroundColor Yellow
        $selectedPort = 8083
        $fallbackInUse = Get-NetTCPConnection -LocalPort $selectedPort -ErrorAction SilentlyContinue
        if ($null -ne $fallbackInUse) {
            Write-Host "[ERROR] Port 8083 is also in use. Please specify another port, e.g. .\run.ps1 --web 8084" -ForegroundColor Red
            exit 1
        }
    }

    Write-Host "==============================" -ForegroundColor Green
    Write-Host "[INFO] Starting web backend service (Spring Boot)..." -ForegroundColor Green
    Write-Host "[INFO] Using port: $selectedPort" -ForegroundColor Green
    Write-Host "==============================" -ForegroundColor Green
    mvn spring-boot:run -D"spring-boot.run.arguments=--server.port=$selectedPort"
} else {
    Write-Host "[ERROR] Invalid mode. Use --web or --test" -ForegroundColor Red
}
