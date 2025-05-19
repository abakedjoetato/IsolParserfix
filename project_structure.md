# Deadside Discord Bot - Project Structure and Documentation

## Overview
The Deadside Discord Bot processes game statistics from CSV files, stores them in MongoDB, and displays them using themed embeds in Discord.

## Major Components

### Core Classes
- `com.deadside.bot.Main` - Entry point that initializes MongoDB and starts the bot
- `com.deadside.bot.bot.DeadsideBot` - Main bot implementation that handles Discord connection

### Data Processing
- `com.deadside.bot.parsers.DeadsideCsvParser` - Parses death logs from CSV files
- `com.deadside.bot.utils.HistoricalDataProcessor` - Processes historical data with fixed killer attribution

### UI/Discord Components
- `com.deadside.bot.utils.EmbedThemes` - Provides consistent themed embeds with proper styling
- `com.deadside.bot.utils.DynamicTitles` - Generates varied text for different notification types
- `com.deadside.bot.utils.ResourceManager` - Handles loading and caching of image resources

### Utility Classes
- `com.deadside.bot.utils.AccessibilityUtils` - Ensures proper color contrast for UI elements

## Directory Structure
- `src/main/java/` - All source code (Maven standard structure)
- `src/main/resources/` - Resources including images for embed thumbnails
- `attached_assets/` - Original images and asset files

## Build Process
The project uses Maven for building:
```
mvn clean package
```

## Recent Fixes
1. Fixed CSV parser to correctly handle blank killer fields
2. Enhanced kill streak tracking and K/D ratio calculations
3. Created consolidated EmbedThemes class with proper styling
4. Organized directory structure to resolve duplicate file issues
5. Cleaned up redundant files to avoid future confusion

## Important Implementation Notes
- Changes to embed styling require a full Maven rebuild
- MongoDB connection must be initialized before starting the bot
- Image resources should be placed in the src/main/resources/images/ directory