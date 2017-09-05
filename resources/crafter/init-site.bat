@echo off
REM Script to create the Solr core & Deployer target for a delivery environment.
rem Make sure this variable is clean.

SET DELIVERY_HOME=
SET DELIVERY_HOME=%~dp0
set SITE=
set REPO=
for %%i in ("%~dp0..") do set DELIVERY_ROOT=%%~fi\
for %%i in ("%~dp0..\..") do set AUTHORING_ROOT=%%~fi\
SET AUTHORING_SITE_REPOS=%AUTHORING_ROOT%crafter-authoring\data\repos\sites
echo %DELIVERY_HOME%
echo %DELIVERY_ROOT%
echo %AUTHORING_ROOT%
echo %AUTHORING_SITE_REPOS%

IF /i "%1%"=="" goto shelp

set SITE=%1
set REPO=%2
IF NOT DEFINED REPO SET REPO=%AUTHORING_SITE_REPOS%\%SITE%\published

echo "Creating Solr Core"
java -jar %DELIVERY_HOME%craftercms-utils.jar post "http://localhost:@TOMCAT_HTTP_PORT@/crafter-search/api/2/admin/index/create" "{""id"":""%SITE%""}"
echo ""
echo "Creating Deployer Target"
java -jar %DELIVERY_HOME%craftercms-utils.jar post "http://localhost:@DEPLOYER_PORT@/api/1/target/create"  "{""env"":""default"", ""site_name"":""%SITE%"", ""template_name"":""remote"", ""repo_url"":""%REPO%"", ""repo_branch"":""live"", ""engine_url"":""http://localhost:@TOMCAT_HTTP_PORT@""}"
exit /b 0
:shelp
echo "Usage: init-site.sh <site name> [site's published repo git url]"
exit /b 1
