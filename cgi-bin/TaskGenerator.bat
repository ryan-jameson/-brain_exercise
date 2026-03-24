@echo off
chcp 65001 > nul
set MODE=%1
if "%MODE%"=="" set MODE=memory
set ROOT=%~dp0..\
java -cp "%ROOT%out" TaskGenerator %MODE%
