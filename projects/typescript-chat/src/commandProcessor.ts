import { ChatMessage, ChatRoom, User, SearchResult, CommandResult } from './types';
import * as MessageStore from './messageStore';
import * as UserManager from './userManager';
import * as RoomManager from './roomManager';

// ANSI color helpers - inlined everywhere (intentional duplication)
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
const BG_RED = '\x1b[41m';
const BG_BLUE = '\x1b[44m';

export function processCommand(
  rawInput: string,
  currentUserId: string,
  currentRoomId: string | null,
  isAdmin: boolean,
  verboseMode: boolean,
  echoCommands: boolean,
  maxHistoryLines: number,
  searchResultLimit: number
): CommandResult {
  const trimmed = rawInput.trim();
  if (!trimmed) {
    return { success: false, output: '', error: 'Empty input', data: null };
  }

  if (!trimmed.startsWith('/')) {
    // regular message
    if (!currentRoomId) {
      return { success: false, output: '', error: 'You are not in any room. Use /join <room> first.', data: null };
    }
    const user = UserManager.getUserById(currentUserId);
    if (!user) {
      return { success: false, output: '', error: 'User not found', data: null };
    }
    const room = RoomManager.getRoomById(currentRoomId);
    if (!room) {
      return { success: false, output: '', error: 'Room not found', data: null };
    }
    if (room.bannedIds.includes(currentUserId)) {
      return { success: false, output: '', error: 'You are banned from this room', data: null };
    }
    if (!room.memberIds.includes(currentUserId)) {
      return { success: false, output: '', error: 'You must join the room first', data: null };
    }
    const msg = MessageStore.storeMessage(currentRoomId, currentUserId, user.displayName, trimmed, false, null);
    UserManager.incrementMessageCount(currentUserId);
    RoomManager.updateRoomActivity(currentRoomId);
    return { success: true, output: '', error: null, data: { message: msg } };
  }

  const parts = trimmed.slice(1).split(/\s+/);
  const cmd = parts[0].toLowerCase();
  const args = parts.slice(1);

  if (cmd === 'help') {
    const helpText = buildHelpText(isAdmin, verboseMode);
    return { success: true, output: helpText, error: null, data: null };
  }

  if (cmd === 'quit' || cmd === 'exit' || cmd === 'q') {
    return { success: true, output: CYAN + 'Goodbye!' + RESET, error: null, data: { quit: true } };
  }

  if (cmd === 'join') {
    if (args.length === 0) {
      return { success: false, output: '', error: 'Usage: /join <room-name>', data: null };
    }
    const roomName = args[0];
    let targetRoom = RoomManager.getRoomByName(roomName);
    if (!targetRoom) {
      if (args.length > 1 || isAdmin) {
        const desc = args.slice(1).join(' ') || 'No description';
        targetRoom = RoomManager.createRoom(roomName, desc, currentUserId, false, 100);
        if (!targetRoom) {
          return { success: false, output: '', error: 'Failed to create room "' + roomName + '". Name may be invalid or taken.', data: null };
        }
      } else {
        return { success: false, output: '', error: 'Room "' + roomName + '" not found. Use /join <name> <description> to create it.', data: null };
      }
    }
    if (targetRoom.bannedIds.includes(currentUserId)) {
      return { success: false, output: '', error: 'You are banned from room "' + roomName + '"', data: null };
    }
    if (targetRoom.isPrivate && targetRoom.createdBy !== currentUserId && !isAdmin) {
      return { success: false, output: '', error: 'Room "' + roomName + '" is private', data: null };
    }
    const joined = RoomManager.addMemberToRoom(targetRoom.id, currentUserId);
    if (!joined) {
      return { success: false, output: '', error: 'Could not join room "' + roomName + '". It may be full.', data: null };
    }
    UserManager.updateUserRoom(currentUserId, targetRoom.id);
    const memberCount = targetRoom.memberIds.length + 1;
    let joinMsg = GREEN + BOLD + '✓ Joined #' + targetRoom.name + RESET;
    if (targetRoom.description) {
      joinMsg += '\n' + DIM + '  ' + targetRoom.description + RESET;
    }
    if (targetRoom.topic) {
      joinMsg += '\n' + YELLOW + '  Topic: ' + targetRoom.topic + RESET;
    }
    joinMsg += '\n' + DIM + '  Members online: ' + memberCount + RESET;
    return { success: true, output: joinMsg, error: null, data: { roomId: targetRoom.id, roomName: targetRoom.name } };
  }

  if (cmd === 'leave') {
    if (!currentRoomId) {
      return { success: false, output: '', error: 'You are not in any room', data: null };
    }
    const room = RoomManager.getRoomById(currentRoomId);
    if (!room) {
      return { success: false, output: '', error: 'Current room not found', data: null };
    }
    RoomManager.removeMemberFromRoom(currentRoomId, currentUserId);
    UserManager.removeUserFromRoom(currentUserId, currentRoomId);
    return { success: true, output: YELLOW + 'Left #' + room.name + RESET, error: null, data: { leftRoomId: currentRoomId } };
  }

  if (cmd === 'list' || cmd === 'rooms') {
    const publicRooms = RoomManager.getPublicRooms();
    if (publicRooms.length === 0) {
      return { success: true, output: DIM + 'No public rooms available.' + RESET, error: null, data: null };
    }
    let output = BOLD + BLUE + '┌─ Available Rooms ─────────────────────────────┐' + RESET + '\n';
    for (const r of publicRooms) {
      const memberCount = r.memberIds.length;
      const isCurrentRoom = r.id === currentRoomId;
      const marker = isCurrentRoom ? GREEN + '▶ ' + RESET : '  ';
      const nameColor = isCurrentRoom ? GREEN + BOLD : CYAN;
      output += marker + nameColor + '#' + r.name.padEnd(20) + RESET;
      output += WHITE + ' [' + memberCount + ' members]' + RESET;
      if (r.description) {
        output += DIM + ' - ' + r.description.slice(0, 35) + (r.description.length > 35 ? '...' : '') + RESET;
      }
      output += '\n';
    }
    output += BOLD + BLUE + '└───────────────────────────────────────────────┘' + RESET;
    return { success: true, output, error: null, data: { rooms: publicRooms } };
  }

  if (cmd === 'users' || cmd === 'who') {
    const targetRoomId = args[0] ? (RoomManager.getRoomByName(args[0])?.id || null) : currentRoomId;
    if (!targetRoomId) {
      return { success: false, output: '', error: 'No room specified and not currently in a room', data: null };
    }
    const usersInRoom = UserManager.getUsersInRoom(targetRoomId);
    const room = RoomManager.getRoomById(targetRoomId);
    if (usersInRoom.length === 0) {
      return { success: true, output: DIM + 'No users in this room.' + RESET, error: null, data: null };
    }
    let output = BOLD + '── Users in #' + (room?.name || targetRoomId) + ' ──' + RESET + '\n';
    for (const u of usersInRoom) {
      const statusColor = u.status === 'online' ? GREEN : u.status === 'away' ? YELLOW : DIM;
      const statusIcon = u.status === 'online' ? '●' : u.status === 'away' ? '◐' : '○';
      output += statusColor + statusIcon + RESET + ' ' + u.color + u.displayName + RESET;
      output += DIM + ' (' + u.username + ')' + RESET + '\n';
    }
    return { success: true, output, error: null, data: { users: usersInRoom } };
  }

  if (cmd === 'msg' || cmd === 'pm' || cmd === 'dm') {
    if (args.length < 2) {
      return { success: false, output: '', error: 'Usage: /msg <username> <message>', data: null };
    }
    const targetUsername = args[0];
    const messageContent = args.slice(1).join(' ');
    if (!messageContent.trim()) {
      return { success: false, output: '', error: 'Message cannot be empty', data: null };
    }
    const targetUser = UserManager.getUserByUsername(targetUsername);
    if (!targetUser) {
      return { success: false, output: '', error: 'User "' + targetUsername + '" not found', data: null };
    }
    if (targetUser.id === currentUserId) {
      return { success: false, output: '', error: 'You cannot message yourself', data: null };
    }
    const sender = UserManager.getUserById(currentUserId);
    if (!sender) {
      return { success: false, output: '', error: 'Sender not found', data: null };
    }
    const msg = MessageStore.storeMessage('', currentUserId, sender.displayName, messageContent, true, targetUser.id);
    UserManager.incrementMessageCount(currentUserId);
    const output = MAGENTA + BOLD + '→ PM to ' + targetUser.displayName + RESET + MAGENTA + ': ' + messageContent + RESET;
    return { success: true, output, error: null, data: { message: msg, recipientId: targetUser.id } };
  }

  if (cmd === 'history') {
    if (!currentRoomId && args.length === 0) {
      return { success: false, output: '', error: 'Not in a room. Use /history <room-name>', data: null };
    }
    const targetRoomId = args[0]
      ? (RoomManager.getRoomByName(args[0])?.id || null)
      : currentRoomId;
    if (!targetRoomId) {
      return { success: false, output: '', error: 'Room not found', data: null };
    }
    const room = RoomManager.getRoomById(targetRoomId);
    const limit = args[1] ? parseInt(args[1], 10) : maxHistoryLines;
    const safeLimit = isNaN(limit) || limit <= 0 ? maxHistoryLines : Math.min(limit, 200);
    const messages = MessageStore.getMessagesForRoom(targetRoomId, safeLimit, 0);
    if (messages.length === 0) {
      return { success: true, output: DIM + 'No messages in #' + (room?.name || targetRoomId) + RESET, error: null, data: null };
    }
    let output = BOLD + BLUE + '── History: #' + (room?.name || targetRoomId) + ' (last ' + messages.length + ') ──' + RESET + '\n';
    for (const msg of messages) {
      output += formatMessageLine(msg, false, verboseMode);
      output += '\n';
    }
    return { success: true, output, error: null, data: { messages } };
  }

  if (cmd === 'search') {
    if (args.length === 0) {
      return { success: false, output: '', error: 'Usage: /search <query> [room-name]', data: null };
    }
    const query = args[0];
    const searchRoomName = args[1] || null;
    const searchRoomId = searchRoomName ? (RoomManager.getRoomByName(searchRoomName)?.id || null) : null;
    const roomNames = buildRoomNamesMap();
    const results = MessageStore.searchMessages(query, searchRoomId, null, null, null, searchResultLimit, roomNames);
    if (results.length === 0) {
      return { success: true, output: DIM + 'No results for "' + query + '"' + RESET, error: null, data: null };
    }
    let output = BOLD + YELLOW + '── Search: "' + query + '" (' + results.length + ' results) ──' + RESET + '\n';
    for (const r of results) {
      const highlighted = highlightMatch(r.message.content, r.matchIndex, r.matchLength);
      const ts = formatTimestamp(r.message.timestamp);
      output += DIM + ts + RESET + ' ' + CYAN + '#' + r.roomName + RESET + ' ';
      output += r.message.senderName + ': ' + highlighted + '\n';
    }
    return { success: true, output, error: null, data: { results } };
  }

  if (cmd === 'me' || cmd === 'profile') {
    const user = UserManager.getUserById(currentUserId);
    if (!user) {
      return { success: false, output: '', error: 'User not found', data: null };
    }
    const stats = MessageStore.getUserStats(currentUserId);
    const currentRoom = currentRoomId ? RoomManager.getRoomById(currentRoomId) : null;
    let output = BOLD + CYAN + '── Your Profile ──' + RESET + '\n';
    output += '  Username:     ' + user.username + '\n';
    output += '  Display name: ' + user.color + user.displayName + RESET + '\n';
    output += '  Status:       ' + (user.status === 'online' ? GREEN : YELLOW) + user.status + RESET + '\n';
    output += '  Current room: ' + (currentRoom ? CYAN + '#' + currentRoom.name + RESET : DIM + 'none' + RESET) + '\n';
    output += '  Rooms joined: ' + user.rooms.length + '\n';
    output += '  Messages:     ' + stats.total + ' total, ' + stats.today + ' today\n';
    output += '  Member since: ' + formatTimestamp(user.joinedAt) + '\n';
    return { success: true, output, error: null, data: { user, stats } };
  }

  if (cmd === 'nick' || cmd === 'rename') {
    if (args.length === 0) {
      return { success: false, output: '', error: 'Usage: /nick <new-display-name>', data: null };
    }
    const newName = args.join(' ');
    const updated = UserManager.updateDisplayName(currentUserId, newName);
    if (!updated) {
      return { success: false, output: '', error: 'Invalid display name (1-30 chars)', data: null };
    }
    return { success: true, output: GREEN + 'Display name updated to: ' + newName + RESET, error: null, data: { newName } };
  }

  if (cmd === 'topic') {
    if (!currentRoomId) {
      return { success: false, output: '', error: 'Not in a room', data: null };
    }
    if (args.length === 0) {
      const room = RoomManager.getRoomById(currentRoomId);
      const topic = room?.topic || '(no topic set)';
      return { success: true, output: YELLOW + 'Topic: ' + topic + RESET, error: null, data: { topic } };
    }
    const newTopic = args.join(' ');
    const room = RoomManager.getRoomById(currentRoomId);
    if (room && room.createdBy !== currentUserId && !isAdmin) {
      return { success: false, output: '', error: 'Only the room creator or admin can set the topic', data: null };
    }
    RoomManager.updateRoomTopic(currentRoomId, newTopic);
    return { success: true, output: YELLOW + 'Topic set to: ' + newTopic + RESET, error: null, data: { topic: newTopic } };
  }

  if (cmd === 'away') {
    const status = args[0] === 'off' ? 'online' : 'away';
    UserManager.updateUserStatus(currentUserId, status);
    const msg = status === 'away' ? YELLOW + 'You are now away' + RESET : GREEN + 'You are back online' + RESET;
    return { success: true, output: msg, error: null, data: { status } };
  }

  if (cmd === 'stats') {
    if (args[0] === 'room' || (!args[0] && currentRoomId)) {
      const rid = args[1] ? (RoomManager.getRoomByName(args[1])?.id || currentRoomId) : currentRoomId;
      if (!rid) return { success: false, output: '', error: 'No room specified', data: null };
      const room = RoomManager.getRoomById(rid);
      const roomStats = MessageStore.getRoomStats(rid);
      let output = BOLD + '── Room Stats: #' + (room?.name || rid) + ' ──' + RESET + '\n';
      output += '  Total messages:   ' + roomStats.total + '\n';
      output += '  Today:            ' + roomStats.today + '\n';
      output += '  This week:        ' + roomStats.thisWeek + '\n';
      output += '  Unique senders:   ' + roomStats.uniqueSenders + '\n';
      output += '  Current members:  ' + (room?.memberIds.length || 0) + '\n';
      return { success: true, output, error: null, data: roomStats };
    }
    const userStats = MessageStore.getUserStats(currentUserId);
    let output = BOLD + '── Your Stats ──' + RESET + '\n';
    output += '  Total messages: ' + userStats.total + '\n';
    output += '  Today:          ' + userStats.today + '\n';
    output += '  This week:      ' + userStats.thisWeek + '\n';
    output += '  Rooms active:   ' + userStats.roomCount + '\n';
    return { success: true, output, error: null, data: userStats };
  }

  if (cmd === 'ban' && isAdmin) {
    if (args.length < 2) {
      return { success: false, output: '', error: 'Usage: /ban <username> <room-name>', data: null };
    }
    const targetUser = UserManager.getUserByUsername(args[0]);
    const targetRoom = RoomManager.getRoomByName(args[1]);
    if (!targetUser) return { success: false, output: '', error: 'User not found', data: null };
    if (!targetRoom) return { success: false, output: '', error: 'Room not found', data: null };
    RoomManager.banUserFromRoom(targetRoom.id, targetUser.id);
    return { success: true, output: RED + 'Banned ' + targetUser.username + ' from #' + targetRoom.name + RESET, error: null, data: null };
  }

  if (cmd === 'create' && isAdmin) {
    if (args.length < 1) {
      return { success: false, output: '', error: 'Usage: /create <room-name> [description]', data: null };
    }
    const newRoomName = args[0];
    const newRoomDesc = args.slice(1).join(' ') || 'No description';
    const newRoom = RoomManager.createRoom(newRoomName, newRoomDesc, currentUserId, false, 100);
    if (!newRoom) {
      return { success: false, output: '', error: 'Failed to create room "' + newRoomName + '"', data: null };
    }
    return { success: true, output: GREEN + 'Created room #' + newRoom.name + RESET, error: null, data: { room: newRoom } };
  }

  return {
    success: false,
    output: '',
    error: 'Unknown command: /' + cmd + '. Type /help for available commands.',
    data: null,
  };
}

function buildHelpText(isAdmin: boolean, _verbose: boolean): string {
  let help = BOLD + CYAN + '┌─ ChatApp Commands ────────────────────────────────────┐' + RESET + '\n';
  help += BOLD + '  Navigation' + RESET + '\n';
  help += '  ' + GREEN + '/join <room>' + RESET + '          Join or create a room\n';
  help += '  ' + GREEN + '/leave' + RESET + '                Leave the current room\n';
  help += '  ' + GREEN + '/list' + RESET + '                 List all public rooms\n';
  help += '  ' + GREEN + '/rooms' + RESET + '                Same as /list\n';
  help += BOLD + '  Messaging' + RESET + '\n';
  help += '  ' + GREEN + '/msg <user> <text>' + RESET + '    Send a private message\n';
  help += '  ' + GREEN + '/pm <user> <text>' + RESET + '     Same as /msg\n';
  help += '  ' + GREEN + '/history [room] [n]' + RESET + '   Show message history\n';
  help += '  ' + GREEN + '/search <query> [room]' + RESET + ' Search messages\n';
  help += BOLD + '  Users' + RESET + '\n';
  help += '  ' + GREEN + '/users [room]' + RESET + '         List users in a room\n';
  help += '  ' + GREEN + '/who [room]' + RESET + '           Same as /users\n';
  help += '  ' + GREEN + '/nick <name>' + RESET + '          Change your display name\n';
  help += '  ' + GREEN + '/away [off]' + RESET + '           Toggle away status\n';
  help += '  ' + GREEN + '/me' + RESET + '                   View your profile\n';
  help += '  ' + GREEN + '/stats [room <name>]' + RESET + '  View statistics\n';
  help += BOLD + '  Room' + RESET + '\n';
  help += '  ' + GREEN + '/topic [new-topic]' + RESET + '    View or set room topic\n';
  if (isAdmin) {
    help += BOLD + RED + '  Admin' + RESET + '\n';
    help += '  ' + RED + '/ban <user> <room>' + RESET + '    Ban a user from a room\n';
    help += '  ' + RED + '/create <room> [desc]' + RESET + ' Create a new room\n';
  }
  help += BOLD + '  General' + RESET + '\n';
  help += '  ' + GREEN + '/help' + RESET + '                 Show this help\n';
  help += '  ' + GREEN + '/quit' + RESET + '                 Exit the application\n';
  help += BOLD + CYAN + '└───────────────────────────────────────────────────────┘' + RESET;
  return help;
}

export function formatMessageLine(msg: ChatMessage, showRoom: boolean, verbose: boolean): string {
  const ts = formatTimestamp(msg.timestamp);
  const user = UserManager.getUserById(msg.senderId);
  const nameColor = user?.color || WHITE;
  const displayName = user?.displayName || msg.senderName;

  let line = '';
  if (verbose) {
    line += DIM + '[' + msg.id + '] ' + RESET;
  }
  line += DIM + ts + RESET + ' ';
  if (showRoom) {
    const room = RoomManager.getRoomById(msg.roomId);
    line += CYAN + '#' + (room?.name || msg.roomId) + RESET + ' ';
  }
  if (msg.isPrivate) {
    line += MAGENTA + BOLD + '[PM] ' + RESET;
  }
  line += nameColor + BOLD + displayName + RESET + ': ';
  if (msg.deleted) {
    line += DIM + RED + '[deleted]' + RESET;
  } else {
    line += msg.content;
    if (msg.edited) {
      line += DIM + ' (edited)' + RESET;
    }
  }
  return line;
}

function highlightMatch(content: string, matchIndex: number, matchLength: number): string {
  if (matchIndex < 0 || matchLength <= 0) return content;
  const before = content.slice(0, matchIndex);
  const match = content.slice(matchIndex, matchIndex + matchLength);
  const after = content.slice(matchIndex + matchLength);
  return before + BG_BLUE + WHITE + BOLD + match + RESET + after;
}

function formatTimestamp(date: Date): string {
  const h = date.getHours().toString().padStart(2, '0');
  const m = date.getMinutes().toString().padStart(2, '0');
  const s = date.getSeconds().toString().padStart(2, '0');
  return h + ':' + m + ':' + s;
}

function buildRoomNamesMap(): Map<string, string> {
  const map = new Map<string, string>();
  for (const room of RoomManager.getAllRooms()) {
    map.set(room.id, room.name);
  }
  return map;
}

export function buildRoomHeader(room: ChatRoom, memberCount: number): string {
  let header = '\n' + BOLD + BG_BLUE + WHITE;
  header += ' #' + room.name.padEnd(18);
  header += ' │ ' + (room.description || 'No description').slice(0, 30).padEnd(30);
  header += ' │ ' + memberCount + ' members ';
  header += RESET + '\n';
  if (room.topic) {
    header += YELLOW + '  Topic: ' + room.topic + RESET + '\n';
  }
  return header;
}

export function buildStatusBar(username: string, roomName: string | null, userCount: number, msgCount: number): string {
  const roomPart = roomName ? CYAN + '#' + roomName + RESET : DIM + 'no room' + RESET;
  const bar = DIM + '─── ' + RESET + GREEN + username + RESET + DIM + ' @ ' + RESET + roomPart
    + DIM + ' | users: ' + userCount + ' | msgs: ' + msgCount + ' ───' + RESET;
  return bar;
}

export function buildWelcomeBanner(username: string, displayName: string): string {
  let banner = '\n';
  banner += BOLD + CYAN + '╔══════════════════════════════════════════════════╗' + RESET + '\n';
  banner += BOLD + CYAN + '║' + RESET + BOLD + '          Welcome to TypeScript Chat!             ' + BOLD + CYAN + '║' + RESET + '\n';
  banner += BOLD + CYAN + '╠══════════════════════════════════════════════════╣' + RESET + '\n';
  banner += BOLD + CYAN + '║' + RESET + '  Logged in as: ' + GREEN + displayName.padEnd(33) + RESET + BOLD + CYAN + '║' + RESET + '\n';
  banner += BOLD + CYAN + '║' + RESET + '  Username:     ' + WHITE + username.padEnd(33) + RESET + BOLD + CYAN + '║' + RESET + '\n';
  banner += BOLD + CYAN + '╠══════════════════════════════════════════════════╣' + RESET + '\n';
  banner += BOLD + CYAN + '║' + RESET + DIM + '  Type /help for available commands               ' + RESET + BOLD + CYAN + '║' + RESET + '\n';
  banner += BOLD + CYAN + '║' + RESET + DIM + '  Type /list to see available rooms               ' + RESET + BOLD + CYAN + '║' + RESET + '\n';
  banner += BOLD + CYAN + '║' + RESET + DIM + '  Type /join <room> to enter a chat room          ' + RESET + BOLD + CYAN + '║' + RESET + '\n';
  banner += BOLD + CYAN + '╚══════════════════════════════════════════════════╝' + RESET + '\n';
  return banner;
}

export function renderError(errorMsg: string): string {
  return RED + BOLD + '✗ Error: ' + RESET + RED + errorMsg + RESET;
}

export function renderSuccess(msg: string): string {
  return GREEN + '✓ ' + msg + RESET;
}

export function renderInfo(msg: string): string {
  return CYAN + 'ℹ ' + msg + RESET;
}

export function renderWarning(msg: string): string {
  return YELLOW + '⚠ ' + msg + RESET;
}
