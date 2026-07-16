package com.chatapp;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages persistent message storage, indexing, and retrieval.
 * Handles all read/write operations for chat message persistence.
 */
public class MessageStore {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private static final int MAX_MESSAGES_PER_ROOM = 10000;
    private static final int MAX_PRIVATE_MESSAGES = 5000;
    private static final int INDEX_WORD_MIN_LENGTH = 3;

    // Primary storage: room -> list of messages
    private Map<String, List<Map<String, String>>> roomStorage;
    // Private message storage: conversationKey -> list of messages
    private Map<String, List<Map<String, String>>> privateStorage;
    // Inverted index for search: word -> list of message IDs
    private Map<String, List<String>> searchIndex;
    // Message lookup by ID
    private Map<String, Map<String, String>> messageById;
    // Room metadata
    private Map<String, Map<String, String>> roomMetadata;
    // Statistics counters
    private Map<String, Integer> wordFrequency;
    private Map<String, Integer> userPostCount;
    private Map<String, Long> roomLastActivity;
    private int nextMessageId;
    private long totalStoredBytes;
    private int pruneCount;

    public MessageStore() {
        this.roomStorage = new HashMap<>();
        this.privateStorage = new HashMap<>();
        this.searchIndex = new HashMap<>();
        this.messageById = new HashMap<>();
        this.roomMetadata = new HashMap<>();
        this.wordFrequency = new HashMap<>();
        this.userPostCount = new HashMap<>();
        this.roomLastActivity = new HashMap<>();
        this.nextMessageId = 1;
        this.totalStoredBytes = 0;
        this.pruneCount = 0;
    }

    public String storeMessage(String room, String username, String content, String type, long timestamp) {
        if (room == null || room.isEmpty()) {
            return null;
        }
        if (username == null || username.isEmpty()) {
            return null;
        }
        if (content == null || content.isEmpty()) {
            return null;
        }
        if (type == null) {
            type = "public";
        }
        String messageId = "msg-" + (nextMessageId++);
        Map<String, String> message = new HashMap<>();
        message.put("id", messageId);
        message.put("room", room);
        message.put("username", username);
        message.put("content", content);
        message.put("type", type);
        message.put("timestamp", String.valueOf(timestamp));
        message.put("timeStr", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        message.put("dateStr", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        if (!roomStorage.containsKey(room)) {
            roomStorage.put(room, new ArrayList<>());
        }
        roomStorage.get(room).add(message);
        messageById.put(messageId, message);
        totalStoredBytes += content.length() + username.length() + 50;
        // Update room metadata
        if (!roomMetadata.containsKey(room)) {
            Map<String, String> meta = new HashMap<>();
            meta.put("created", message.get("dateStr"));
            meta.put("messageCount", "0");
            roomMetadata.put(room, meta);
        }
        Map<String, String> meta = roomMetadata.get(room);
        int roomMsgCount = Integer.parseInt(meta.getOrDefault("messageCount", "0"));
        meta.put("messageCount", String.valueOf(roomMsgCount + 1));
        meta.put("lastMessage", content.substring(0, Math.min(100, content.length())));
        meta.put("lastUser", username);
        meta.put("lastTime", message.get("timeStr"));
        roomLastActivity.put(room, timestamp);
        // Update user post count
        int userCount = userPostCount.getOrDefault(username, 0);
        userPostCount.put(username, userCount + 1);
        // Index words for search
        indexMessage(messageId, content);
        // Prune if needed
        if (roomStorage.get(room).size() > MAX_MESSAGES_PER_ROOM) {
            pruneRoom(room);
        }
        return messageId;
    }

    public String storePrivateMessage(String fromUser, String toUser, String content, long timestamp) {
        if (fromUser == null || toUser == null || content == null) {
            return null;
        }
        if (fromUser.isEmpty() || toUser.isEmpty() || content.isEmpty()) {
            return null;
        }
        String key = fromUser.compareTo(toUser) < 0 ? fromUser + ":" + toUser : toUser + ":" + fromUser;
        String messageId = "pm-" + (nextMessageId++);
        Map<String, String> message = new HashMap<>();
        message.put("id", messageId);
        message.put("from", fromUser);
        message.put("to", toUser);
        message.put("content", content);
        message.put("type", "private");
        message.put("timestamp", String.valueOf(timestamp));
        message.put("timeStr", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        message.put("dateStr", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        message.put("read", "false");
        if (!privateStorage.containsKey(key)) {
            privateStorage.put(key, new ArrayList<>());
        }
        privateStorage.get(key).add(message);
        messageById.put(messageId, message);
        totalStoredBytes += content.length() + fromUser.length() + toUser.length() + 50;
        indexMessage(messageId, content);
        if (privateStorage.get(key).size() > MAX_PRIVATE_MESSAGES) {
            List<String[]> pruned = new ArrayList<>();
            List<Map<String, String>> msgs = privateStorage.get(key);
            int removeCount = msgs.size() - MAX_PRIVATE_MESSAGES;
            for (int i = 0; i < removeCount; i++) {
                Map<String, String> removed = msgs.remove(0);
                String removedId = removed.get("id");
                messageById.remove(removedId);
                pruneCount++;
            }
        }
        return messageId;
    }

    private void indexMessage(String messageId, String content) {
        String[] words = content.toLowerCase().split("[^a-zA-Z0-9]+");
        for (String word : words) {
            if (word.length() < INDEX_WORD_MIN_LENGTH) {
                continue;
            }
            if (!searchIndex.containsKey(word)) {
                searchIndex.put(word, new ArrayList<>());
            }
            searchIndex.get(word).add(messageId);
            // Trim large index entries
            if (searchIndex.get(word).size() > 1000) {
                List<String> idx = searchIndex.get(word);
                idx.remove(0);
            }
            // Track word frequency
            int freq = wordFrequency.getOrDefault(word, 0);
            wordFrequency.put(word, freq + 1);
        }
    }

    private void pruneRoom(String room) {
        if (!roomStorage.containsKey(room)) {
            return;
        }
        List<Map<String, String>> messages = roomStorage.get(room);
        int targetSize = MAX_MESSAGES_PER_ROOM / 2;
        int removeCount = messages.size() - targetSize;
        for (int i = 0; i < removeCount; i++) {
            Map<String, String> removed = messages.remove(0);
            String removedId = removed.get("id");
            if (removedId != null) {
                messageById.remove(removedId);
            }
            pruneCount++;
        }
    }

    public List<Map<String, String>> getRecentMessages(String room, int limit) {
        if (room == null || !roomStorage.containsKey(room)) {
            return new ArrayList<>();
        }
        List<Map<String, String>> messages = roomStorage.get(room);
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    public List<Map<String, String>> searchByKeyword(String keyword, String room, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) {
            return results;
        }
        String lowerKeyword = keyword.toLowerCase();
        // Try index first for speed
        if (searchIndex.containsKey(lowerKeyword)) {
            List<String> ids = searchIndex.get(lowerKeyword);
            for (String id : ids) {
                if (results.size() >= maxResults) {
                    break;
                }
                Map<String, String> msg = messageById.get(id);
                if (msg != null) {
                    if (room == null || room.isEmpty() || room.equals("*") || room.equals(msg.get("room"))) {
                        results.add(msg);
                    }
                }
            }
        }
        // Fall back to linear scan if not enough results
        if (results.size() < maxResults) {
            Collection<List<Map<String, String>>> storageSets;
            if (room != null && !room.isEmpty() && !room.equals("*") && roomStorage.containsKey(room)) {
                storageSets = Collections.singleton(roomStorage.get(room));
            } else {
                storageSets = roomStorage.values();
            }
            for (List<Map<String, String>> roomMsgs : storageSets) {
                for (Map<String, String> msg : roomMsgs) {
                    if (results.size() >= maxResults) {
                        break;
                    }
                    String content = msg.get("content");
                    if (content != null && content.toLowerCase().contains(lowerKeyword)) {
                        if (!results.contains(msg)) {
                            results.add(msg);
                        }
                    }
                }
            }
        }
        return results;
    }

    public List<Map<String, String>> searchByUser(String username, String room, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();
        if (username == null || username.isEmpty()) {
            return results;
        }
        Collection<List<Map<String, String>>> storageSets;
        if (room != null && !room.isEmpty() && !room.equals("*") && roomStorage.containsKey(room)) {
            storageSets = Collections.singleton(roomStorage.get(room));
        } else {
            storageSets = roomStorage.values();
        }
        for (List<Map<String, String>> roomMsgs : storageSets) {
            for (Map<String, String> msg : roomMsgs) {
                if (results.size() >= maxResults) {
                    break;
                }
                String msgUser = msg.get("username");
                if (username.equalsIgnoreCase(msgUser)) {
                    results.add(msg);
                }
            }
        }
        return results;
    }

    public List<Map<String, String>> searchByDateRange(String room, long fromTimestamp, long toTimestamp, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();
        Collection<List<Map<String, String>>> storageSets;
        if (room != null && !room.isEmpty() && !room.equals("*") && roomStorage.containsKey(room)) {
            storageSets = Collections.singleton(roomStorage.get(room));
        } else {
            storageSets = roomStorage.values();
        }
        for (List<Map<String, String>> roomMsgs : storageSets) {
            for (Map<String, String> msg : roomMsgs) {
                if (results.size() >= maxResults) {
                    break;
                }
                String tsStr = msg.get("timestamp");
                if (tsStr != null) {
                    try {
                        long ts = Long.parseLong(tsStr);
                        boolean afterFrom = fromTimestamp <= 0 || ts >= fromTimestamp;
                        boolean beforeTo = toTimestamp <= 0 || ts <= toTimestamp;
                        if (afterFrom && beforeTo) {
                            results.add(msg);
                        }
                    } catch (NumberFormatException e) {
                        // skip malformed
                    }
                }
            }
        }
        return results;
    }

    public Map<String, String> getMessageById(String messageId) {
        return messageById.get(messageId);
    }

    public boolean deleteMessage(String messageId, String requestingUser) {
        Map<String, String> msg = messageById.get(messageId);
        if (msg == null) {
            return false;
        }
        String msgUser = msg.get("username");
        if (msgUser == null || !msgUser.equals(requestingUser)) {
            return false;
        }
        String room = msg.get("room");
        if (room != null && roomStorage.containsKey(room)) {
            roomStorage.get(room).remove(msg);
        }
        messageById.remove(messageId);
        return true;
    }

    public List<Map<String, String>> getPrivateMessages(String user1, String user2, int limit) {
        String key = user1.compareTo(user2) < 0 ? user1 + ":" + user2 : user2 + ":" + user1;
        List<Map<String, String>> messages = privateStorage.getOrDefault(key, new ArrayList<>());
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    public void markPrivateMessagesRead(String fromUser, String toUser) {
        String key = fromUser.compareTo(toUser) < 0 ? fromUser + ":" + toUser : toUser + ":" + fromUser;
        List<Map<String, String>> messages = privateStorage.getOrDefault(key, new ArrayList<>());
        for (Map<String, String> msg : messages) {
            if (toUser.equals(msg.get("to"))) {
                msg.put("read", "true");
            }
        }
    }

    public int getUnreadCount(String username) {
        int count = 0;
        for (List<Map<String, String>> conversation : privateStorage.values()) {
            for (Map<String, String> msg : conversation) {
                if (username.equals(msg.get("to")) && "false".equals(msg.get("read"))) {
                    count++;
                }
            }
        }
        return count;
    }

    public Map<String, Integer> getRoomMessageCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, List<Map<String, String>>> entry : roomStorage.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    public List<String> getMostActiveRooms(int limit) {
        Map<String, Integer> counts = getRoomMessageCounts();
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            result.add(sorted.get(i).getKey());
        }
        return result;
    }

    public List<String> getTopWords(int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(wordFrequency.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            result.add(sorted.get(i).getKey() + "(" + sorted.get(i).getValue() + ")");
        }
        return result;
    }

    public void printStorageStats() {
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Message Store Statistics ===" + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Total rooms with messages: " + roomStorage.size() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Total private conversations: " + privateStorage.size() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Total indexed messages: " + messageById.size() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Total search index entries: " + searchIndex.size() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Approximate storage bytes: " + totalStoredBytes + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Messages pruned: " + pruneCount + ANSI_RESET);
        System.out.println(ANSI_CYAN + ANSI_BOLD + "Top 5 most active rooms:" + ANSI_RESET);
        for (String room : getMostActiveRooms(5)) {
            List<Map<String, String>> msgs = roomStorage.get(room);
            System.out.println("  #" + room + ": " + (msgs != null ? msgs.size() : 0) + " messages");
        }
        System.out.println(ANSI_CYAN + ANSI_BOLD + "Top 10 words:" + ANSI_RESET);
        for (String word : getTopWords(10)) {
            System.out.println("  " + word);
        }
    }

    public int getTotalMessageCount() {
        int total = 0;
        for (List<Map<String, String>> msgs : roomStorage.values()) {
            total += msgs.size();
        }
        return total;
    }

    public Map<String, Integer> getUserPostCounts() {
        return new HashMap<>(userPostCount);
    }

    public void clearRoom(String room) {
        if (roomStorage.containsKey(room)) {
            List<Map<String, String>> msgs = roomStorage.get(room);
            for (Map<String, String> msg : msgs) {
                messageById.remove(msg.get("id"));
            }
            roomStorage.remove(room);
        }
        roomMetadata.remove(room);
        roomLastActivity.remove(room);
    }

    public Map<String, String> getRoomMetadata(String room) {
        return roomMetadata.getOrDefault(room, new HashMap<>());
    }

    public long getRoomLastActivity(String room) {
        return roomLastActivity.getOrDefault(room, 0L);
    }

    public List<String> getAllRooms() {
        return new ArrayList<>(roomStorage.keySet());
    }
}
