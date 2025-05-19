# Deadside Discord Bot

A multi-guild Discord bot for Deadside servers with SFTP integration, statistics tracking, factions, economy, and premium features.

## Features

- SFTP integration to pull game server logs
- Kill feed with detailed player statistics
- Faction system with ranks, bank, and leveling
- Economy system with player balances
- Premium tier system via Tip4serv
- Multi-guild support
- Real-time game events and notifications
- Emerald-themed UI elements

## Setup Instructions

### Prerequisites

- JDK 17 or higher
- Maven
- MongoDB database
- Discord bot token

### Environment Variables

The following environment variables are required:

- `DISCORD_TOKEN`: Your Discord bot token
- `MONGODB_URI`: MongoDB connection string

### Running the Bot

#### Using Replit

1. Click the Run button or execute the start script:
   ```
   ./start.sh
   ```

#### Running Locally

1. Build the project:
   ```
   mvn package -DskipTests
   ```

2. Run the bot:
   ```
   mvn exec:java -Dexec.mainClass="com.deadside.bot.Main" -Dexec.cleanupDaemonThreads=false
   ```

## Commands

### Admin Commands

- `/server add` - Add a new game server
- `/server remove` - Remove a game server
- `/server list` - List all configured game servers
- `/server test` - Test SFTP connection to a server
- `/server setkillfeed` - Set the killfeed channel
- `/server setlogs` - Set the server log channel

### Player Commands

- `/stats` - View player statistics
- `/link` - Link Discord account to game account
- `/leaderboard` - View server leaderboards

### Economy Commands

- `/balance` - Check your balance
- `/daily` - Claim daily rewards
- `/work` - Earn emeralds through work

### Faction Commands

- `/faction create` - Create a new faction
- `/faction invite` - Invite a player to your faction
- `/faction bank` - Manage faction bank

## License

All rights reserved. © 2025 Deadside Bot