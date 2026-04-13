param(
    [Parameter(Position=0, Mandatory=$false, HelpMessage="Run mode")]
    [ValidateSet("--web", "--pms", "--spider", "--natsql", "--ref", "--help")]
    [string]$Mode = "--help",

    [Parameter(Position=1, Mandatory=$false, HelpMessage="Optional web port, e.g. 8080")]
    [int]$Port = 8080
)

function Show-Header($Title, $Color) {
    Write-Host "`n"
    Write-Host "====================================================" -ForegroundColor $Color
    Write-Host "  $Title" -ForegroundColor $Color
    Write-Host "====================================================" -ForegroundColor $Color
}

function Show-Help {
    Show-Header "ACAI Project - Multi-Stage NL2SQL System" "Cyan"
    Write-Host "Usage: .\run.ps1 [Mode] [OptionalPort]`n"
    Write-Host "Available Modes:" -ForegroundColor Yellow
    Write-Host "  --web     : [Web Mode] Start Spring Boot Backend & Chat UI (Port: $Port)"
    Write-Host "  --pms     : [PMS Mode] Run PMS 100-question Evaluation (3-Stage Pipeline)"
    Write-Host "  --spider  : [Spider Mode] Run Spider Dataset Evaluation"
    Write-Host "  --natsql  : [NatSQL Mode] Run NatSQL vs MQL Comparative Evaluation"
    Write-Host "  --ref     : [Utils] Run ReferenceBuilder (Generate Ground Truth SQLs)"
    Write-Host "  --help    : Show this help information"
    Write-Host "`nExamples:"
    Write-Host "  .\run.ps1 --web 8083"
    Write-Host "  .\run.ps1 --pms"
}

if ($Mode -eq "--help") {
    Show-Help
    exit 0
}

# 1. Environment Check
if (-not (Test-Path "src/main/resources/application.properties")) {
    Write-Host "[ERROR] Configuration file 'application.properties' not found!" -ForegroundColor Red
    if (Test-Path "src/main/resources/application.properties.example") {
        Write-Host "[TIP] Found 'application.properties.example'. Rename it and fill in your DashScope API Key." -ForegroundColor Cyan
    }
    exit 1
}

# 2. Execution Logic
switch ($Mode) {
    "--web" {
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
        Show-Header "Starting Web Service - ACAI Chat UI" "Green"
        Write-Host "[INFO] Server UI: http://localhost:$selectedPort" -ForegroundColor Green
        mvn spring-boot:run -D"spring-boot.run.arguments=--server.port=$selectedPort"
    }

    "--pms" {
        Show-Header "Running PMS NL2SQL Evaluation (3-Stage)" "Cyan"
        Write-Host "[INFO] Step 1: NL2MQL -> SchemaLinker -> MQL2SQL" -ForegroundColor Cyan
        mvn compile exec:java -D"exec.mainClass=org.example.DatasetEvaluator"
        
        if (Test-Path "dataset/evaluate.py") {
            Show-Header "Step 2: Running Python Accuracy Report" "Yellow"
            Push-Location dataset
            python evaluate.py
            Pop-Location
        }
    }

    "--spider" {
        Show-Header "Running Spider Evaluation Pipeline" "Magenta"
        mvn compile exec:java -D"exec.mainClass=org.example.SpiderEvaluator"
    }

    "--natsql" {
        Show-Header "Running NatSQL Performance Benchmarking" "Magenta"
        mvn compile exec:java -D"exec.mainClass=org.example.NatSQLEvaluator"
    }

    "--ref" {
        Show-Header "Utility: Building/Refreshing Reference SQLs" "Gray"
        mvn compile exec:java -D"exec.mainClass=org.example.ReferenceBuilder"
    }

    default {
        Show-Help
    }
}
