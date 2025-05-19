
#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting Deadside Discord Bot setup...${NC}"

# Check if MongoDB is running, if not start it
if ! pgrep -x "mongod" > /dev/null; then
  echo -e "${YELLOW}Starting MongoDB...${NC}"
  mkdir -p data/db
  mongod --dbpath=data/db --logpath=data/mongod.log --fork
  if [ $? -eq 0 ]; then
    echo -e "${GREEN}MongoDB started successfully${NC}"
  else
    echo -e "${RED}Failed to start MongoDB${NC}"
    exit 1
  fi
else
  echo -e "${GREEN}MongoDB is already running${NC}"
fi

# Create directories if they don't exist
mkdir -p data/deathlogs
mkdir -p logs

# Copy test data if it doesn't exist
if [ ! -f data/deathlogs/2025.05.15-00.00.00.csv ]; then
  echo -e "${YELLOW}Copying test data...${NC}"
  cp -f backups/2025.05.15-00.00.00.csv data/deathlogs/
fi

# Compile the project
echo -e "${YELLOW}Compiling the project...${NC}"
mvn clean compile

# If compilation failed, exit
if [ $? -ne 0 ]; then
  echo -e "${RED}Compilation failed, please fix the errors and try again${NC}"
  exit 1
fi

# Package the project
echo -e "${YELLOW}Packaging the project...${NC}"
mvn package

# If packaging failed, exit
if [ $? -ne 0 ]; then
  echo -e "${RED}Packaging failed, please fix the errors and try again${NC}"
  exit 1
fi

# Run the bot with all dependencies
echo -e "${GREEN}Starting the Deadside Discord Bot...${NC}"
echo -e "${YELLOW}Using classpath with all libraries to ensure proper dependency resolution${NC}"
mvn exec:java -Dexec.mainClass="com.deadside.bot.Main"
