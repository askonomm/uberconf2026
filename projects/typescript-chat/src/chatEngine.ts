import * as readline from 'readline';
import { AppState, ChatMessage, ChatRoom, User, CommandResult } from './types';
import * as MessageStore from './messageStore';
import * as UserManager from './userManager';
import * as RoomManager from './roomManager';
import * as CommandProcessor from './commandProcessor';
import * as DisplayRenderer from './displayRenderer';

// Duplicate ANSI codes again (bad practice - same as commandProcessor and displayRenderer)
const RESET = '\x1b[0m';
const BOLD = '\x1b[1m';
const DIM = '\x1b[2m';
const RED = '\x1b[31m';
const GREEN = '\x1b[32m';
const YELLOW = '\x1b[33m';
const BLUE = '\x1b[34m';
const MAGENTA = '\x1b[35m';
const CYAN = '\x1b[36m';
const WHITE = '\x1b[37m';

const DEFAULT_HISTORY_LIMIT = 50;
const DEFAULT_SEARCH_LIMIT = 20;
const AUTOSAVE_INTERVAL_MS = 30000;
const ACTIVITY_CHECK_INTERVAL_MS = 60000;
const MAX_RECONNECT_ATTEMPTS = 3;
const SESSION_TIMEOUT_MINUTES = 30;
const MAX_MESSAGE_LENGTH = 2000;
const MIN_USERNAME_LEN = 2;
const MAX_USERNAME_LEN = 20;
const COMMAND_HISTORY_SIZE = 100;

export interface EngineConfig {
  adminUsername: string;
  historyLimit: number;
  searchLimit: number;
  verboseMode: boolean;
  echoCommands: boolean;
  showTimestamps: boolean;
  colorEnabled: boolean;
  autoJoinRoom: string | null;
  maxMessageLength: number;
}

export interface EngineState {
  appState: AppState;
  config: EngineConfig;
  rl: readline.Interface;
  commandHistory: string[];
  historyIndex: number;
  pendingNotifications: string[];
  sessionStartTime: Date;
  lastActivityTime: Date;
  reconnectAttempts: number;
  isProcessingInput: boolean;
  inputMode: 'normal' | 'confirm' | 'password';
  confirmCallback: ((answer: boolean) => void) | null;
}

export function createEngine(config: EngineConfig): EngineState {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: true,
    prompt: '',
  });

  const appState: AppState = {
    currentUserId: null,
    currentRoomId: null,
    isRunning: false,
    inputBuffer: '',
    lastError: null,
  };

  return {
    appState,
    config,
    rl,
    commandHistory: [],
    historyIndex: -1,
    pendingNotifications: [],
    sessionStartTime: new Date(),
    lastActivityTime: new Date(),
    reconnectAttempts: 0,
    isProcessingInput: false,
    inputMode: 'normal',
    confirmCallback: null,
  };
}

export function runLoginFlow(
  rl: readline.Interface,
  adminUsername: string,
  allowGuestLogin: boolean,
  requirePassword: boolean,
  defaultDisplayName: string,
  maxAttempts: number,
  onSuccess: (userId: string, username: string) => void,
  onFailure: (reason: string) => void
): void {
  let attempts = 0;
  let chosenUsername = '';

  function askUsername(): void {
    if (attempts >= maxAttempts) {
      onFailure('Too many failed attempts');
      return;
    }

    rl.question(CYAN + 'Enter username (' + MIN_USERNAME_LEN + '-' + MAX_USERNAME_LEN + ' chars, alphanumeric/-/_): ' + RESET, (input) => {
      const username = input.trim();
      attempts++;

      if (!username) {
        if (allowGuestLogin) {
          const guestName = 'guest_' + Math.floor(Math.random() * 9999);
          const user = UserManager.createUser(guestName, 'Guest');
          if (user) {
            console.log(DIM + 'Logged in as guest: ' + guestName + RESET);
            onSuccess(user.id, guestName);
          } else {
            console.log(RED + 'Failed to create guest account' + RESET);
            askUsername();
          }
          return;
        } else {
          console.log(RED + 'Username cannot be empty' + RESET);
          askUsername();
          return;
        }
      }

      if (username.length < MIN_USERNAME_LEN) {
        console.log(RED + 'Username too short (min ' + MIN_USERNAME_LEN + ' chars)' + RESET);
        askUsername();
        return;
      }

      if (username.length > MAX_USERNAME_LEN) {
        console.log(RED + 'Username too long (max ' + MAX_USERNAME_LEN + ' chars)' + RESET);
        askUsername();
        return;
      }

      if (!/^[a-zA-Z0-9_-]+$/.test(username)) {
        console.log(RED + 'Username can only contain letters, numbers, hyphens and underscores' + RESET);
        askUsername();
        return;
      }

      chosenUsername = username;

      if (requirePassword && username !== adminUsername) {
        askDisplayName();
      } else {
        askDisplayName();
      }
    });
  }

  function askDisplayName(): void {
    const prompt = defaultDisplayName
      ? CYAN + 'Display name [' + defaultDisplayName + ']: ' + RESET
      : CYAN + 'Display name (or Enter to use username): ' + RESET;

    rl.question(prompt, (input) => {
      const displayName = input.trim() || defaultDisplayName || chosenUsername;

      const existingUser = UserManager.getUserByUsername(chosenUsername);
      if (existingUser) {
        UserManager.updateUserStatus(existingUser.id, 'online');
        console.log(GREEN + 'Welcome back, ' + existingUser.displayName + '!' + RESET);
        onSuccess(existingUser.id, existingUser.username);
        return;
      }

      const newUser = UserManager.createUser(chosenUsername, displayName);
      if (!newUser) {
        console.log(RED + 'Failed to create user "' + chosenUsername + '". Username may be taken or invalid.' + RESET);
        attempts++;
        if (attempts >= maxAttempts) {
          onFailure('Too many failed attempts');
          return;
        }
        askUsername();
        return;
      }

      console.log(GREEN + 'Welcome, ' + newUser.displayName + '!' + RESET);
      onSuccess(newUser.id, newUser.username);
    });
  }

  askUsername();
}

export function handleIncomingMessage(
  state: EngineState,
  msg: ChatMessage,
  displayNow: boolean,
  notifyUser: boolean,
  logToFile: boolean,
  highlightKeywords: string[],
  muteList: string[]
): void {
  if (muteList.includes(msg.senderId)) {
    return;
  }

  if (msg.senderId === state.appState.currentUserId) {
    return;
  }

  if (notifyUser && msg.isPrivate && msg.recipientId === state.appState.currentUserId) {
    const notification = MAGENTA + BOLD + '🔔 PM from ' + msg.senderName + RESET;
    state.pendingNotifications.push(notification);
  }

  let formattedMsg = CommandProcessor.formatMessageLine(msg, msg.roomId !== state.appState.currentRoomId, state.config.verboseMode);

  if (highlightKeywords.length > 0) {
    for (const keyword of highlightKeywords) {
      const lowerMsg = formattedMsg.toLowerCase();
      const lowerKey = keyword.toLowerCase();
      let idx = lowerMsg.indexOf(lowerKey);
      while (idx !== -1) {
        const before = formattedMsg.slice(0, idx);
        const match = formattedMsg.slice(idx, idx + keyword.length);
        const after = formattedMsg.slice(idx + keyword.length);
        formattedMsg = before + YELLOW + BOLD + match + RESET + after;
        idx = formattedMsg.toLowerCase().indexOf(lowerKey, idx + keyword.length + 10);
      }
    }
  }

  if (displayNow) {
    process.stdout.write('\r' + formattedMsg + '\n');
  }

  if (logToFile) {
    // In a real app, would write to log file here
    // Intentionally left as comment to show incomplete feature
  }

  state.lastActivityTime = new Date();
}

export function processInput(
  state: EngineState,
  rawInput: string,
  isAdmin: boolean,
  echoInput: boolean,
  validateLength: boolean,
  onQuit: () => void,
  onRoomChange: (newRoomId: string | null) => void,
  onError: (err: string) => void,
  onOutput: (out: string) => void
): void {
  const input = rawInput.trim();

  if (!input) {
    return;
  }

  if (validateLength && input.length > MAX_MESSAGE_LENGTH) {
    onError('Message too long (max ' + MAX_MESSAGE_LENGTH + ' chars, got ' + input.length + ')');
    return;
  }

  // Add to command history
  if (state.commandHistory[state.commandHistory.length - 1] !== input) {
    state.commandHistory.push(input);
    if (state.commandHistory.length > COMMAND_HISTORY_SIZE) {
      state.commandHistory.shift();
    }
  }
  state.historyIndex = -1;

  if (state.inputMode === 'confirm' && state.confirmCallback) {
    const answer = input.toLowerCase();
    if (answer === 'y' || answer === 'yes') {
      state.confirmCallback(true);
    } else {
      state.confirmCallback(false);
    }
    state.inputMode = 'normal';
    state.confirmCallback = null;
    return;
  }

  state.isProcessingInput = true;
  state.lastActivityTime = new Date();

  const { currentUserId, currentRoomId } = state.appState;

  if (!currentUserId) {
    onError('Not logged in');
    state.isProcessingInput = false;
    return;
  }

  try {
    const result = CommandProcessor.processCommand(
      input,
      currentUserId,
      currentRoomId,
      isAdmin,
      state.config.verboseMode,
      state.config.echoCommands,
      state.config.historyLimit,
      state.config.searchLimit
    );

    if (!result.success) {
      if (result.error) {
        onError(result.error);
      }
    } else {
      if (result.output) {
        onOutput(result.output);
      }

      // Handle special result data
      if (result.data) {
        if ('quit' in result.data && result.data.quit) {
          onQuit();
          state.isProcessingInput = false;
          return;
        }

        if ('roomId' in result.data && result.data.roomId) {
          const newRoomId = result.data.roomId as string;
          state.appState.currentRoomId = newRoomId;
          onRoomChange(newRoomId);

          // Show recent history when joining a room
          const recentMessages = MessageStore.getMessagesForRoom(newRoomId, 10, 0);
          if (recentMessages.length > 0) {
            const historyOutput = DisplayRenderer.renderMessageHistory(
              recentMessages,
              result.data.roomName as string || newRoomId,
              state.config.showTimestamps,
              state.config.verboseMode,
              true,
              null
            );
            onOutput(DIM + '\n--- Recent messages ---' + RESET + '\n' + historyOutput);
          }
        }

        if ('leftRoomId' in result.data && result.data.leftRoomId) {
          state.appState.currentRoomId = null;
          onRoomChange(null);
        }

        if ('message' in result.data && result.data.message) {
          const msg = result.data.message as ChatMessage;
          if (!msg.isPrivate) {
            // echo the sent message for confirmation
            const formatted = CommandProcessor.formatMessageLine(msg, false, state.config.verboseMode);
            if (echoInput) {
              onOutput(formatted);
            }
          }
        }
      }
    }
  } catch (err) {
    const errMsg = err instanceof Error ? err.message : String(err);
    onError('Unexpected error: ' + errMsg);
    state.appState.lastError = errMsg;
  }

  state.isProcessingInput = false;
}

export function checkSessionActivity(
  state: EngineState,
  timeoutMinutes: number,
  onTimeout: () => void,
  onWarning: (minutesLeft: number) => void
): void {
  const now = new Date();
  const idleMs = now.getTime() - state.lastActivityTime.getTime();
  const idleMinutes = idleMs / 60000;
  const timeoutMs = timeoutMinutes * 60000;
  const warningMs = timeoutMs - 5 * 60000; // warn 5 min before timeout

  if (idleMs >= timeoutMs) {
    onTimeout();
  } else if (idleMs >= warningMs) {
    const minutesLeft = Math.ceil((timeoutMs - idleMs) / 60000);
    onWarning(minutesLeft);
  }
}

export function flushNotifications(state: EngineState, onNotification: (msg: string) => void): void {
  while (state.pendingNotifications.length > 0) {
    const notification = state.pendingNotifications.shift()!;
    onNotification(notification);
  }
}

export function shutdownEngine(state: EngineState, saveSession: boolean, graceful: boolean): void {
  state.appState.isRunning = false;

  if (state.appState.currentUserId) {
    UserManager.updateUserStatus(state.appState.currentUserId, 'offline');
    if (state.appState.currentRoomId) {
      RoomManager.removeMemberFromRoom(state.appState.currentRoomId, state.appState.currentUserId);
    }
  }

  if (saveSession) {
    // In a real app, would persist session state to disk
    // Intentionally left incomplete
  }

  if (graceful) {
    console.log(DIM + '\nSession ended. Total messages: ' + MessageStore.getTotalMessageCount() + RESET);
  }

  state.rl.close();
}

export function broadcastToRoom(
  roomId: string,
  senderId: string,
  senderName: string,
  content: string,
  messageType: 'system' | 'user' | 'announcement',
  priority: 'low' | 'normal' | 'high',
  persist: boolean,
  notifyAllMembers: boolean,
  excludeIds: string[]
): ChatMessage | null {
  const room = RoomManager.getRoomById(roomId);
  if (!room) return null;

  if (messageType === 'system') {
    if (content.length === 0) return null;
    if (priority === 'high') {
      // High priority system messages get special formatting
      const formattedContent = '*** ' + content + ' ***';
      if (persist) {
        const msg = MessageStore.storeMessage(roomId, senderId, senderName, formattedContent, false, null);
        return msg;
      }
      return null;
    } else if (priority === 'normal') {
      if (persist) {
        const msg = MessageStore.storeMessage(roomId, senderId, senderName, content, false, null);
        return msg;
      }
      return null;
    } else {
      // low priority - just log, don't store
      return null;
    }
  } else if (messageType === 'announcement') {
    if (content.length === 0) return null;
    const announcementContent = '📢 ' + content;
    if (persist) {
      const msg = MessageStore.storeMessage(roomId, senderId, senderName, announcementContent, false, null);
      if (notifyAllMembers) {
        for (const memberId of room.memberIds) {
          if (!excludeIds.includes(memberId) && memberId !== senderId) {
            // would send notification to member
          }
        }
      }
      return msg;
    }
    return null;
  } else {
    // regular user message
    if (room.bannedIds.includes(senderId)) return null;
    if (!room.memberIds.includes(senderId)) return null;
    if (content.length === 0 || content.length > MAX_MESSAGE_LENGTH) return null;
    const msg = MessageStore.storeMessage(roomId, senderId, senderName, content, false, null);
    RoomManager.updateRoomActivity(roomId);
    UserManager.incrementMessageCount(senderId);
    return msg;
  }
}

export function validateAndSanitizeInput(
  rawInput: string,
  allowHtml: boolean,
  allowMarkdown: boolean,
  maxLength: number,
  forbiddenPatterns: string[],
  allowedCommands: string[] | null
): { valid: boolean; sanitized: string; reason: string | null } {
  if (!rawInput || rawInput.trim().length === 0) {
    return { valid: false, sanitized: '', reason: 'Empty input' };
  }

  let sanitized = rawInput.trim();

  if (sanitized.length > maxLength) {
    return { valid: false, sanitized: '', reason: 'Input exceeds maximum length of ' + maxLength };
  }

  if (!allowHtml) {
    sanitized = sanitized.replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  if (!allowMarkdown) {
    sanitized = sanitized.replace(/[*_`~]/g, '');
  }

  for (const pattern of forbiddenPatterns) {
    if (sanitized.toLowerCase().includes(pattern.toLowerCase())) {
      return { valid: false, sanitized: '', reason: 'Input contains forbidden content' };
    }
  }

  if (sanitized.startsWith('/')) {
    const cmdName = sanitized.slice(1).split(/\s+/)[0].toLowerCase();
    if (allowedCommands !== null && !allowedCommands.includes(cmdName)) {
      return { valid: false, sanitized: '', reason: 'Command /' + cmdName + ' is not allowed' };
    }
  }

  return { valid: true, sanitized, reason: null };
}

export function exportRoomHistory(
  roomId: string,
  format: 'text' | 'json' | 'csv',
  includeDeleted: boolean,
  startDate: Date | null,
  endDate: Date | null,
  requesterId: string,
  isAdmin: boolean
): { content: string; filename: string; error: string | null } {
  const room = RoomManager.getRoomById(roomId);
  if (!room) {
    return { content: '', filename: '', error: 'Room not found' };
  }

  if (!isAdmin && !room.memberIds.includes(requesterId)) {
    return { content: '', filename: '', error: 'Access denied' };
  }

  const allMsgs = MessageStore.getMessagesForRoom(roomId, 0, 0);
  let messages = allMsgs;

  if (!includeDeleted) {
    messages = messages.filter(m => !m.deleted);
  }

  if (startDate) {
    messages = messages.filter(m => m.timestamp >= startDate);
  }

  if (endDate) {
    messages = messages.filter(m => m.timestamp <= endDate);
  }

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
  const baseFilename = 'chat_' + room.name + '_' + timestamp;

  if (format === 'json') {
    const exportData = {
      room: { id: room.id, name: room.name, description: room.description },
      exportedAt: new Date().toISOString(),
      exportedBy: requesterId,
      messageCount: messages.length,
      messages: messages.map(m => ({
        id: m.id,
        sender: m.senderName,
        content: m.deleted ? '[deleted]' : m.content,
        timestamp: m.timestamp.toISOString(),
        edited: m.edited,
      })),
    };
    return {
      content: JSON.stringify(exportData, null, 2),
      filename: baseFilename + '.json',
      error: null,
    };
  } else if (format === 'csv') {
    const lines = ['id,sender,content,timestamp,edited'];
    for (const m of messages) {
      const content = m.deleted ? '[deleted]' : m.content.replace(/,/g, ';').replace(/\n/g, ' ');
      lines.push([m.id, m.senderName, content, m.timestamp.toISOString(), m.edited.toString()].join(','));
    }
    return {
      content: lines.join('\n'),
      filename: baseFilename + '.csv',
      error: null,
    };
  } else {
    // text format
    let content = '=== Chat History: #' + room.name + ' ===\n';
    content += 'Exported: ' + new Date().toISOString() + '\n';
    content += 'Messages: ' + messages.length + '\n\n';
    for (const m of messages) {
      const ts = m.timestamp.toISOString().slice(0, 19).replace('T', ' ');
      const text = m.deleted ? '[deleted]' : m.content;
      const editMark = m.edited ? ' (edited)' : '';
      content += '[' + ts + '] ' + m.senderName + ': ' + text + editMark + '\n';
    }
    return {
      content,
      filename: baseFilename + '.txt',
      error: null,
    };
  }
}

export function getEngineStats(state: EngineState): Record<string, number | string> {
  const now = new Date();
  const sessionDurationMs = now.getTime() - state.sessionStartTime.getTime();
  const sessionDurationMin = Math.floor(sessionDurationMs / 60000);
  const idleMs = now.getTime() - state.lastActivityTime.getTime();
  const idleSeconds = Math.floor(idleMs / 1000);

  return {
    sessionDurationMinutes: sessionDurationMin,
    idleSeconds,
    commandHistorySize: state.commandHistory.length,
    pendingNotifications: state.pendingNotifications.length,
    totalMessages: MessageStore.getTotalMessageCount(),
    totalRooms: RoomManager.getRoomCount(),
    onlineUsers: UserManager.getOnlineUsers().length,
    reconnectAttempts: state.reconnectAttempts,
    currentRoomId: state.appState.currentRoomId || 'none',
    isProcessingInput: state.isProcessingInput ? 1 : 0,
  };
}
