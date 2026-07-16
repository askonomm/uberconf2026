# TypeScript Chat

A multi-room CLI chat application built with TypeScript and Node.js.

## Features

- Multiple chat rooms with join/leave
- User management with usernames and display names
- Message history with pagination
- Private messaging between users
- Full-text message search across rooms
- ANSI color output (no external color libraries)
- Away status and user presence
- Room topics and descriptions
- Admin commands (ban, create rooms)
- Session timeout and activity tracking

## Commands

| Command | Description |
|---|---|
| `/help` | Show all available commands |
| `/join <room>` | Join or create a room |
| `/leave` | Leave the current room |
| `/list` | List all public rooms |
| `/users [room]` | Show users in a room |
| `/msg <user> <text>` | Send a private message |
| `/history [room] [n]` | Show message history |
| `/search <query> [room]` | Search messages |
| `/me` | View your profile |
| `/nick <name>` | Change display name |
| `/away [off]` | Toggle away status |
| `/topic [text]` | View or set room topic |
| `/stats [room <name>]` | View statistics |
| `/quit` | Exit the application |

## Prerequisites

- **Node.js 18+** — `node -v` to check
- **npm 9+** — comes with Node.js (`npm -v` to check)

On macOS: `brew install node`  
On Ubuntu/Debian: `sudo apt install nodejs npm` (or use [nvm](https://github.com/nvm-sh/nvm))  
On Windows: download from [nodejs.org](https://nodejs.org)

## Running

```bash
npm install
npm run dev
```

## Options

```
--debug       Enable verbose/debug output
--no-color    Disable ANSI color output
--verbose     Enable verbose mode
--admin <u>   Set admin username (default: admin)
--history <n> Set default history limit
--no-autojoin Don't auto-join the general room
--version     Show version info
```

## Development

```bash
npm run build              # Compile TypeScript
npm test                   # Run tests (Jest)
npm run test:coverage      # Run tests with coverage report (output in coverage/)
```

Note: the test suite is scaffolded but contains no test cases yet — see `tests/chatEngine.test.ts`.
