@echo off
setlocal EnableDelayedExpansion
chcp 65001 > nul
title AURA TV 2.0 - Install on TCL
set "ADB=%USERPROFILE%\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%USERPROFILE%\Downloads\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo [ERROR] adb.exe not found in Downloads. Extract Android platform-tools first.
  pause
  exit /b 1
)
set "APK=%USERPROFILE%\Downloads\AURA-TV-V2-APK\app-debug.apk"
if not exist "%APK%" set "APK=%~dp0app-debug.apk"
if not exist "%APK%" (
  echo [ERROR] app-debug.apk not found.
  echo Extract AURA-TV-V2-APK.zip into Downloads\AURA-TV-V2-APK, or put APK next to this script.
  pause
  exit /b 1
)
echo Checking connected devices...
"%ADB%" devices
set "DEVICE="
for /f "tokens=1,2" %%a in ('"%ADB%" devices ^| findstr /B "192.168.8.5:"') do (
  if "%%b"=="device" set "DEVICE=%%a"
)
if not defined DEVICE (
  echo.
  echo [ERROR] TCL not found with connected IP 192.168.8.5.
  echo Enable Wireless Debugging and connect through ADB first.
  echo Example: adb connect 192.168.8.5:PORT
  pause
  exit /b 1
)
echo Installing on !DEVICE!...
"%ADB%" -s "!DEVICE!" install -r "%APK%"
if not errorlevel 1 goto :DONE
echo.
echo [WARNING] Update failed. One common reason: the GitHub debug APK has a different signature.
choice /c YN /m "Uninstall ONLY the old AURA TV app and try again? (This erases its preferences)"
if errorlevel 2 exit /b 1
"%ADB%" -s "!DEVICE!" uninstall com.auratv.launcher
"%ADB%" -s "!DEVICE!" install "%APK%"
if errorlevel 1 (
  echo Installation failed. Check messages above.
  pause
  exit /b 1
)
:DONE
echo.
echo SUCCESS - Open AURA TV in TCL Apps.
pause
