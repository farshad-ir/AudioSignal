@echo off
setlocal enabledelayedexpansion

:: تنظیمات پروژه
set PROJECT_NAME=AudioSignal

:: مسیر SDK اندروید
set ANDROID_PLATFORM=C:\Tools\android\platforms\android-30\android.jar

:: مسیر فایل‌های جاوا (3 فایل)
set JAVA1=src\com\example\%PROJECT_NAME%\MainActivity.java
set JAVA2=src\com\example\%PROJECT_NAME%\SettingsActivity.java
set JAVA3=src\com\example\%PROJECT_NAME%\PrefManager.java

:: فایل مانیفست
set MANIFEST=AndroidManifest.xml

:: مسیر منابع (اگر دارید)
set RES_DIR=res

:: مسیر خروجی‌ها
set OUT_DIR=out
set OUT_DEX_DIR=out_dex

:: نام فایل‌های APK
set APK_RAW=unsigned.apk
set APK_ALIGNED=%PROJECT_NAME%_aligned.apk
set APK_SIGNED=%PROJECT_NAME%-signed.apk

:: کلین آپ فایل‌ها و فولدرهای قبلی
if exist %OUT_DIR% rmdir /s /q %OUT_DIR%
if exist %OUT_DEX_DIR% rmdir /s /q %OUT_DEX_DIR%
if exist %APK_RAW% del /f /q %APK_RAW%
if exist %APK_ALIGNED% del /f /q %APK_ALIGNED%
if exist %APK_SIGNED% del /f /q %APK_SIGNED%
if exist resources.ap_ del /f /q resources.ap_

:: 1. کامپایل جاوا با کلاس‌پث ZXing و اندروید
echo === 1. Compiling Java sources ===
mkdir %OUT_DIR%
javac -encoding UTF-8 -d %OUT_DIR% --release 11 -classpath "%ANDROID_PLATFORM%" %JAVA1% %JAVA2% %JAVA3%
if errorlevel 1 (
    echo ERROR: javac failed.
    pause
    exit /b 1
)

:: 2. بسته‌بندی منابع با AAPT
echo === 2. Packaging resources with AAPT ===
aapt package -f -M %MANIFEST% -S %RES_DIR% -I %ANDROID_PLATFORM% -F resources.ap_
if errorlevel 1 (
    echo ERROR: aapt failed.
    pause
    exit /b 1
)

:: 3. ساخت APK خام (copy باینری منابع به فایل apk)
echo === 3. Building unsigned APK ===
copy /b resources.ap_ %APK_RAW% >nul
if errorlevel 1 (
    echo ERROR: copy resources.ap_ failed.
    pause
    exit /b 1
)

:: 4. تبدیل کلاس‌ها به DEX با d8
echo === 4. Converting to DEX ===
mkdir %OUT_DEX_DIR%
:: مسیر درست کلاس‌ها (فرض com/example/PROJECT)
call d8log --output %OUT_DEX_DIR% --lib "%ANDROID_PLATFORM%" %OUT_DIR%\com\example\%PROJECT_NAME%\*.class
if errorlevel 1 (
    echo ERROR: d8 failed.
    pause
    exit /b 1
)

:: 5. افزودن classes.dex به APK
echo === 5. Adding classes.dex to APK ===
copy /y %OUT_DEX_DIR%\classes.dex . >nul
aapt add %APK_RAW% classes.dex
if errorlevel 1 (
    echo ERROR: aapt add failed.
    del classes.dex
    pause
    exit /b 1
)
del classes.dex

:: 6. zipalign نهایی
echo === 6. Aligning APK ===
zipalign -v 4 %APK_RAW% %APK_ALIGNED%
if errorlevel 1 (
    echo ERROR: zipalign failed.
    pause
    exit /b 1
)

:: 7. امضا با apksigner
echo === 7. Signing APK ===
call apksigner sign --ks ..\..\mykey.keystore --ks-key-alias myalias --out %APK_SIGNED% %APK_ALIGNED%
if errorlevel 1 (
    echo ERROR: signing failed.
    pause
    exit /b 1
)

:: 8. کپی فایل نهایی به dist با نام ساده
echo === 8. Copying signed APK to dist ===
if not exist dist mkdir dist
copy /y %APK_SIGNED% dist\%PROJECT_NAME%.apk >nul
if errorlevel 1 (
    echo ERROR: failed to copy signed APK.
    pause
    exit /b 1
)

echo.
echo === DONE: dist\%PROJECT_NAME%.apk ===
exit /b 0



