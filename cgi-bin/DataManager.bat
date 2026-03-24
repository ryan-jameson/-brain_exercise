@echo off
chcp 65001 > nul
set MODE=%1
if "%MODE%"=="" set MODE=save
set ROOT=%~dp0..\
java -cp "%ROOT%out" DataManager %MODE%
