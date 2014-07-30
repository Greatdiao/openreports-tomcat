cd database
start start-database.bat
cd ..
for /d %%i in ("%ProgramFiles%\Java\jre*") do set JRE_HOME=%%i
set JAVA_HOME=
set CATALINA_HOME=tomcat
set "JAVA_OPTS=-Xms256m -Xmx2048m -XX:PermSize=128m -XX:MaxPermSize=512m"
tomcat\bin\catalina.bat run





