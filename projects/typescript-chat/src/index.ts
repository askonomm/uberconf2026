import * as readline from 'readline';
import * as ChatEngine from './chatEngine';
import * as UserManager from './userManager';
import * as RoomManager from './roomManager';
import * as MessageStore from './messageStore';
import * as DisplayRenderer from './displayRenderer';
import * as CommandProcessor from './commandProcessor';
import { ChatMessage } from './types';

// ANSI codes - duplicated again (intentionally bad practice)
const RESET = '\x1b[0m';
const BOLD = '\x1b[1m';
const DIM = '\x1b[2m';
const RED = '\x1b[31m';
const GREEN = '\x1b[32m';
const YELLOW = '\x1b[33m';
const CYAN = '\x1b[36m';

const DEFAULT_CONFIG: ChatEngine.EngineConfig = {
  adminUsername: 'admin',
  historyLimit: 50,
  searchLimit: 20,
  verboseMode: false,
  echoCommands: false,
  showTimestamps: true,
  colorEnabled: true,
  autoJoinRoom: 'general',
  maxMessageLength: 2000,
};

function parseArgs(argv: string[]): Partial<ChatEngine.EngineConfig> & { debug: boolean; noColor: boolean; version: boolean } {
  const result: Partial<ChatEngine.EngineConfig> & { debug: boolean; noColor: boolean; version: boolean } = {
    debug: false,
    noColor: false,
    version: false,
  };

  for (let i = 2; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--debug' || arg === '-d') {
      result.debug = true;
      result.verboseMode = true;
    } else if (arg === '--no-color') {
      result.noColor = true;
      result.colorEnabled = false;
    } else if (arg === '--version' || arg === '-v') {
      result.version = true;
    } else if (arg === '--admin' && argv[i + 1]) {
      result.adminUsername = argv[++i];
    } else if (arg === '--history' && argv[i + 1]) {
      const n = parseInt(argv[++i], 10);
      if (!isNaN(n) && n > 0) result.historyLimit = n;
    } else if (arg === '--no-autojoin') {
      result.autoJoinRoom = null;
    } else if (arg === '--verbose') {
      result.verboseMode = true;
    } else if (arg === '--timestamps') {
      result.showTimestamps = true;
    }
  }

  return result;
}

function printVersion(): void {
  console.log('TypeScript Chat v1.0.0');
  console.log('Node.js ' + process.version);
  console.log('Platform: ' + process.platform);
}

function setupSeedData(adminUserId: string): void {
  RoomManager.initDefaultRooms(adminUserId);

  const general = RoomManager.getRoomByName('general');
  const random = RoomManager.getRoomByName('random');
  const tech = RoomManager.getRoomByName('tech');

  if (general && random && tech) {
    RoomManager.updateRoomTopic(general.id, 'Welcome to the chat! Be kind and have fun.');
    RoomManager.updateRoomTopic(tech.id, 'Discussing TypeScript, Node.js, and everything tech');

    const botUser = UserManager.createUser('chatbot', 'ChatBot');
    if (botUser) {
      RoomManager.addMemberToRoom(general.id, botUser.id);
      RoomManager.addMemberToRoom(random.id, botUser.id);
      RoomManager.addMemberToRoom(tech.id, botUser.id);
      UserManager.updateUserRoom(botUser.id, general.id);

      MessageStore.storeMessage(general.id, botUser.id, 'ChatBot', 'Welcome to TypeScript Chat! 👋', false, null);
      MessageStore.storeMessage(general.id, botUser.id, 'ChatBot', 'Type /help to see available commands.', false, null);
      MessageStore.storeMessage(general.id, botUser.id, 'ChatBot', 'Use /list to see all rooms, /join <room> to enter one.', false, null);
      MessageStore.storeMessage(random.id, botUser.id, 'ChatBot', 'This is the random chat room - talk about anything!', false, null);
      MessageStore.storeMessage(tech.id, botUser.id, 'ChatBot', 'Welcome to #tech! Share your latest coding adventures.', false, null);
    }
  }
}

function renderCurrentPrompt(state: ChatEngine.EngineState): void {
  const user = UserManager.getUserById(state.appState.currentUserId || '');
  const room = state.appState.currentRoomId
    ? RoomManager.getRoomById(state.appState.currentRoomId)
    : null;

  const prompt = DisplayRenderer.renderPrompt(
    user?.displayName || 'user',
    room?.name || null
  );

  process.stdout.write(prompt);
}

function handleOutput(output: string, state: ChatEngine.EngineState): void {
  if (!output) return;
  process.stdout.clearLine(0);
  process.stdout.cursorTo(0);
  console.log(output);
  renderCurrentPrompt(state);
}

function handleError(err: string, state: ChatEngine.EngineState): void {
  process.stdout.clearLine(0);
  process.stdout.cursorTo(0);
  console.log(CommandProcessor.renderError(err));
  renderCurrentPrompt(state);
}

async function main(): Promise<void> {
  const parsedArgs = parseArgs(process.argv);

  if (parsedArgs.version) {
    printVersion();
    process.exit(0);
  }

  const config: ChatEngine.EngineConfig = {
    ...DEFAULT_CONFIG,
    ...parsedArgs,
  };

  if (parsedArgs.debug) {
    console.log(DIM + 'Debug mode enabled' + RESET);
    console.log(DIM + 'Config: ' + JSON.stringify(config) + RESET);
  }

  const engineState = ChatEngine.createEngine(config);
  engineState.appState.isRunning = true;

  // Create admin user for seed data
  const seedAdmin = UserManager.createUser('system', 'System');
  if (!seedAdmin) {
    console.error(RED + 'Fatal: could not create system user' + RESET);
    process.exit(1);
  }
  setupSeedData(seedAdmin.id);

  const publicRooms = RoomManager.getPublicRooms();

  console.clear();

  // Run login
  await new Promise<void>((resolve) => {
    ChatEngine.runLoginFlow(
      engineState.rl,
      config.adminUsername,
      true,
      false,
      '',
      5,
      (userId, username) => {
        engineState.appState.currentUserId = userId;
        const user = UserManager.getUserById(userId)!;
        const isAdmin = username === config.adminUsername;

        console.log(DisplayRenderer.renderWelcome(username, user.displayName, publicRooms.length));

        // Auto-join default room
        if (config.autoJoinRoom) {
          const autoRoom = RoomManager.getRoomByName(config.autoJoinRoom);
          if (autoRoom) {
            RoomManager.addMemberToRoom(autoRoom.id, userId);
            UserManager.updateUserRoom(userId, autoRoom.id);
            engineState.appState.currentRoomId = autoRoom.id;

            console.log(DisplayRenderer.renderRoomHeader(autoRoom, autoRoom.memberIds.length, MessageStore.getRoomMessageCount(autoRoom.id), true));

            const recent = MessageStore.getMessagesForRoom(autoRoom.id, 10, 0);
            if (recent.length > 0) {
              const history = DisplayRenderer.renderMessageHistory(recent, autoRoom.name, config.showTimestamps, false, true, null);
              console.log(DIM + '--- Recent messages ---' + RESET);
              console.log(history);
            }
          }
        }

        resolve();
      },
      (reason) => {
        console.error(RED + 'Login failed: ' + reason + RESET);
        process.exit(1);
      }
    );
  });

  // Main input loop
  renderCurrentPrompt(engineState);

  engineState.rl.on('line', (line) => {
    const input = line.trim();

    process.stdout.clearLine(0);
    process.stdout.cursorTo(0);

    const currentUser = UserManager.getUserById(engineState.appState.currentUserId || '');
    const isAdmin = currentUser?.username === config.adminUsername;

    ChatEngine.processInput(
      engineState,
      input,
      isAdmin,
      true,
      true,
      () => {
        // quit handler
        ChatEngine.shutdownEngine(engineState, false, true);
        process.exit(0);
      },
      (newRoomId) => {
        // room change handler - show new room info
        if (newRoomId) {
          const newRoom = RoomManager.getRoomById(newRoomId);
          if (newRoom) {
            const memberCount = newRoom.memberIds.length;
            const msgCount = MessageStore.getRoomMessageCount(newRoomId);
            console.log(DisplayRenderer.renderRoomHeader(newRoom, memberCount, msgCount, true));
          }
        } else {
          console.log(DIM + 'Left room. Use /join <room> to enter another.' + RESET);
        }
        renderCurrentPrompt(engineState);
      },
      (err) => handleError(err, engineState),
      (out) => handleOutput(out, engineState)
    );

    // flush any queued notifications
    ChatEngine.flushNotifications(engineState, (notification) => {
      console.log('\n' + notification);
    });

    renderCurrentPrompt(engineState);
  });

  // Activity check timer
  const activityTimer = setInterval(() => {
    ChatEngine.checkSessionActivity(
      engineState,
      30,
      () => {
        console.log('\n' + YELLOW + 'Session timed out due to inactivity.' + RESET);
        ChatEngine.shutdownEngine(engineState, false, true);
        clearInterval(activityTimer);
        process.exit(0);
      },
      (minutesLeft) => {
        console.log('\n' + DIM + 'Idle warning: ' + minutesLeft + ' minute(s) until session timeout' + RESET);
        renderCurrentPrompt(engineState);
      }
    );
  }, 60000);

  // Graceful shutdown on signals
  process.on('SIGINT', () => {
    console.log('\n' + DIM + 'Caught SIGINT, shutting down...' + RESET);
    ChatEngine.shutdownEngine(engineState, false, true);
    clearInterval(activityTimer);
    process.exit(0);
  });

  process.on('SIGTERM', () => {
    console.log('\n' + DIM + 'Caught SIGTERM, shutting down...' + RESET);
    ChatEngine.shutdownEngine(engineState, false, true);
    clearInterval(activityTimer);
    process.exit(0);
  });

  engineState.rl.on('close', () => {
    ChatEngine.shutdownEngine(engineState, false, true);
    clearInterval(activityTimer);
    process.exit(0);
  });
}

main().catch((err) => {
  console.error(RED + BOLD + 'Fatal error: ' + RESET + RED + String(err) + RESET);
  process.exit(1);
});
