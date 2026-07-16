# java-chat

A multi-room terminal UI (TUI) chat application written in Java. Runs entirely in the terminal using ANSI escape codes for color output - no external TUI libraries required.

## Features

- Multiple chat rooms with join/leave
- User management (registration, status, profiles, blocking, friends)
- Message history with pagination
- Private messaging between users
- Full-text message search
- ANSI color output
- Command-driven interface

## Commands

| Command | Description |
|---|---|
| `/help` | Show available commands |
| `/join <room>` | Join a chat room |
| `/leave` | Leave current room |
| `/msg <user> <text>` | Send a private message |
| `/list` | List all rooms |
| `/history [n]` | Show last n messages |
| `/search <query>` | Search messages |
| `/users` | Show online users |
| `/status <status>` | Set your status |
| `/profile [user]` | View user profile |
| `/stats` | Show chat statistics |
| `/quit` | Exit |

## Prerequisites

- **Java 17+** — `java -version` to check
- **Maven 3.6+** — `mvn -version` to check

On macOS: `brew install openjdk maven`  
On Ubuntu/Debian: `sudo apt install openjdk-17-jdk maven`  
On Windows: download from [adoptium.net](https://adoptium.net) and [maven.apache.org](https://maven.apache.org)

## Build & Run

```bash
mvn package
java -jar target/java-chat-1.0-SNAPSHOT.jar
```

## Options

```
--debug, -d      Enable debug mode
--no-color, -n   Disable color output
--user <name>    Set username from command line
--room <name>    Start in specified room
```

## Running Tests

```bash
# Run all tests
mvn test

# Run tests with coverage report (output in target/site/jacoco/)
mvn verify

# Open coverage report in browser (macOS)
open target/site/jacoco/index.html
```

Note: the test suite is scaffolded but contains no test cases yet — see `src/test/java/com/chatapp/ChatApplicationTest.java`.
