echo $0
echo $1
echo $2
echo $3

java -Xms8g -Xmx30g  -jar target/satd-analyzer-jar-with-all-dependencies.jar -r maldonado_study.csv -d mySQL.properties -s $1 -f $2 -t CHANGED -g $3 -add-opens java.base/java.lang=All-UNNAMED Main
