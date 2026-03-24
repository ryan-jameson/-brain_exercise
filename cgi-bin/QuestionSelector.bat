@echo off
chcp 65001 > nul
set PYTHONIOENCODING=utf-8
set SCRIPT=%~dp0QuestionSelector.py
python -X utf8 "%SCRIPT%" %*