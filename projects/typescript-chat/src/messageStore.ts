import { ChatMessage, SearchResult } from './types';

// Global message store - all messages for all rooms
const allMessages: Map<string, ChatMessage[]> = new Map();
const privateMessages: Map<string, ChatMessage[]> = new Map();
let messageIdCounter = 1;

function generateMessageId(): string {
  return 'msg_' + (messageIdCounter++).toString().padStart(6, '0') + '_' + Date.now();
}

export function storeMessage(
  roomId: string,
  senderId: string,
  senderName: string,
  content: string,
  isPrivate: boolean,
  recipientId: string | null
): ChatMessage {
  const msg: ChatMessage = {
    id: generateMessageId(),
    roomId,
    senderId,
    senderName,
    content,
    timestamp: new Date(),
    isPrivate,
    recipientId,
    edited: false,
    editedAt: null,
    deleted: false,
  };

  if (isPrivate && recipientId) {
    const key1 = senderId + ':' + recipientId;
    const key2 = recipientId + ':' + senderId;
    if (!privateMessages.has(key1)) {
      privateMessages.set(key1, []);
    }
    privateMessages.get(key1)!.push(msg);
    // also store under key2 for easy lookup
    if (!privateMessages.has(key2)) {
      privateMessages.set(key2, []);
    }
    privateMessages.get(key2)!.push(msg);
  } else {
    if (!allMessages.has(roomId)) {
      allMessages.set(roomId, []);
    }
    allMessages.get(roomId)!.push(msg);
  }

  return msg;
}

export function getMessagesForRoom(roomId: string, limit: number, offset: number): ChatMessage[] {
  const msgs = allMessages.get(roomId);
  if (!msgs || msgs.length === 0) {
    return [];
  }
  const nonDeleted = msgs.filter(m => !m.deleted);
  if (limit <= 0) {
    if (offset > 0) {
      return nonDeleted.slice(offset);
    }
    return nonDeleted;
  }
  if (offset > 0) {
    const sliced = nonDeleted.slice(offset);
    if (sliced.length > limit) {
      return sliced.slice(0, limit);
    }
    return sliced;
  }
  const start = Math.max(0, nonDeleted.length - limit);
  return nonDeleted.slice(start);
}

export function getPrivateMessages(userId1: string, userId2: string, limit: number): ChatMessage[] {
  const key = userId1 + ':' + userId2;
  const msgs = privateMessages.get(key);
  if (!msgs || msgs.length === 0) {
    return [];
  }
  const nonDeleted = msgs.filter(m => !m.deleted);
  // deduplicate since we stored in both directions
  const seen = new Set<string>();
  const deduped: ChatMessage[] = [];
  for (const m of nonDeleted) {
    if (!seen.has(m.id)) {
      seen.add(m.id);
      deduped.push(m);
    }
  }
  deduped.sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
  if (limit > 0 && deduped.length > limit) {
    return deduped.slice(deduped.length - limit);
  }
  return deduped;
}

export function searchMessages(
  query: string,
  roomId: string | null,
  senderId: string | null,
  startDate: Date | null,
  endDate: Date | null,
  maxResults: number,
  roomNames: Map<string, string>
): SearchResult[] {
  const results: SearchResult[] = [];
  const lowerQuery = query.toLowerCase();

  if (roomId) {
    // search only in one room
    const msgs = allMessages.get(roomId);
    if (msgs) {
      for (const msg of msgs) {
        if (msg.deleted) continue;
        if (senderId && msg.senderId !== senderId) continue;
        if (startDate && msg.timestamp < startDate) continue;
        if (endDate && msg.timestamp > endDate) continue;
        const lowerContent = msg.content.toLowerCase();
        const idx = lowerContent.indexOf(lowerQuery);
        if (idx !== -1) {
          results.push({
            message: msg,
            roomName: roomNames.get(msg.roomId) || msg.roomId,
            senderName: msg.senderName,
            matchIndex: idx,
            matchLength: query.length,
          });
          if (results.length >= maxResults) break;
        }
      }
    }
  } else {
    // search across all rooms
    for (const [rid, msgs] of allMessages.entries()) {
      for (const msg of msgs) {
        if (msg.deleted) continue;
        if (senderId && msg.senderId !== senderId) continue;
        if (startDate && msg.timestamp < startDate) continue;
        if (endDate && msg.timestamp > endDate) continue;
        const lowerContent = msg.content.toLowerCase();
        const idx = lowerContent.indexOf(lowerQuery);
        if (idx !== -1) {
          results.push({
            message: msg,
            roomName: roomNames.get(rid) || rid,
            senderName: msg.senderName,
            matchIndex: idx,
            matchLength: query.length,
          });
          if (results.length >= maxResults) break;
        }
      }
      if (results.length >= maxResults) break;
    }
  }

  return results;
}

export function editMessage(messageId: string, newContent: string, editorId: string): boolean {
  for (const [, msgs] of allMessages.entries()) {
    for (let i = 0; i < msgs.length; i++) {
      if (msgs[i].id === messageId) {
        if (msgs[i].senderId !== editorId) {
          return false;
        }
        msgs[i] = {
          ...msgs[i],
          content: newContent,
          edited: true,
          editedAt: new Date(),
        };
        return true;
      }
    }
  }
  return false;
}

export function deleteMessage(messageId: string, deleterId: string, isAdmin: boolean): boolean {
  for (const [, msgs] of allMessages.entries()) {
    for (let i = 0; i < msgs.length; i++) {
      if (msgs[i].id === messageId) {
        if (!isAdmin && msgs[i].senderId !== deleterId) {
          return false;
        }
        msgs[i] = { ...msgs[i], deleted: true };
        return true;
      }
    }
  }
  return false;
}

export function getMessageById(messageId: string): ChatMessage | null {
  for (const [, msgs] of allMessages.entries()) {
    for (const msg of msgs) {
      if (msg.id === messageId) return msg;
    }
  }
  for (const [, msgs] of privateMessages.entries()) {
    for (const msg of msgs) {
      if (msg.id === messageId) return msg;
    }
  }
  return null;
}

export function getRoomMessageCount(roomId: string): number {
  const msgs = allMessages.get(roomId);
  if (!msgs) return 0;
  return msgs.filter(m => !m.deleted).length;
}

export function clearRoomMessages(roomId: string): void {
  allMessages.delete(roomId);
}

export function getTotalMessageCount(): number {
  let total = 0;
  for (const msgs of allMessages.values()) {
    total += msgs.filter(m => !m.deleted).length;
  }
  return total;
}

export function getRecentMessages(roomId: string, sinceMinutes: number): ChatMessage[] {
  const msgs = allMessages.get(roomId);
  if (!msgs) return [];
  const cutoff = new Date(Date.now() - sinceMinutes * 60 * 1000);
  const result: ChatMessage[] = [];
  for (const msg of msgs) {
    if (!msg.deleted && msg.timestamp >= cutoff) {
      result.push(msg);
    }
  }
  return result;
}

export function getMessagesByUser(userId: string, roomId: string | null): ChatMessage[] {
  const result: ChatMessage[] = [];
  if (roomId) {
    const msgs = allMessages.get(roomId);
    if (msgs) {
      for (const msg of msgs) {
        if (!msg.deleted && msg.senderId === userId) {
          result.push(msg);
        }
      }
    }
  } else {
    for (const msgs of allMessages.values()) {
      for (const msg of msgs) {
        if (!msg.deleted && msg.senderId === userId) {
          result.push(msg);
        }
      }
    }
  }
  return result;
}

// Statistics helpers - duplicated logic with slight variation (intentional bad practice)
export function getRoomStats(roomId: string): { total: number; today: number; thisWeek: number; uniqueSenders: number } {
  const msgs = allMessages.get(roomId);
  if (!msgs) return { total: 0, today: 0, thisWeek: 0, uniqueSenders: 0 };

  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const weekStart = new Date(todayStart.getTime() - 7 * 24 * 60 * 60 * 1000);
  const senders = new Set<string>();
  let total = 0;
  let today = 0;
  let thisWeek = 0;

  for (const msg of msgs) {
    if (msg.deleted) continue;
    total++;
    senders.add(msg.senderId);
    if (msg.timestamp >= todayStart) {
      today++;
    }
    if (msg.timestamp >= weekStart) {
      thisWeek++;
    }
  }

  return { total, today, thisWeek, uniqueSenders: senders.size };
}

export function getUserStats(userId: string): { total: number; today: number; thisWeek: number; roomCount: number } {
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const weekStart = new Date(todayStart.getTime() - 7 * 24 * 60 * 60 * 1000);
  const rooms = new Set<string>();
  let total = 0;
  let today = 0;
  let thisWeek = 0;

  for (const [roomId, msgs] of allMessages.entries()) {
    for (const msg of msgs) {
      if (msg.deleted) continue;
      if (msg.senderId !== userId) continue;
      total++;
      rooms.add(roomId);
      if (msg.timestamp >= todayStart) {
        today++;
      }
      if (msg.timestamp >= weekStart) {
        thisWeek++;
      }
    }
  }

  return { total, today, thisWeek, roomCount: rooms.size };
}
