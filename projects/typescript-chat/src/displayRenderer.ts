import { ChatMessage, ChatRoom, User, SearchResult } from './types';
import * as UserManager from './userManager';
import * as RoomManager from './roomManager';

// Raw ANSI codes - duplicated from commandProcessor intentionally
const RESET = '\x1b[0m';
const BOLD = '\x1b[1m';
const DIM = '\x1b[2m';
const ITALIC = '\x1b[3m';
const UNDERLINE = '\x1b[4m';
const RED = '\x1b[31m';
const GREEN = '\x1b[32m';
const YELLOW = '\x1b[33m';
const BLUE = '\x1b[34m';
const MAGENTA = '\x1b[35m';
const CYAN = '\x1b[36m';
const WHITE = '\x1b[37m';
const BRIGHT_RED = '\x1b[91m';
const BRIGHT_GREEN = '\x1b[92m';
const BRIGHT_YELLOW = '\x1b[93m';
const BRIGHT_CYAN = '\x1b[96m';
const BG_BLUE = '\x1b[44m';
const BG_GREEN = '\x1b[42m';
const BG_RED = '\x1b[41m';
const BG_BLACK = '\x1b[40m';

let terminalWidth = process.stdout.columns || 80;

process.stdout.on('resize', () => {
  terminalWidth = process.stdout.columns || 80;
});

function padRight(str: string, width: number): string {
  const visibleLen = stripAnsi(str).length;
  if (visibleLen >= width) return str;
  return str + ' '.repeat(width - visibleLen);
}

function stripAnsi(str: string): string {
  // eslint-disable-next-line no-control-regex
  return str.replace(/\x1b\[[0-9;]*m/g, '');
}

function truncate(str: string, maxLen: number): string {
  if (str.length <= maxLen) return str;
  return str.slice(0, maxLen - 3) + '...';
}

export function renderRoomList(
  rooms: ChatRoom[],
  currentRoomId: string | null,
  currentUserId: string,
  showMemberCounts: boolean,
  showDescriptions: boolean,
  maxWidth: number
): string {
  if (rooms.length === 0) {
    return DIM + '  (no rooms available)' + RESET + '\n';
  }

  const width = Math.min(maxWidth, terminalWidth);
  let output = '';

  // Header
  const headerTitle = ' Available Rooms ';
  const headerPad = Math.max(0, width - headerTitle.length - 4);
  const leftPad = Math.floor(headerPad / 2);
  const rightPad = headerPad - leftPad;
  output += BOLD + BLUE + '┌' + '─'.repeat(leftPad) + headerTitle + '─'.repeat(rightPad) + '┐' + RESET + '\n';

  for (const room of rooms) {
    const isCurrentRoom = room.id === currentRoomId;
    const isMember = room.memberIds.includes(currentUserId);
    const memberCount = room.memberIds.length;

    let line = BOLD + BLUE + '│' + RESET;

    if (isCurrentRoom) {
      line += GREEN + ' ▶ ' + RESET;
    } else if (isMember) {
      line += CYAN + ' ◦ ' + RESET;
    } else {
      line += '   ';
    }

    const nameWidth = 20;
    const nameDisplay = isCurrentRoom
      ? GREEN + BOLD + ('#' + room.name).padEnd(nameWidth) + RESET
      : isMember
      ? CYAN + ('#' + room.name).padEnd(nameWidth) + RESET
      : WHITE + ('#' + room.name).padEnd(nameWidth) + RESET;

    line += nameDisplay;

    if (showMemberCounts) {
      const memberStr = '[' + memberCount + ']';
      line += DIM + ' ' + memberStr.padEnd(6) + RESET;
    }

    if (showDescriptions && room.description) {
      const maxDescWidth = width - 35;
      const desc = truncate(room.description, maxDescWidth);
      line += DIM + ' - ' + desc + RESET;
    }

    if (room.topic) {
      line += YELLOW + ' │ ' + truncate(room.topic, 20) + RESET;
    }

    // pad to full width
    const visLen = stripAnsi(line).length;
    const remaining = width - visLen - 1;
    if (remaining > 0) {
      line += ' '.repeat(remaining);
    }
    line += BOLD + BLUE + '│' + RESET;
    output += line + '\n';
  }

  output += BOLD + BLUE + '└' + '─'.repeat(width - 2) + '┘' + RESET + '\n';
  return output;
}

export function renderUserList(
  users: User[],
  currentUserId: string,
  roomName: string,
  showStats: boolean,
  compact: boolean
): string {
  if (users.length === 0) {
    return DIM + '  (no users online)' + RESET + '\n';
  }

  let output = '';
  if (!compact) {
    output += BOLD + '── Users in #' + roomName + ' (' + users.length + ') ──' + RESET + '\n';
  }

  for (const user of users) {
    const isCurrentUser = user.id === currentUserId;
    let statusIcon: string;
    let statusColor: string;

    if (user.status === 'online') {
      statusIcon = '●';
      statusColor = GREEN;
    } else if (user.status === 'away') {
      statusIcon = '◐';
      statusColor = YELLOW;
    } else {
      statusIcon = '○';
      statusColor = DIM;
    }

    let line = '  ' + statusColor + statusIcon + RESET + ' ';
    line += user.color + user.displayName + RESET;

    if (isCurrentUser) {
      line += DIM + ' (you)' + RESET;
    }

    if (!compact) {
      line += DIM + ' @' + user.username + RESET;
    }

    if (showStats) {
      line += DIM + ' | msgs: ' + user.messageCount + RESET;
    }

    output += line + '\n';
  }

  return output;
}

export function renderMessageHistory(
  messages: ChatMessage[],
  roomName: string,
  showTimestamps: boolean,
  showIds: boolean,
  groupByDate: boolean,
  highlightUserId: string | null
): string {
  if (messages.length === 0) {
    return DIM + '  (no messages)' + RESET + '\n';
  }

  let output = '';
  if (groupByDate) {
    output += BOLD + BLUE + '── History: #' + roomName + ' ──' + RESET + '\n';
  }

  let lastDate = '';

  for (const msg of messages) {
    const msgDate = msg.timestamp.toDateString();

    if (groupByDate && msgDate !== lastDate) {
      lastDate = msgDate;
      const dateLabel = formatDateLabel(msg.timestamp);
      const pad = Math.max(0, Math.floor((terminalWidth - dateLabel.length - 4) / 2));
      output += DIM + '─'.repeat(pad) + ' ' + dateLabel + ' ' + '─'.repeat(pad) + RESET + '\n';
    }

    let line = '';

    if (showIds) {
      line += DIM + '[' + msg.id + '] ' + RESET;
    }

    if (showTimestamps) {
      line += DIM + formatTime(msg.timestamp) + RESET + ' ';
    }

    if (msg.isPrivate) {
      line += MAGENTA + BOLD + '[PM] ' + RESET;
    }

    const user = UserManager.getUserById(msg.senderId);
    const nameColor = user?.color || WHITE;
    const isHighlighted = highlightUserId && msg.senderId === highlightUserId;

    if (isHighlighted) {
      line += BG_BLACK + nameColor + BOLD + (user?.displayName || msg.senderName) + RESET + ': ';
    } else {
      line += nameColor + BOLD + (user?.displayName || msg.senderName) + RESET + ': ';
    }

    if (msg.deleted) {
      line += DIM + RED + '[message deleted]' + RESET;
    } else {
      line += msg.content;
      if (msg.edited) {
        line += DIM + ITALIC + ' (edited)' + RESET;
      }
    }

    output += line + '\n';
  }

  return output;
}

export function renderSearchResults(
  results: SearchResult[],
  query: string,
  showContext: boolean,
  maxPerRoom: number
): string {
  if (results.length === 0) {
    return DIM + 'No results found for "' + query + '"' + RESET + '\n';
  }

  let output = BOLD + YELLOW + '── Search Results: "' + query + '" ──' + RESET + '\n';
  output += DIM + '  Found ' + results.length + ' match' + (results.length === 1 ? '' : 'es') + RESET + '\n\n';

  const byRoom = new Map<string, SearchResult[]>();
  for (const r of results) {
    if (!byRoom.has(r.roomName)) {
      byRoom.set(r.roomName, []);
    }
    byRoom.get(r.roomName)!.push(r);
  }

  for (const [roomName, roomResults] of byRoom.entries()) {
    output += BOLD + CYAN + '  #' + roomName + RESET + DIM + ' (' + roomResults.length + ' match' + (roomResults.length === 1 ? '' : 'es') + ')' + RESET + '\n';

    const displayResults = maxPerRoom > 0 ? roomResults.slice(0, maxPerRoom) : roomResults;
    for (const r of displayResults) {
      const ts = formatTime(r.message.timestamp);
      const highlighted = highlightSearchMatch(r.message.content, r.matchIndex, r.matchLength);
      output += '    ' + DIM + ts + RESET + ' ' + BOLD + r.senderName + RESET + ': ' + highlighted + '\n';

      if (showContext) {
        output += '\n';
      }
    }

    if (maxPerRoom > 0 && roomResults.length > maxPerRoom) {
      output += DIM + '    ... and ' + (roomResults.length - maxPerRoom) + ' more in this room' + RESET + '\n';
    }

    output += '\n';
  }

  return output;
}

export function renderPrivateMessageThread(
  messages: ChatMessage[],
  currentUserId: string,
  otherUsername: string,
  showTimestamps: boolean
): string {
  if (messages.length === 0) {
    return DIM + '  No messages with ' + otherUsername + RESET + '\n';
  }

  let output = BOLD + MAGENTA + '── Private: @' + otherUsername + ' ──' + RESET + '\n';

  for (const msg of messages) {
    const isSentByMe = msg.senderId === currentUserId;
    let line = '';

    if (showTimestamps) {
      line += DIM + formatTime(msg.timestamp) + RESET + ' ';
    }

    if (isSentByMe) {
      line += GREEN + BOLD + '→ You' + RESET + ': ' + msg.content;
    } else {
      line += MAGENTA + BOLD + '← ' + msg.senderName + RESET + ': ' + msg.content;
    }

    if (msg.edited) {
      line += DIM + ' (edited)' + RESET;
    }

    output += line + '\n';
  }

  return output;
}

export function renderSystemMessage(message: string, level: 'info' | 'warn' | 'error' | 'success'): string {
  let icon: string;
  let color: string;

  if (level === 'info') {
    icon = 'ℹ';
    color = CYAN;
  } else if (level === 'warn') {
    icon = '⚠';
    color = YELLOW;
  } else if (level === 'error') {
    icon = '✗';
    color = RED;
  } else {
    icon = '✓';
    color = GREEN;
  }

  return color + BOLD + icon + RESET + ' ' + color + message + RESET;
}

export function renderRoomHeader(
  room: ChatRoom,
  memberCount: number,
  msgCount: number,
  showFullInfo: boolean
): string {
  const width = Math.min(terminalWidth, 80);
  let output = '\n';

  const title = ' #' + room.name + ' ';
  const leftBorder = '╔' + '═'.repeat(Math.floor((width - title.length) / 2));
  const rightBorder = '═'.repeat(width - leftBorder.length - title.length - 1) + '╗';

  output += BOLD + CYAN + leftBorder + title + rightBorder + RESET + '\n';

  if (showFullInfo) {
    const descLine = '  ' + (room.description || 'No description');
    const memberLine = '  Members: ' + memberCount + '  Messages: ' + msgCount;
    output += BOLD + CYAN + '║' + RESET + padRight(descLine, width - 2) + BOLD + CYAN + '║' + RESET + '\n';
    output += BOLD + CYAN + '║' + RESET + padRight(memberLine, width - 2) + BOLD + CYAN + '║' + RESET + '\n';
  }

  if (room.topic) {
    const topicLine = '  Topic: ' + room.topic;
    output += BOLD + CYAN + '║' + RESET + YELLOW + padRight(topicLine, width - 2) + RESET + BOLD + CYAN + '║' + RESET + '\n';
  }

  output += BOLD + CYAN + '╚' + '═'.repeat(width - 2) + '╝' + RESET + '\n';
  return output;
}

export function renderStatusBar(
  username: string,
  displayName: string,
  roomName: string | null,
  status: string,
  msgCount: number
): string {
  const roomPart = roomName ? CYAN + '#' + roomName + RESET : DIM + 'no room' + RESET;
  const statusColor = status === 'online' ? GREEN : status === 'away' ? YELLOW : DIM;
  const statusDot = statusColor + '●' + RESET;

  return DIM + '─' + RESET + ' ' + statusDot + ' ' + GREEN + displayName + RESET
    + DIM + ' (' + username + ')' + RESET
    + DIM + ' in ' + RESET + roomPart
    + DIM + ' | ' + msgCount + ' msgs' + RESET
    + DIM + ' ─' + RESET;
}

export function renderWelcome(username: string, displayName: string, roomCount: number): string {
  let output = '\n';
  output += BOLD + BRIGHT_CYAN + '╔══════════════════════════════════════════════════╗' + RESET + '\n';
  output += BOLD + BRIGHT_CYAN + '║' + RESET + BOLD + '     Welcome to TypeScript Chat v1.0.0            ' + BOLD + BRIGHT_CYAN + '║' + RESET + '\n';
  output += BOLD + BRIGHT_CYAN + '╠══════════════════════════════════════════════════╣' + RESET + '\n';
  output += BOLD + BRIGHT_CYAN + '║' + RESET + '  Hello, ' + GREEN + displayName.padEnd(40) + RESET + BOLD + BRIGHT_CYAN + '║' + RESET + '\n';
  output += BOLD + BRIGHT_CYAN + '║' + RESET + '  Username: ' + WHITE + username.padEnd(37) + RESET + BOLD + BRIGHT_CYAN + '║' + RESET + '\n';
  output += BOLD + BRIGHT_CYAN + '║' + RESET + '  Rooms available: ' + YELLOW + roomCount.toString().padEnd(30) + RESET + BOLD + BRIGHT_CYAN + '║' + RESET + '\n';
  output += BOLD + BRIGHT_CYAN + '╠══════════════════════════════════════════════════╣' + RESET + '\n';
  output += BOLD + BRIGHT_CYAN + '║' + RESET + DIM + '  /help for commands  │  /list for rooms          ' + RESET + BOLD + BRIGHT_CYAN + '║' + RESET + '\n';
  output += BOLD + BRIGHT_CYAN + '╚══════════════════════════════════════════════════╝' + RESET + '\n';
  return output;
}

export function renderPrompt(displayName: string, roomName: string | null): string {
  const room = roomName ? CYAN + '#' + roomName + RESET + ' ' : '';
  return GREEN + displayName + RESET + ' ' + room + DIM + '>' + RESET + ' ';
}

function highlightSearchMatch(content: string, matchIndex: number, matchLength: number): string {
  if (matchIndex < 0 || matchLength <= 0) return content;
  const before = content.slice(0, matchIndex);
  const match = content.slice(matchIndex, matchIndex + matchLength);
  const after = content.slice(matchIndex + matchLength);
  return before + BG_BLUE + WHITE + BOLD + match + RESET + after;
}

function formatTime(date: Date): string {
  const h = date.getHours().toString().padStart(2, '0');
  const m = date.getMinutes().toString().padStart(2, '0');
  const s = date.getSeconds().toString().padStart(2, '0');
  return h + ':' + m + ':' + s;
}

function formatDateLabel(date: Date): string {
  const today = new Date();
  const yesterday = new Date(today.getTime() - 24 * 60 * 60 * 1000);
  if (date.toDateString() === today.toDateString()) return 'Today';
  if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return months[date.getMonth()] + ' ' + date.getDate() + ', ' + date.getFullYear();
}

// Mega render function that does everything in one place - intentionally huge and complex
export function renderFullScreenView(
  viewType: 'room' | 'userlist' | 'search' | 'history' | 'profile' | 'stats' | 'help',
  currentUserId: string,
  currentRoomId: string | null,
  messages: ChatMessage[],
  extraData: Record<string, unknown>,
  showBorders: boolean,
  showTimestamps: boolean,
  showIds: boolean,
  compact: boolean,
  filterUserId: string | null,
  highlightText: string | null,
  maxLines: number,
  sortOrder: 'asc' | 'desc',
  groupByDate: boolean,
  showStats: boolean
): string {
  const width = Math.min(terminalWidth, 120);
  let output = '';
  const user = UserManager.getUserById(currentUserId);
  const room = currentRoomId ? RoomManager.getRoomById(currentRoomId) : null;

  if (viewType === 'room') {
    if (showBorders) {
      output += BOLD + CYAN + '┌' + '─'.repeat(width - 2) + '┐' + RESET + '\n';
    }
    if (room) {
      const roomLine = ' #' + room.name + (room.topic ? ' │ ' + room.topic : '');
      if (showBorders) {
        output += BOLD + CYAN + '│' + RESET + padRight(CYAN + roomLine + RESET, width - 2) + BOLD + CYAN + '│' + RESET + '\n';
        output += BOLD + CYAN + '├' + '─'.repeat(width - 2) + '┤' + RESET + '\n';
      } else {
        output += CYAN + roomLine + RESET + '\n';
      }
    }

    let displayMessages = [...messages];
    if (filterUserId) {
      displayMessages = displayMessages.filter(m => m.senderId === filterUserId);
    }
    if (sortOrder === 'desc') {
      displayMessages = displayMessages.reverse();
    }
    if (maxLines > 0 && displayMessages.length > maxLines) {
      displayMessages = displayMessages.slice(displayMessages.length - maxLines);
    }

    let lastDateStr = '';
    for (const msg of displayMessages) {
      if (msg.deleted) continue;

      if (groupByDate) {
        const dateStr = msg.timestamp.toDateString();
        if (dateStr !== lastDateStr) {
          lastDateStr = dateStr;
          const label = formatDateLabel(msg.timestamp);
          const pad = Math.max(0, Math.floor((width - label.length - 4) / 2));
          const dateLine = DIM + '─'.repeat(pad) + ' ' + label + ' ' + '─'.repeat(pad) + RESET;
          if (showBorders) {
            output += BOLD + CYAN + '│' + RESET + dateLine + BOLD + CYAN + '│' + RESET + '\n';
          } else {
            output += dateLine + '\n';
          }
        }
      }

      const msgUser = UserManager.getUserById(msg.senderId);
      const nameColor = msgUser?.color || WHITE;
      let line = '';

      if (showIds) {
        line += DIM + '[' + msg.id + '] ' + RESET;
      }
      if (showTimestamps) {
        line += DIM + formatTime(msg.timestamp) + RESET + ' ';
      }
      if (msg.isPrivate) {
        line += MAGENTA + BOLD + '[PM] ' + RESET;
      }

      const isHighlighted = filterUserId && msg.senderId === filterUserId;
      if (isHighlighted) {
        line += BG_BLACK + nameColor + BOLD + (msgUser?.displayName || msg.senderName) + RESET + ': ';
      } else {
        line += nameColor + BOLD + (msgUser?.displayName || msg.senderName) + RESET + ': ';
      }

      let content = msg.content;
      if (highlightText) {
        const idx = content.toLowerCase().indexOf(highlightText.toLowerCase());
        if (idx !== -1) {
          content = content.slice(0, idx) + BG_BLUE + WHITE + BOLD + content.slice(idx, idx + highlightText.length) + RESET + content.slice(idx + highlightText.length);
        }
      }
      line += content;

      if (msg.edited) {
        line += DIM + ' (edited)' + RESET;
      }

      if (showBorders) {
        output += BOLD + CYAN + '│' + RESET + ' ' + line + '\n';
      } else {
        output += line + '\n';
      }
    }

    if (showBorders) {
      if (showStats && room) {
        const memberCount = room.memberIds.length;
        const statsLine = DIM + ' ' + messages.length + ' messages │ ' + memberCount + ' members' + RESET;
        output += BOLD + CYAN + '├' + '─'.repeat(width - 2) + '┤' + RESET + '\n';
        output += BOLD + CYAN + '│' + RESET + padRight(statsLine, width - 2) + BOLD + CYAN + '│' + RESET + '\n';
      }
      output += BOLD + CYAN + '└' + '─'.repeat(width - 2) + '┘' + RESET + '\n';
    }
  } else if (viewType === 'userlist') {
    const usersInRoom = currentRoomId ? UserManager.getUsersInRoom(currentRoomId) : UserManager.getOnlineUsers();

    if (showBorders) {
      output += BOLD + '┌── Users' + (room ? ' in #' + room.name : '') + ' (' + usersInRoom.length + ') ' + '─'.repeat(Math.max(0, width - 20 - (room?.name.length || 0))) + '┐' + RESET + '\n';
    } else {
      output += BOLD + 'Users' + (room ? ' in #' + room.name : '') + ' (' + usersInRoom.length + ')' + RESET + '\n';
    }

    for (const u of usersInRoom) {
      const isCurrentUser = u.id === currentUserId;
      let statusIcon: string;
      let statusColor: string;
      if (u.status === 'online') {
        statusIcon = '●';
        statusColor = GREEN;
      } else if (u.status === 'away') {
        statusIcon = '◐';
        statusColor = YELLOW;
      } else {
        statusIcon = '○';
        statusColor = DIM;
      }
      let line = '  ' + statusColor + statusIcon + RESET + ' ' + u.color + u.displayName + RESET;
      if (isCurrentUser) {
        line += DIM + ' (you)' + RESET;
      }
      if (!compact) {
        line += DIM + ' @' + u.username + RESET;
      }
      if (showStats) {
        line += DIM + ' [' + u.messageCount + ' msgs]' + RESET;
      }
      if (showBorders) {
        output += BOLD + '│' + RESET + padRight(line, width - 2) + BOLD + '│' + RESET + '\n';
      } else {
        output += line + '\n';
      }
    }

    if (showBorders) {
      output += BOLD + '└' + '─'.repeat(width - 2) + '┘' + RESET + '\n';
    }
  } else if (viewType === 'profile') {
    if (!user) {
      return RED + 'User not found' + RESET + '\n';
    }
    if (showBorders) {
      output += BOLD + CYAN + '┌── Profile ─' + '─'.repeat(width - 14) + '┐' + RESET + '\n';
    }

    const fields: Array<[string, string]> = [
      ['Username', user.username],
      ['Display name', user.color + user.displayName + RESET],
      ['Status', (user.status === 'online' ? GREEN : YELLOW) + user.status + RESET],
      ['Current room', room ? CYAN + '#' + room.name + RESET : DIM + 'none' + RESET],
      ['Rooms joined', String(user.rooms.length)],
      ['Messages sent', String(user.messageCount)],
      ['Member since', formatTime(user.joinedAt)],
    ];

    for (const [label, value] of fields) {
      const line = '  ' + label.padEnd(16) + ': ' + value;
      if (showBorders) {
        output += BOLD + CYAN + '│' + RESET + padRight(line, width - 2) + BOLD + CYAN + '│' + RESET + '\n';
      } else {
        output += line + '\n';
      }
    }

    if (showBorders) {
      output += BOLD + CYAN + '└' + '─'.repeat(width - 2) + '┘' + RESET + '\n';
    }
  } else if (viewType === 'help') {
    const isAdmin = (extraData.isAdmin as boolean) || false;
    if (showBorders) {
      output += BOLD + CYAN + '┌── Help ─' + '─'.repeat(width - 11) + '┐' + RESET + '\n';
    }
    const helpLines = [
      ['Navigation', ''],
      ['/join <room>', 'Join or create a room'],
      ['/leave', 'Leave the current room'],
      ['/list', 'List all public rooms'],
      ['Messaging', ''],
      ['/msg <user> <text>', 'Send a private message'],
      ['/history [room] [n]', 'Show message history'],
      ['/search <query>', 'Search messages'],
      ['Users', ''],
      ['/users [room]', 'List users in a room'],
      ['/nick <name>', 'Change display name'],
      ['/away [off]', 'Toggle away status'],
      ['/me', 'View your profile'],
      ['Other', ''],
      ['/topic [text]', 'View or set room topic'],
      ['/stats', 'View statistics'],
      ['/help', 'Show this help'],
      ['/quit', 'Exit the application'],
    ];
    if (isAdmin) {
      helpLines.push(['Admin', '']);
      helpLines.push(['/ban <user> <room>', 'Ban a user from a room']);
      helpLines.push(['/create <room>', 'Create a new room']);
    }

    for (const [cmd, desc] of helpLines) {
      let line: string;
      if (!desc) {
        line = '\n  ' + BOLD + cmd + RESET;
      } else {
        line = '    ' + GREEN + cmd.padEnd(22) + RESET + DIM + desc + RESET;
      }
      if (showBorders) {
        output += BOLD + CYAN + '│' + RESET + padRight(line, width - 2) + BOLD + CYAN + '│' + RESET + '\n';
      } else {
        output += line + '\n';
      }
    }
    if (showBorders) {
      output += BOLD + CYAN + '└' + '─'.repeat(width - 2) + '┘' + RESET + '\n';
    }
  } else if (viewType === 'stats') {
    const statsData = extraData as { total: number; today: number; thisWeek: number; uniqueSenders?: number; roomCount?: number };
    if (showBorders) {
      output += BOLD + '┌── Statistics ─' + '─'.repeat(width - 17) + '┐' + RESET + '\n';
    }
    const statLines: Array<[string, string]> = [
      ['Total messages', String(statsData.total)],
      ['Today', String(statsData.today)],
      ['This week', String(statsData.thisWeek)],
    ];
    if (statsData.uniqueSenders !== undefined) {
      statLines.push(['Unique senders', String(statsData.uniqueSenders)]);
    }
    if (statsData.roomCount !== undefined) {
      statLines.push(['Active rooms', String(statsData.roomCount)]);
    }
    for (const [label, value] of statLines) {
      const line = '  ' + label.padEnd(18) + ': ' + YELLOW + value + RESET;
      if (showBorders) {
        output += BOLD + '│' + RESET + padRight(line, width - 2) + BOLD + '│' + RESET + '\n';
      } else {
        output += line + '\n';
      }
    }
    if (showBorders) {
      output += BOLD + '└' + '─'.repeat(width - 2) + '┘' + RESET + '\n';
    }
  } else {
    output += DIM + '(unknown view type: ' + viewType + ')' + RESET + '\n';
  }

  return output;
}

export function renderNotificationBanner(
  notifications: string[],
  userId: string,
  roomId: string | null,
  showCount: boolean,
  autoAcknowledge: boolean,
  maxDisplay: number,
  urgency: 'low' | 'normal' | 'high'
): string {
  if (notifications.length === 0) return '';

  let output = '';
  const color = urgency === 'high' ? RED : urgency === 'normal' ? YELLOW : DIM;
  const displayNotifs = maxDisplay > 0 ? notifications.slice(0, maxDisplay) : notifications;

  if (showCount && notifications.length > 1) {
    output += color + BOLD + '🔔 ' + notifications.length + ' notification' + (notifications.length > 1 ? 's' : '') + RESET + '\n';
  }

  for (const notif of displayNotifs) {
    output += color + '  › ' + RESET + notif + '\n';
  }

  if (maxDisplay > 0 && notifications.length > maxDisplay) {
    output += DIM + '  ... and ' + (notifications.length - maxDisplay) + ' more' + RESET + '\n';
  }

  return output;
}

export function renderInputHint(
  currentRoomId: string | null,
  currentUserId: string,
  inputMode: string,
  lastCommand: string | null,
  showSuggestions: boolean
): string {
  if (!showSuggestions) return '';
  let hint = DIM;

  if (inputMode === 'confirm') {
    hint += 'Type y/yes or n/no: ';
  } else if (!currentRoomId) {
    hint += 'Not in a room — try /join general or /list';
  } else if (!lastCommand) {
    hint += 'Type a message or /help for commands';
  } else if (lastCommand.startsWith('/join')) {
    hint += 'Joined a room! Type a message or /users to see who\'s here';
  } else if (lastCommand.startsWith('/search')) {
    hint += 'Search complete. Try /history for full history';
  } else {
    hint += 'Type a message or /help';
  }

  hint += RESET;
  return hint;
}
