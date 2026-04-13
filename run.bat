@echo off
title ACAI Control Center

:menu
cls
echo ============================================================
echo      ACAI Project - Multi-Stage NL2SQL Control Center
echo ============================================================
echo.
echo   [1] Start Web Mode (Spring Boot + Chat UI)
echo.
echo   [2] PMS - Step 1: Java SQL Generation
echo   [3] PMS - Step 2: Python Accuracy Report
echo.
echo   [4] Run Spider Dataset Evaluation
echo   [5] Run NatSQL Benchmark
echo   [6] Run Reference Builder Utility
echo.
echo   [x] Exit
echo.
echo ============================================================
set /p choice="Please select an option (1-6, x): "

if "%choice%"=="1" goto web
if "%choice%"=="2" goto pms1
if "%choice%"=="3" goto pms2
if "%choice%"=="4" goto spider
if "%choice%"=="5" goto natsql
if "%choice%"=="6" goto ref
if "%choice%"=="x" exit
if "%choice%"=="X" exit
goto menu

:web
cls
echo [INFO] Starting Web Backend (Spring Boot)...
call mvn spring-boot:run
pause
goto menu

:pms1
cls
echo [INFO] Running PMS Eval Step 1: Java SQL Generation...
call mvn compile exec:java -D"exec.mainClass=org.example.DatasetEvaluator"
echo.
echo Process finished.
pause
goto menu

:pms2
cls
echo [INFO] Running PMS Eval Step 2: Python Accuracy Report...
if exist "dataset\evaluate.py" (
    pushd dataset
    python evaluate.py
    popd
) else (
    echo [ERROR] File not found: dataset\evaluate.py
)
echo.
echo Process finished.
pause
goto menu

:spider
cls
echo [INFO] Running Spider Evaluation...
call mvn compile exec:java -D"exec.mainClass=org.example.SpiderEvaluator"
pause
goto menu

:natsql
cls
echo [INFO] Running NatSQL Benchmark...
call mvn compile exec:java -D"exec.mainClass=org.example.NatSQLEvaluator"
pause
goto menu

:ref
cls
echo [INFO] Running Reference Builder Utility...
call mvn compile exec:java -D"exec.mainClass=org.example.ReferenceBuilder"
pause
goto menu
