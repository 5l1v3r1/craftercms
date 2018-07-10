@echo off
REM Script download new version of the Crafter installation bundle

SET UPGRADE_HOME=%~dp0
SET CRAFTER_HOME=%UPGRADE_HOME%\..
SET CRAFTER_ROOT=%CRAFTER_HOME%\..
SET UPGRADE_TMP_DIR=%CRAFTER_ROOT%\upgrade

call %CRAFTER_HOME%\crafter-setenv.bat

REM Execute Groovy script
%CRAFTER_HOME%\groovy\bin\groovy -cp %CRAFTER_HOME% -Dgrape.root=%CRAFTER_HOME% %UPGRADE_HOME%\download-bundle.groovy %*
