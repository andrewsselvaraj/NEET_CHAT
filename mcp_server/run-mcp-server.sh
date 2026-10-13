#!/usr/bin/env sh
# Starts the NEET Paper Coach MCP server over stdio. Build the jar first with: mvn -DskipTests package
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$REPO_DIR/target/neet-question-chat-0.1.0.jar"
if [ ! -f "$JAR" ]; then
    echo "Missing $JAR. Run 'mvn -DskipTests package' in $REPO_DIR first." >&2
    exit 1
fi
export NEET_DOCUMENTS_DIRECTORY="${NEET_DOCUMENTS_DIRECTORY:-$REPO_DIR/Questions}"
exec java -cp "$JAR" -Dloader.main=com.neetchat.mcp.NeetMcpServer \
    org.springframework.boot.loader.launch.PropertiesLauncher
