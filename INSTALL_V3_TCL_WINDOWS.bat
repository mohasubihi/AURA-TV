@echo off
setlocal
set "ADB=%USERPROFILE%\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%USERPROFILE%\Downloads\platform-tools\adb.exe"
if not exist "%ADB%" (
 echo ADB was not found. Extract platform-tools to Downloads first.
 pause
 exit /b 1
)
set "APK=%USERPROFILE%\Downloads\AURA-TV-V3-APK\app-debug.apk"
if not exist "%APK%" (
 echo APK not found at: %APK%
 echo Download and extract the GitHub Actions artifact into Downloads\AURA-TV-V3-APK
 pause
 exit /b 1
)
echo Connected Android devices:
"%ADB%" devices
echo.
set /p SERIAL=Enter the TV serial shown above as IP:PORT (the row marked device): 
if "%SERIAL%"=="" exit /b 1
"%ADB%" -s "%SERIAL%" install "%APK%"
pause
