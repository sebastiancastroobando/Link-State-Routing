# Link-State-Routing
 Simulating link state routing protocol with Java socket programming

## How to run
1. Open terminal and navigate to the project directory
2. Compile the project using the following command:
```bash
mvn compile assembly:single
```
3. To run router1 (run the project in general), use the following command:
```bash
java -cp target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar socs.network.Main conf/router1.conf
```
Configuration of each router is specified in the `conf` directory.

## Non-important TODOs 
-[ ] you can attach twice to the same router. Should we check for this? 