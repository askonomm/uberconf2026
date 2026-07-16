# PyChat

A multi-room terminal chat application written in Python using only the standard library.

## Features

- Multiple chat rooms (public and private)
- User management (registration, login, display names)
- Message history with configurable limits
- Search across messages in rooms
- Private messaging between users
- Color output using ANSI escape codes
- Room moderation (moderators, bans, mutes)

## Prerequisites

- **Python 3.8+** — `python3 --version` to check
- **pip** — comes with Python (`pip3 --version` to check)
- A terminal with ANSI color support (most modern terminals)

On macOS: `brew install python`  
On Ubuntu/Debian: `sudo apt install python3 python3-pip`  
On Windows: download from [python.org](https://python.org)

## Usage

```bash
cd src
python3 main.py
python3 main.py --demo            # Start with demo data
python3 main.py --username alice  # Auto-login as alice
python3 main.py --no-color        # Disable ANSI colors
```

## Commands

| Command | Description |
|---------|-------------|
| `/help` | Show available commands |
| `/join <room>` | Join a chat room |
| `/leave [room]` | Leave a room |
| `/msg <user> <text>` | Send a private message |
| `/list [rooms\|users]` | List rooms or users |
| `/history [count]` | Show message history |
| `/search <query>` | Search messages |
| `/create <room>` | Create a new room |
| `/who [room]` | See who is in a room |
| `/pm <user>` | View private message history |
| `/topic [text]` | View or set room topic |
| `/info [room]` | Get room info |
| `/profile [user]` | View user profile |
| `/nick <name>` | Change display name |
| `/me <action>` | Perform an action |
| `/status <text>` | Set status message |
| `/color <color>` | Set name color |
| `/clear` | Clear the screen |
| `/quit` | Exit the application |

## Running Tests

```bash
# Install test dependencies
pip install pytest pytest-cov

# Run all tests
pytest tests/

# Run with coverage report (output in htmlcov/)
pytest --cov=src --cov-report=html tests/

# Open coverage report in browser (macOS)
open htmlcov/index.html
```

Note: the test suite is scaffolded but contains no test cases yet — see `tests/test_chat_engine.py`.
