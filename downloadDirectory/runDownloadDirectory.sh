
## Generate the jar file and run the Download Directory program
mvn clean package
unzip -o target/downloadDirectory-distr.zip
java -Xmx4096m -javaagent:downloadDirectory/lib/spring-instrument-4.2.4.RELEASE.jar -jar downloadDirectory/downloadDirectory.jar
