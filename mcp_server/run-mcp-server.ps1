# Starts the NEET Paper Coach MCP server over stdio. Build the jar first with: mvn -DskipTests package
$repoDir = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repoDir "target\neet-question-chat-0.1.0.jar"
if (-not (Test-Path $jar)) {
    [Console]::Error.WriteLine("Missing $jar. Run 'mvn -DskipTests package' in $repoDir first.")
    exit 1
}
if (-not $env:NEET_DOCUMENTS_DIRECTORY) {
    $env:NEET_DOCUMENTS_DIRECTORY = Join-Path $repoDir "Questions"
}
& java -cp $jar "-Dloader.main=com.neetchat.mcp.NeetMcpServer" org.springframework.boot.loader.launch.PropertiesLauncher
exit $LASTEXITCODE
