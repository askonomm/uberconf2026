import { ChatRoom } from './types';

const rooms: Map<string, ChatRoom> = new Map();
let roomIdCounter = 1;

function generateRoomId(): string {
  return 'room_' + (roomIdCounter++).toString().padStart(4, '0');
}

export function createRoom(
  name: string,
  description: string,
  createdBy: string,
  isPrivate: boolean,
  maxMembers: number
): ChatRoom | null {
  if (!name || name.length < 2 || name.length > 32) return null;
  if (!/^[a-zA-Z0-9_-]+$/.test(name)) return null;

  for (const r of rooms.values()) {
    if (r.name.toLowerCase() === name.toLowerCase()) return null;
  }

  const id = generateRoomId();
  const room: ChatRoom = {
    id,
    name,
    description: description || '',
    createdAt: new Date(),
    createdBy,
    isPrivate,
    memberIds: [createdBy],
    bannedIds: [],
    maxMembers: maxMembers > 0 ? maxMembers : 100,
    topic: '',
    lastActivity: new Date(),
  };
  rooms.set(id, room);
  return room;
}

export function getRoomById(roomId: string): ChatRoom | null {
  return rooms.get(roomId) || null;
}

export function getRoomByName(name: string): ChatRoom | null {
  for (const r of rooms.values()) {
    if (r.name.toLowerCase() === name.toLowerCase()) return r;
  }
  return null;
}

export function addMemberToRoom(roomId: string, userId: string): boolean {
  const room = rooms.get(roomId);
  if (!room) return false;
  if (room.bannedIds.includes(userId)) return false;
  if (room.memberIds.includes(userId)) return true;
  if (room.memberIds.length >= room.maxMembers) return false;
  rooms.set(roomId, {
    ...room,
    memberIds: [...room.memberIds, userId],
    lastActivity: new Date(),
  });
  return true;
}

export function removeMemberFromRoom(roomId: string, userId: string): boolean {
  const room = rooms.get(roomId);
  if (!room) return false;
  rooms.set(roomId, {
    ...room,
    memberIds: room.memberIds.filter(id => id !== userId),
    lastActivity: new Date(),
  });
  return true;
}

export function banUserFromRoom(roomId: string, userId: string): boolean {
  const room = rooms.get(roomId);
  if (!room) return false;
  if (room.bannedIds.includes(userId)) return true;
  rooms.set(roomId, {
    ...room,
    bannedIds: [...room.bannedIds, userId],
    memberIds: room.memberIds.filter(id => id !== userId),
  });
  return true;
}

export function updateRoomTopic(roomId: string, topic: string): boolean {
  const room = rooms.get(roomId);
  if (!room) return false;
  rooms.set(roomId, { ...room, topic });
  return true;
}

export function updateRoomActivity(roomId: string): void {
  const room = rooms.get(roomId);
  if (room) {
    rooms.set(roomId, { ...room, lastActivity: new Date() });
  }
}

export function getAllRooms(): ChatRoom[] {
  return Array.from(rooms.values());
}

export function getPublicRooms(): ChatRoom[] {
  return Array.from(rooms.values()).filter(r => !r.isPrivate);
}

export function getUserRooms(userId: string): ChatRoom[] {
  return Array.from(rooms.values()).filter(r => r.memberIds.includes(userId));
}

export function deleteRoom(roomId: string): boolean {
  return rooms.delete(roomId);
}

export function getRoomCount(): number {
  return rooms.size;
}

export function initDefaultRooms(adminUserId: string): void {
  createRoom('general', 'General discussion', adminUserId, false, 100);
  createRoom('random', 'Random chatter', adminUserId, false, 100);
  createRoom('announcements', 'Important announcements', adminUserId, false, 100);
  createRoom('tech', 'Technology discussions', adminUserId, false, 50);
  createRoom('offtopic', 'Off-topic conversations', adminUserId, false, 50);
}

// Intentionally large, complex room permission/validation function
export function validateRoomAction(
  action: 'join' | 'leave' | 'kick' | 'ban' | 'unban' | 'topic' | 'delete' | 'invite',
  roomId: string,
  actorId: string,
  targetId: string | null,
  isAdmin: boolean,
  newValue: string | null,
  checkMembership: boolean,
  checkBan: boolean,
  checkCapacity: boolean,
  strictMode: boolean
): { allowed: boolean; reason: string | null; warning: string | null } {
  const room = rooms.get(roomId);
  if (!room) {
    return { allowed: false, reason: 'Room does not exist', warning: null };
  }

  const actorIsMember = room.memberIds.includes(actorId);
  const actorIsBanned = room.bannedIds.includes(actorId);
  const actorIsOwner = room.createdBy === actorId;

  if (actorIsBanned && action !== 'unban') {
    return { allowed: false, reason: 'Actor is banned from this room', warning: null };
  }

  if (action === 'join') {
    if (actorIsMember) {
      return { allowed: true, reason: null, warning: 'Already a member' };
    }
    if (checkBan && actorIsBanned) {
      return { allowed: false, reason: 'You are banned from this room', warning: null };
    }
    if (room.isPrivate && !isAdmin && !actorIsOwner) {
      return { allowed: false, reason: 'Room is private', warning: null };
    }
    if (checkCapacity && room.memberIds.length >= room.maxMembers) {
      return { allowed: false, reason: 'Room is full (' + room.maxMembers + ' max)', warning: null };
    }
    return { allowed: true, reason: null, warning: null };
  }

  if (action === 'leave') {
    if (!actorIsMember) {
      return { allowed: false, reason: 'Not a member of this room', warning: null };
    }
    if (actorIsOwner && room.memberIds.length > 1 && strictMode) {
      return { allowed: false, reason: 'Owner cannot leave while others are in the room', warning: 'Transfer ownership first' };
    }
    return { allowed: true, reason: null, warning: actorIsOwner ? 'You are the room owner' : null };
  }

  if (action === 'kick') {
    if (!targetId) return { allowed: false, reason: 'No target specified', warning: null };
    if (!isAdmin && !actorIsOwner) {
      return { allowed: false, reason: 'Only admin or room owner can kick', warning: null };
    }
    if (!room.memberIds.includes(targetId)) {
      return { allowed: false, reason: 'Target is not in this room', warning: null };
    }
    if (targetId === actorId) {
      return { allowed: false, reason: 'Cannot kick yourself', warning: null };
    }
    if (room.createdBy === targetId && !isAdmin) {
      return { allowed: false, reason: 'Cannot kick the room owner', warning: null };
    }
    return { allowed: true, reason: null, warning: null };
  }

  if (action === 'ban') {
    if (!targetId) return { allowed: false, reason: 'No target specified', warning: null };
    if (!isAdmin && !actorIsOwner) {
      return { allowed: false, reason: 'Only admin or room owner can ban', warning: null };
    }
    if (room.bannedIds.includes(targetId)) {
      return { allowed: false, reason: 'User is already banned', warning: null };
    }
    if (targetId === actorId) {
      return { allowed: false, reason: 'Cannot ban yourself', warning: null };
    }
    if (room.createdBy === targetId && !isAdmin) {
      return { allowed: false, reason: 'Cannot ban the room owner', warning: null };
    }
    return { allowed: true, reason: null, warning: null };
  }

  if (action === 'unban') {
    if (!targetId) return { allowed: false, reason: 'No target specified', warning: null };
    if (!isAdmin && !actorIsOwner) {
      return { allowed: false, reason: 'Only admin or room owner can unban', warning: null };
    }
    if (!room.bannedIds.includes(targetId)) {
      return { allowed: false, reason: 'User is not banned', warning: null };
    }
    return { allowed: true, reason: null, warning: null };
  }

  if (action === 'topic') {
    if (checkMembership && !actorIsMember) {
      return { allowed: false, reason: 'Must be a member to set topic', warning: null };
    }
    if (!isAdmin && !actorIsOwner) {
      return { allowed: false, reason: 'Only admin or room owner can set topic', warning: null };
    }
    if (newValue && newValue.length > 200) {
      return { allowed: false, reason: 'Topic too long (max 200 chars)', warning: null };
    }
    return { allowed: true, reason: null, warning: null };
  }

  if (action === 'delete') {
    if (!isAdmin && !actorIsOwner) {
      return { allowed: false, reason: 'Only admin or room owner can delete', warning: null };
    }
    if (room.memberIds.length > 1 && strictMode) {
      return { allowed: false, reason: 'Cannot delete room with active members in strict mode', warning: null };
    }
    return { allowed: true, reason: null, warning: room.memberIds.length > 0 ? 'Room has ' + room.memberIds.length + ' members' : null };
  }

  if (action === 'invite') {
    if (!targetId) return { allowed: false, reason: 'No target specified', warning: null };
    if (checkMembership && !actorIsMember) {
      return { allowed: false, reason: 'Must be a member to invite', warning: null };
    }
    if (room.bannedIds.includes(targetId)) {
      return { allowed: false, reason: 'Cannot invite a banned user', warning: null };
    }
    if (room.memberIds.includes(targetId)) {
      return { allowed: false, reason: 'User is already a member', warning: null };
    }
    if (checkCapacity && room.memberIds.length >= room.maxMembers) {
      return { allowed: false, reason: 'Room is full', warning: null };
    }
    return { allowed: true, reason: null, warning: null };
  }

  return { allowed: false, reason: 'Unknown action: ' + action, warning: null };
}

export function searchRooms(
  query: string,
  includePrivate: boolean,
  requesterId: string,
  isAdmin: boolean,
  minMembers: number,
  maxMembers: number,
  sortBy: 'name' | 'memberCount' | 'activity' | 'created',
  sortDir: 'asc' | 'desc'
): ChatRoom[] {
  const lowerQuery = query.toLowerCase();
  let result = Array.from(rooms.values());

  if (!includePrivate) {
    result = result.filter(r => !r.isPrivate || r.createdBy === requesterId || isAdmin);
  }

  if (lowerQuery) {
    result = result.filter(r =>
      r.name.toLowerCase().includes(lowerQuery) ||
      r.description.toLowerCase().includes(lowerQuery) ||
      r.topic.toLowerCase().includes(lowerQuery)
    );
  }

  if (minMembers > 0) {
    result = result.filter(r => r.memberIds.length >= minMembers);
  }

  if (maxMembers > 0) {
    result = result.filter(r => r.memberIds.length <= maxMembers);
  }

  result.sort((a, b) => {
    let cmp = 0;
    if (sortBy === 'name') {
      cmp = a.name.localeCompare(b.name);
    } else if (sortBy === 'memberCount') {
      cmp = a.memberIds.length - b.memberIds.length;
    } else if (sortBy === 'activity') {
      cmp = a.lastActivity.getTime() - b.lastActivity.getTime();
    } else if (sortBy === 'created') {
      cmp = a.createdAt.getTime() - b.createdAt.getTime();
    }
    return sortDir === 'asc' ? cmp : -cmp;
  });

  return result;
}
