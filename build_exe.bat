@echo off
cd /d "%~dp0"
if not exist .venv (
  py -m venv .venv
)
call .venv\Scripts\activate
python -m pip install --upgrade pip
pip install -r requirements.txt
pyinstaller --noconfirm --clean --onefile --windowed --name "PersonalExeMadeByAI" --paths src src\app.py

echo.
echo Build complete. Your exe should be here:
echo %CD%\dist\PersonalExeMadeByAI.exe
echo.
pause
