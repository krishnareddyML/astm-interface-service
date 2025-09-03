#!/bin/bash
# Build script for ASTM Interface Service

echo "Building ASTM Interface Service..."
echo

# Clean and build all modules
echo "[1/3] Cleaning previous builds..."
mvn clean

echo
echo "[2/3] Building all modules..."
mvn install

if [ $? -ne 0 ]; then
    echo
    echo "ERROR: Build failed!"
    exit 1
fi

echo
echo "[3/3] Build completed successfully!"
echo

echo "Created artifacts:"
echo "- astm-server/target/astm-server-1.0.0.jar (Main server application)"
echo "- instrument-simulator/target/instrument-simulator-1.0.0-shaded.jar (Testing simulator)"
echo

echo "To run the server: java -jar astm-server/target/astm-server-1.0.0.jar"
echo "To run the simulator: java -jar instrument-simulator/target/instrument-simulator-1.0.0-shaded.jar"
echo
