@echo off
setlocal

if "%1"=="--test" (
    echo [INFO] 启动测试脚本 (DatasetEvaluator)...
    mvn compile exec:java -D"exec.mainClass=org.example.DatasetEvaluator"
) else if "%1"=="--web" (
    set "PORT=8082"
    if not "%2"=="" set "PORT=%2"

    netstat -ano | findstr /R /C:":%PORT% " >nul
    if %errorlevel%==0 (
        echo [WARN] Port %PORT% is in use, trying 8083...
        set "PORT=8083"
        netstat -ano | findstr /R /C:":%PORT% " >nul
        if %errorlevel%==0 (
            echo [ERROR] Port 8083 is also in use. Please specify another port, e.g. run.bat --web 8084
            exit /b 1
        )
    )

    echo [INFO] 启动网页后端服务 (Spring Boot)...
    echo [INFO] 使用端口: %PORT%
    mvn spring-boot:run -D"spring-boot.run.arguments=--server.port=%PORT%"
) else (
    echo [ERROR] 无效参数或未提供参数！
    echo.
    echo ==============================
    echo 使用方法：
    echo run.bat --web [port]   (start web backend, default 8082; auto-fallback to 8083 if occupied)
    echo run.bat --test  (运行所有的测试用例并生成 results.txt 和 usage.txt)
    echo ==============================
)
