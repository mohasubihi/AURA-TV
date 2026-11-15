@echo off
setlocal
set "ADB=%USERPROFILE%\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%USERPROFILE%\Downloads\platform-tools\adb.exe"
if not exist "%ADB%" (
  echo ERROR: Could not find adb.exe. Extract platform-tools to Downloads.
  pause
  exit /b 1
)
set "APK=%USERPROFILE%\Downloads\AURA-LOUNGE-V4-APK\app-debug.apk"
if not exist "%APK%" (
  echo ERROR: Could not find %APK%
  echo Extract the latest artifact to Downloads\AURA-LOUNGE-V4-APK.
  pause
  exit /b 1
)
echo.
echo Connected TV devices:
"%ADB%" devices
echo.
set /p SERIAL=Enter your TV's adb IP:PORT from the row marked 'device': 
if "%SERIAL%"=="" exit /b 1
"%ADB%" -s "%SERIAL%" install "%APK%"
if errorlevel 1 (
  echo.
  echo Install failed. Check the serial / TV debug authorization.
) else (
  echo.
  echo Installation finished. Open AURA Lounge from your TV applications.
)
pause
