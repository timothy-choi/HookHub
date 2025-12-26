#!/bin/bash
# Test MySQL connection before running the application

echo "Testing MySQL connection..."

# Test 1: Try connecting with mysql command
echo "Test 1: MySQL command line connection"
mysql -h 127.0.0.1 -P 3306 -u root -pNO -e "SELECT 1 as test;" 2>&1

if [ $? -eq 0 ]; then
    echo "✓ MySQL command line connection works!"
    echo ""
    echo "Creating database if it doesn't exist..."
    mysql -h 127.0.0.1 -P 3306 -u root -pNO -e "CREATE DATABASE IF NOT EXISTS webhookdb;" 2>&1
    echo "✓ Database ready!"
    echo ""
    echo "You can now run: mvn spring-boot:run"
else
    echo "✗ MySQL command line connection failed"
    echo ""
    echo "Trying alternative connection methods..."
    
    # Test 2: Try localhost
    echo "Test 2: Trying localhost instead of 127.0.0.1"
    mysql -h localhost -P 3306 -u root -pNO -e "SELECT 1 as test;" 2>&1
    
    if [ $? -eq 0 ]; then
        echo "✓ localhost connection works! Update application.properties to use localhost"
    else
        echo "✗ localhost also failed"
        echo ""
        echo "Possible issues:"
        echo "1. MySQL might not be accepting TCP connections"
        echo "2. macOS network permissions might be blocking connections"
        echo "3. MySQL might need different credentials"
        echo ""
        echo "Try:"
        echo "- Grant Terminal Full Disk Access in macOS System Settings"
        echo "- Check if MySQL is actually running: lsof -i :3306"
        echo "- Try connecting without password: mysql -h 127.0.0.1 -u root"
    fi
fi

