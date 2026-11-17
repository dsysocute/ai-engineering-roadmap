@echo off
setlocal
set "JDK=E:\PCL 正式版 2.10.6\openjdk-17.0.0.1+2_windows-x64_bin\jdk-17.0.0.1\bin"
set "JAVAC=%JDK%\javac.exe"
set "JAVA=%JDK%\java.exe"
set "PROJ=D:\sort_platform"
set "SRC=%PROJ%\backend\src"
set "ADMIN_PASS=sort2026#admin"
set "JUDGE_PASS=abc2511298"
set "SUPER_PASS=super2026"

cd /d "%SRC%"
echo ==== step 1 : compiling ====
"%JAVAC%" -encoding UTF-8 *.java
if errorlevel 1 goto fail

cd /d "%PROJ%"
echo ==== step 2 : starting server ====
echo     site  : http://localhost:8080
echo     admin : http://localhost:8080/admin
echo     judge : http://localhost:8080/judge  (口令 abc2511298)
echo     super : http://localhost:8080/super  (supervisor / super2026)
echo     watch : http://localhost:8080/watch
echo.
set "PID8080="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":8080 "') do set "PID8080=%%a"
if defined PID8080 (
  echo [提示] 8080 端口被进程 %PID8080% 占用，正在结束它...
  taskkill /PID %PID8080% /F >nul 2>&1
  timeout /t 2 >nul
  set "PID8080="
  for /f "tokens=5" %%a in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":8080 "') do set "PID8080=%%a"
  if defined PID8080 (
    echo [错误] 端口仍被进程 %PID8080% 占用，请手动结束它：taskkill /PID %PID8080% /F
    pause
    exit /b 1
  )
  echo [完成] 端口已释放，继续启动
)
"%JAVA%" -cp "%SRC%" Main
goto end

:fail
echo.
echo COMPILE FAILED - read the errors above
pause
exit /b 1

:end
echo.
echo server stopped
pause
