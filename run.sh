#!/bin/bash

# Check if an argument is provided
if [ $# -eq 0 ]; then
    echo "Usage: $0 <routerName>"
    exit 1
fi

# Run the Java application with the specified router configuration
java -cp target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar socs.network.Main "conf/$1.conf"