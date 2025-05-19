
#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting Deadside Discord Bot setup...${NC}"

# Check for MongoDB URI in environment or switch to fallback
if [ -z "$MONGO_URI" ]; then
  echo -e "${YELLOW}Warning: No MONGO_URI environment variable found${NC}"
  echo -e "${YELLOW}Using MongoDB connection string from config file${NC}"
  
  # No need to start MongoDB in Replit as we'll use MongoDB Atlas or equivalent
  echo -e "${GREEN}Skipping MongoDB local startup (using remote connection)${NC}"
else
  echo -e "${GREEN}Using MongoDB connection from environment variable${NC}"
fi

# Create required directories regardless of MongoDB connection
mkdir -p data/db
mkdir -p data/logs

# Create directories if they don't exist
mkdir -p data/deathlogs
mkdir -p logs

# Create backups directory if it doesn't exist
mkdir -p backups

# Create test data if it doesn't exist
if [ ! -f backups/2025.05.15-00.00.00.csv ]; then
  echo -e "${YELLOW}Creating sample test data...${NC}"
  cat > backups/2025.05.15-00.00.00.csv << 'EOF'
2025-05-15 00:00:01,kill,Player1,Player2,AK-47,137.5
2025-05-15 00:01:12,kill,Player3,Player4,MP5,42.8
2025-05-15 00:02:33,kill,Player2,Player3,M4A1,88.2
2025-05-15 00:03:44,kill,Player1,Player4,SVD,242.1
2025-05-15 00:04:55,kill,Player4,Player1,Knife,5.3
EOF
fi

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
