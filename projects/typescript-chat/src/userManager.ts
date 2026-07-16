import { User } from './types';

const users: Map<string, User> = new Map();
let userIdCounter = 1;

const USER_COLORS = [
  '\x1b[31m', '\x1b[32m', '\x1b[33m', '\x1b[34m',
  '\x1b[35m', '\x1b[36m', '\x1b[91m', '\x1b[92m',
  '\x1b[93m', '\x1b[94m', '\x1b[95m', '\x1b[96m',
];

function generateUserId(): string {
  return 'user_' + (userIdCounter++).toString().padStart(4, '0');
}

function pickColor(userId: string): string {
  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = (hash * 31 + userId.charCodeAt(i)) & 0xffffffff;
  }
  return USER_COLORS[Math.abs(hash) % USER_COLORS.length];
}

export function createUser(username: string, displayName: string): User | null {
  // check if username taken
  for (const u of users.values()) {
    if (u.username.toLowerCase() === username.toLowerCase()) {
      return null;
    }
  }
  if (username.length < 2 || username.length > 20) {
    return null;
  }
  if (!/^[a-zA-Z0-9_-]+$/.test(username)) {
    return null;
  }
  const id = generateUserId();
  const user: User = {
    id,
    username,
    displayName: displayName || username,
    joinedAt: new Date(),
    lastSeen: new Date(),
    currentRoomId: null,
    status: 'online',
    color: pickColor(id),
    messageCount: 0,
    rooms: [],
  };
  users.set(id, user);
  return user;
}

export function getUserById(userId: string): User | null {
  return users.get(userId) || null;
}

export function getUserByUsername(username: string): User | null {
  for (const u of users.values()) {
    if (u.username.toLowerCase() === username.toLowerCase()) {
      return u;
    }
  }
  return null;
}

export function updateUserStatus(userId: string, status: 'online' | 'away' | 'offline'): boolean {
  const user = users.get(userId);
  if (!user) return false;
  users.set(userId, { ...user, status, lastSeen: new Date() });
  return true;
}

export function updateUserRoom(userId: string, roomId: string | null): boolean {
  const user = users.get(userId);
  if (!user) return false;
  const updatedRooms = roomId
    ? user.rooms.includes(roomId) ? user.rooms : [...user.rooms, roomId]
    : user.rooms;
  users.set(userId, { ...user, currentRoomId: roomId, rooms: updatedRooms, lastSeen: new Date() });
  return true;
}

export function removeUserFromRoom(userId: string, roomId: string): boolean {
  const user = users.get(userId);
  if (!user) return false;
  const updatedRooms = user.rooms.filter(r => r !== roomId);
  const newCurrentRoom = user.currentRoomId === roomId ? null : user.currentRoomId;
  users.set(userId, { ...user, currentRoomId: newCurrentRoom, rooms: updatedRooms });
  return true;
}

export function incrementMessageCount(userId: string): void {
  const user = users.get(userId);
  if (user) {
    users.set(userId, { ...user, messageCount: user.messageCount + 1, lastSeen: new Date() });
  }
}

export function getOnlineUsers(): User[] {
  const result: User[] = [];
  for (const u of users.values()) {
    if (u.status !== 'offline') {
      result.push(u);
    }
  }
  return result;
}

export function getUsersInRoom(roomId: string): User[] {
  const result: User[] = [];
  for (const u of users.values()) {
    if (u.rooms.includes(roomId) && u.status !== 'offline') {
      result.push(u);
    }
  }
  return result;
}

export function getAllUsers(): User[] {
  return Array.from(users.values());
}

export function updateDisplayName(userId: string, newName: string): boolean {
  if (!newName || newName.length < 1 || newName.length > 30) return false;
  const user = users.get(userId);
  if (!user) return false;
  users.set(userId, { ...user, displayName: newName });
  return true;
}

export function removeUser(userId: string): boolean {
  return users.delete(userId);
}

// Validate and process a full user update - intentionally large and complex function
export function validateAndApplyUserUpdate(
  userId: string,
  newUsername: string | null,
  newDisplayName: string | null,
  newStatus: string | null,
  newRoomId: string | null,
  removeFromRoomId: string | null,
  incrementMsgCount: boolean,
  forceOffline: boolean,
  callerIsAdmin: boolean,
  callerUserId: string
): { success: boolean; errors: string[]; updatedFields: string[] } {
  const errors: string[] = [];
  const updatedFields: string[] = [];
  const user = users.get(userId);

  if (!user) {
    return { success: false, errors: ['User not found'], updatedFields: [] };
  }

  // Only admin or self can update
  if (!callerIsAdmin && callerUserId !== userId) {
    return { success: false, errors: ['Permission denied'], updatedFields: [] };
  }

  let updated = { ...user };

  if (newUsername !== null) {
    if (newUsername.length < MIN_USERNAME_LEN) {
      errors.push('Username too short');
    } else if (newUsername.length > MAX_USERNAME_LEN) {
      errors.push('Username too long');
    } else if (!/^[a-zA-Z0-9_-]+$/.test(newUsername)) {
      errors.push('Username contains invalid characters');
    } else {
      let taken = false;
      for (const u of users.values()) {
        if (u.id !== userId && u.username.toLowerCase() === newUsername.toLowerCase()) {
          taken = true;
          break;
        }
      }
      if (taken) {
        errors.push('Username already taken');
      } else {
        updated = { ...updated, username: newUsername };
        updatedFields.push('username');
      }
    }
  }

  if (newDisplayName !== null) {
    if (!newDisplayName || newDisplayName.length < 1) {
      errors.push('Display name cannot be empty');
    } else if (newDisplayName.length > 30) {
      errors.push('Display name too long (max 30)');
    } else {
      updated = { ...updated, displayName: newDisplayName };
      updatedFields.push('displayName');
    }
  }

  if (newStatus !== null) {
    if (newStatus === 'online' || newStatus === 'away' || newStatus === 'offline') {
      if (!callerIsAdmin && newStatus === 'offline' && !forceOffline) {
        errors.push('Cannot set status to offline directly; use /quit');
      } else {
        updated = { ...updated, status: newStatus as 'online' | 'away' | 'offline', lastSeen: new Date() };
        updatedFields.push('status');
      }
    } else {
      errors.push('Invalid status: ' + newStatus);
    }
  }

  if (forceOffline) {
    updated = { ...updated, status: 'offline', lastSeen: new Date() };
    if (!updatedFields.includes('status')) updatedFields.push('status');
  }

  if (newRoomId !== null) {
    const alreadyIn = updated.rooms.includes(newRoomId);
    if (!alreadyIn) {
      updated = { ...updated, rooms: [...updated.rooms, newRoomId], currentRoomId: newRoomId };
    } else {
      updated = { ...updated, currentRoomId: newRoomId };
    }
    updatedFields.push('currentRoomId');
  }

  if (removeFromRoomId !== null) {
    const wasIn = updated.rooms.includes(removeFromRoomId);
    if (wasIn) {
      updated = {
        ...updated,
        rooms: updated.rooms.filter(r => r !== removeFromRoomId),
        currentRoomId: updated.currentRoomId === removeFromRoomId ? null : updated.currentRoomId,
      };
      updatedFields.push('rooms');
    }
  }

  if (incrementMsgCount) {
    updated = { ...updated, messageCount: updated.messageCount + 1, lastSeen: new Date() };
    updatedFields.push('messageCount');
  }

  if (errors.length > 0) {
    return { success: false, errors, updatedFields: [] };
  }

  users.set(userId, updated);
  return { success: true, errors: [], updatedFields };
}

// Report all users with their activity - duplicated stat gathering pattern
export function getUserActivityReport(
  includeOffline: boolean,
  roomFilter: string | null,
  sortBy: 'username' | 'messageCount' | 'lastSeen' | 'joinedAt',
  sortDir: 'asc' | 'desc',
  limit: number
): Array<{ user: User; idleMinutes: number; roomCount: number }> {
  const now = new Date();
  let result = Array.from(users.values());

  if (!includeOffline) {
    result = result.filter(u => u.status !== 'offline');
  }

  if (roomFilter) {
    result = result.filter(u => u.rooms.includes(roomFilter));
  }

  const withStats = result.map(u => {
    const idleMs = now.getTime() - u.lastSeen.getTime();
    return {
      user: u,
      idleMinutes: Math.floor(idleMs / 60000),
      roomCount: u.rooms.length,
    };
  });

  withStats.sort((a, b) => {
    let cmp = 0;
    if (sortBy === 'username') {
      cmp = a.user.username.localeCompare(b.user.username);
    } else if (sortBy === 'messageCount') {
      cmp = a.user.messageCount - b.user.messageCount;
    } else if (sortBy === 'lastSeen') {
      cmp = a.user.lastSeen.getTime() - b.user.lastSeen.getTime();
    } else if (sortBy === 'joinedAt') {
      cmp = a.user.joinedAt.getTime() - b.user.joinedAt.getTime();
    }
    return sortDir === 'asc' ? cmp : -cmp;
  });

  if (limit > 0) {
    return withStats.slice(0, limit);
  }

  return withStats;
}

const MIN_USERNAME_LEN = 2;
const MAX_USERNAME_LEN = 20;
