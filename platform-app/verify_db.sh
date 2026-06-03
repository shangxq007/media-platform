#!/bin/bash

# Simple database verification script
# This script will run the application and then check the database

echo "Starting database verification..."

# First, let's try to run the application with a minimal configuration
cd platform/platform-app

# Kill any existing processes
pkill -f "java.*PlatformApplication" 2>/dev/null || true

# Start the application in background
echo "Starting application..."
../gradlew bootRun --args='--spring.profiles.active=dev' > app.log 2>&1 &
APP_PID=$!

# Wait for application to start
echo "Waiting for application to start..."
sleep 10

# Check if application is running
if ps -p $APP_PID > /dev/null; then
    echo "Application started with PID: $APP_PID"
else
    echo "Failed to start application"
    cat app.log
    exit 1
fi

# Wait a bit more for database initialization
echo "Waiting for database initialization..."
sleep 5

# Try to connect to H2 database and run verification
echo "Running database verification..."

# Use H2 command line tool if available, otherwise use JDBC
if command -v h2.sh &> /dev/null; then
    echo "Using H2 command line tool..."
    h2.sh -url "jdbc:h2:mem:66196332-3a7e-4f3f-8327-55a8bcefe2fe" -user "SA" -sql "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC;"
else
    echo "H2 command line tool not found, trying JDBC..."
    # Try to use a simple JDBC connection
    java -cp "build/classes/java/main:build/libs/*" -Dspring.profiles.active=dev SimpleDatabaseChecker
fi

# Check the results
echo "Checking verification results..."

if [ -f "verification_results.txt" ]; then
    echo "=== VERIFICATION RESULTS ==="
    cat verification_results.txt
else
    echo "No verification results found"
fi

# Stop the application
echo "Stopping application..."
kill $APP_PID 2>/dev/null || true
wait $APP_PID 2>/dev/null || true

echo "Database verification completed."