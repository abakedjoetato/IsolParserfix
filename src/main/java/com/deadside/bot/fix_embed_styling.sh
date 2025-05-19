#!/bin/bash

# Script to fix embed styling across commands
# This script adds a common fix to ensure all embeds use the Deadside styling

echo "Fixing embed styling in commands..."

# Create a temporary file to save our hooks
find src/main/java/com/deadside/bot/commands/ -name "*.java" -type f | while read -r file; do
  echo "Processing $file..."
  
  # Fix direct sendMessageEmbeds calls to use EmbedSender instead
  sed -i 's/\(event\.getHook\(\)\.sendMessageEmbeds\)(EmbedUtils\.\([^(]*\)(\([^)]*\)))/EmbedSender.sendEmbed(\1, EmbedUtils.\2(\3))/g' "$file"
  
  # Fix interaction replies to use EmbedSender
  sed -i 's/\(event\.\)replyEmbeds(EmbedUtils\.\([^(]*\)(\([^)]*\)))\.setEphemeral(\([^)]*\))\.queue()/EmbedSender.replyEmbed(\1, EmbedUtils.\2(\3), \4)/g' "$file"
  
  # Import the EmbedSender class if it's not already imported
  grep -q "import com.deadside.bot.utils.EmbedSender;" "$file" || sed -i '/import com.deadside.bot.utils.EmbedUtils;/a import com.deadside.bot.utils.EmbedSender;' "$file"
done

echo "Embed styling fixes applied!"