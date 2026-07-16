// Message and room data structures
export interface ChatMessage {
  id: string;
  roomId: string;
  senderId: string;
  senderName: string;
  content: string;
  timestamp: Date;
  isPrivate: boolean;
  recipientId: string | null;
  edited: boolean;
  editedAt: Date | null;
  deleted: boolean;
}

export interface ChatRoom {
  id: string;
  name: string;
  description: string;
  createdAt: Date;
  createdBy: string;
  isPrivate: boolean;
  memberIds: string[];
  bannedIds: string[];
  maxMembers: number;
  topic: string;
  lastActivity: Date;
}

export interface User {
  id: string;
  username: string;
  displayName: string;
  joinedAt: Date;
  lastSeen: Date;
  currentRoomId: string | null;
  status: 'online' | 'away' | 'offline';
  color: string;
  messageCount: number;
  rooms: string[];
}

export interface SearchResult {
  message: ChatMessage;
  roomName: string;
  senderName: string;
  matchIndex: number;
  matchLength: number;
}

export interface CommandResult {
  success: boolean;
  output: string;
  error: string | null;
  data: Record<string, unknown> | null;
}

export interface AppState {
  currentUserId: string | null;
  currentRoomId: string | null;
  isRunning: boolean;
  inputBuffer: string;
  lastError: string | null;
}
