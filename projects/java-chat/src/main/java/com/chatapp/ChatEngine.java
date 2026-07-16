package com.chatapp;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Core chat engine - handles message routing, delivery, and chat state.
 * This class is the heart of the application and coordinates between all subsystems.
 */
public class ChatEngine {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private Map<String, List<String[]>> roomMessages;
    private Map<String, List<String[]>> privateMessages;
    private Map<String, String> userColors;
    private Map<String, Integer> userMessageCounts;
    private Map<String, Long> lastMessageTime;
    private Map<String, Boolean> userMuteStatus;
    private Map<String, List<String>> userMentions;
    private List<String[]> globalEventLog;
    private int totalMessageCount;
    private boolean profanityFilterEnabled;
    private boolean rateLimitEnabled;
    private long rateLimitWindowMs;
    private int rateLimitMaxMessages;
    private static final String[] BAD_WORDS = {"spam", "flood", "abuse"};
    private static final String[] USER_COLORS = {
        ANSI_RED, ANSI_GREEN, ANSI_YELLOW, ANSI_BLUE, ANSI_PURPLE, ANSI_CYAN, ANSI_WHITE
    };

    public ChatEngine() {
        this.roomMessages = new HashMap<>();
        this.privateMessages = new HashMap<>();
        this.userColors = new HashMap<>();
        this.userMessageCounts = new HashMap<>();
        this.lastMessageTime = new HashMap<>();
        this.userMuteStatus = new HashMap<>();
        this.userMentions = new HashMap<>();
        this.globalEventLog = new ArrayList<>();
        this.totalMessageCount = 0;
        this.profanityFilterEnabled = true;
        this.rateLimitEnabled = true;
        this.rateLimitWindowMs = 1000;
        this.rateLimitMaxMessages = 5;
    }

    // Main message sending method - handles routing and display
    public void sendMessage(String username, String room, String content, String messageType,
                             long timestamp, boolean isSystem, String targetUser, int sessionCount) {
        if (username == null || username.isEmpty()) {
            System.out.println(ANSI_RED + "Error: Username cannot be empty" + ANSI_RESET);
            return;
        }
        if (room == null || room.isEmpty()) {
            System.out.println(ANSI_RED + "Error: Room cannot be empty" + ANSI_RESET);
            return;
        }
        if (content == null || content.isEmpty()) {
            System.out.println(ANSI_RED + "Error: Message content cannot be empty" + ANSI_RESET);
            return;
        }
        if (content.length() > 500) {
            System.out.println(ANSI_RED + "Error: Message too long (max 500 chars)" + ANSI_RESET);
            return;
        }
        if (userMuteStatus.containsKey(username) && userMuteStatus.get(username)) {
            System.out.println(ANSI_RED + "You are currently muted and cannot send messages" + ANSI_RESET);
            return;
        }
        if (rateLimitEnabled && !isSystem) {
            long now = System.currentTimeMillis();
            if (lastMessageTime.containsKey(username)) {
                long timeDiff = now - lastMessageTime.get(username);
                if (timeDiff < rateLimitWindowMs) {
                    int count = userMessageCounts.getOrDefault(username + "_ratelimit", 0);
                    if (count >= rateLimitMaxMessages) {
                        System.out.println(ANSI_YELLOW + "Rate limit: please slow down!" + ANSI_RESET);
                        return;
                    }
                    userMessageCounts.put(username + "_ratelimit", count + 1);
                } else {
                    userMessageCounts.put(username + "_ratelimit", 1);
                    lastMessageTime.put(username, now);
                }
            } else {
                lastMessageTime.put(username, now);
                userMessageCounts.put(username + "_ratelimit", 1);
            }
        }
        String processedContent = content;
        if (profanityFilterEnabled) {
            for (String badWord : BAD_WORDS) {
                if (processedContent.toLowerCase().contains(badWord)) {
                    processedContent = processedContent.replaceAll("(?i)" + badWord,
                            "*".repeat(badWord.length()));
                }
            }
        }
        // Check for mentions
        List<String> mentions = new ArrayList<>();
        if (processedContent.contains("@")) {
            String[] words = processedContent.split("\\s+");
            for (String word : words) {
                if (word.startsWith("@") && word.length() > 1) {
                    String mentionedUser = word.substring(1).replaceAll("[^a-zA-Z0-9_]", "");
                    if (!mentionedUser.isEmpty()) {
                        mentions.add(mentionedUser);
                        if (!userMentions.containsKey(mentionedUser)) {
                            userMentions.put(mentionedUser, new ArrayList<>());
                        }
                        userMentions.get(mentionedUser).add(username + " mentioned you in " + room + ": " + processedContent);
                    }
                }
            }
        }
        String userColor = getUserColor(username);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timeStr = LocalDateTime.now().format(formatter);
        String[] messageRecord = {username, room, processedContent, messageType,
            String.valueOf(timestamp), timeStr, isSystem ? "true" : "false",
            targetUser != null ? targetUser : ""};
        if (messageType.equals("private") && targetUser != null) {
            String key = getPrivateKey(username, targetUser);
            if (!privateMessages.containsKey(key)) {
                privateMessages.put(key, new ArrayList<>());
            }
            privateMessages.get(key).add(messageRecord);
            System.out.println(ANSI_PURPLE + "[" + timeStr + "] " + ANSI_BOLD +
                    "[PM to " + targetUser + "] " + userColor + username + ANSI_RESET +
                    ANSI_PURPLE + ": " + processedContent + ANSI_RESET);
        } else if (isSystem) {
            if (!roomMessages.containsKey(room)) {
                roomMessages.put(room, new ArrayList<>());
            }
            roomMessages.get(room).add(messageRecord);
            System.out.println(ANSI_CYAN + "[" + timeStr + "] " + ANSI_BOLD + "*** " +
                    processedContent + " ***" + ANSI_RESET);
        } else {
            if (!roomMessages.containsKey(room)) {
                roomMessages.put(room, new ArrayList<>());
            }
            roomMessages.get(room).add(messageRecord);
            // Check if message has mentions
            boolean hasMention = !mentions.isEmpty();
            if (hasMention) {
                System.out.println(ANSI_YELLOW + "[" + timeStr + "] " + userColor + ANSI_BOLD +
                        username + ANSI_RESET + ANSI_YELLOW + ": " + processedContent + ANSI_RESET);
            } else {
                System.out.println(ANSI_WHITE + "[" + timeStr + "] " + userColor + ANSI_BOLD +
                        username + ANSI_RESET + ANSI_WHITE + ": " + processedContent + ANSI_RESET);
            }
        }
        // Update counts
        int currentCount = userMessageCounts.getOrDefault(username, 0);
        userMessageCounts.put(username, currentCount + 1);
        totalMessageCount++;
        // Log the event
        String[] eventRecord = {"MSG", username, room, timeStr, processedContent.substring(0, Math.min(50, processedContent.length()))};
        globalEventLog.add(eventRecord);
        if (globalEventLog.size() > 10000) {
            globalEventLog.remove(0);
        }
    }

    public void sendPrivateMessage(String fromUser, String toUser, String content, long timestamp) {
        if (fromUser == null || fromUser.isEmpty()) {
            System.out.println(ANSI_RED + "Error: Sender username cannot be empty" + ANSI_RESET);
            return;
        }
        if (toUser == null || toUser.isEmpty()) {
            System.out.println(ANSI_RED + "Error: Recipient username cannot be empty" + ANSI_RESET);
            return;
        }
        if (content == null || content.isEmpty()) {
            System.out.println(ANSI_RED + "Error: Message content cannot be empty" + ANSI_RESET);
            return;
        }
        if (content.length() > 500) {
            System.out.println(ANSI_RED + "Error: Message too long (max 500 chars)" + ANSI_RESET);
            return;
        }
        if (fromUser.equals(toUser)) {
            System.out.println(ANSI_RED + "Error: Cannot send private message to yourself" + ANSI_RESET);
            return;
        }
        if (userMuteStatus.containsKey(fromUser) && userMuteStatus.get(fromUser)) {
            System.out.println(ANSI_RED + "You are currently muted and cannot send messages" + ANSI_RESET);
            return;
        }
        String processedContent = content;
        if (profanityFilterEnabled) {
            for (String badWord : BAD_WORDS) {
                if (processedContent.toLowerCase().contains(badWord)) {
                    processedContent = processedContent.replaceAll("(?i)" + badWord,
                            "*".repeat(badWord.length()));
                }
            }
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String timeStr = LocalDateTime.now().format(formatter);
        String fromColor = getUserColor(fromUser);
        String key = getPrivateKey(fromUser, toUser);
        if (!privateMessages.containsKey(key)) {
            privateMessages.put(key, new ArrayList<>());
        }
        String[] messageRecord = {fromUser, "private", processedContent, "private",
                String.valueOf(timestamp), timeStr, "false", toUser};
        privateMessages.get(key).add(messageRecord);
        System.out.println(ANSI_PURPLE + "[" + timeStr + "] [PM -> " + toUser + "] " +
                fromColor + ANSI_BOLD + fromUser + ANSI_RESET + ANSI_PURPLE + ": " +
                processedContent + ANSI_RESET);
        // Update counts
        int currentCount = userMessageCounts.getOrDefault(fromUser, 0);
        userMessageCounts.put(fromUser, currentCount + 1);
        totalMessageCount++;
        String[] eventRecord = {"PM", fromUser, toUser, timeStr, processedContent.substring(0, Math.min(50, processedContent.length()))};
        globalEventLog.add(eventRecord);
    }

    public List<String[]> getRoomHistory(String room, int limit) {
        if (room == null || room.isEmpty()) {
            return new ArrayList<>();
        }
        List<String[]> messages = roomMessages.getOrDefault(room, new ArrayList<>());
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    public List<String[]> searchMessages(String room, String query, String searchType,
                                          String fromUser, long fromTime, long toTime) {
        List<String[]> results = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return results;
        }
        String lowerQuery = query.toLowerCase();
        List<String[]> messages;
        if (room != null && !room.isEmpty() && !room.equals("*")) {
            messages = roomMessages.getOrDefault(room, new ArrayList<>());
        } else {
            messages = new ArrayList<>();
            for (List<String[]> roomMsgs : roomMessages.values()) {
                messages.addAll(roomMsgs);
            }
        }
        for (String[] msg : messages) {
            if (msg == null || msg.length < 6) {
                continue;
            }
            String msgContent = msg[2].toLowerCase();
            String msgUser = msg[0];
            long msgTime = Long.parseLong(msg[4]);
            boolean contentMatch = false;
            boolean userMatch = true;
            boolean timeMatch = true;
            if (searchType == null || searchType.equals("contains")) {
                contentMatch = msgContent.contains(lowerQuery);
            } else if (searchType.equals("startsWith")) {
                contentMatch = msgContent.startsWith(lowerQuery);
            } else if (searchType.equals("endsWith")) {
                contentMatch = msgContent.endsWith(lowerQuery);
            } else if (searchType.equals("exact")) {
                contentMatch = msgContent.equals(lowerQuery);
            } else if (searchType.equals("regex")) {
                try {
                    contentMatch = msgContent.matches(".*" + query.toLowerCase() + ".*");
                } catch (Exception e) {
                    contentMatch = msgContent.contains(lowerQuery);
                }
            } else {
                contentMatch = msgContent.contains(lowerQuery);
            }
            if (fromUser != null && !fromUser.isEmpty() && !fromUser.equals("*")) {
                userMatch = msgUser.equalsIgnoreCase(fromUser);
            }
            if (fromTime > 0) {
                timeMatch = msgTime >= fromTime;
            }
            if (toTime > 0) {
                timeMatch = timeMatch && msgTime <= toTime;
            }
            if (contentMatch && userMatch && timeMatch) {
                results.add(msg);
            }
        }
        return results;
    }

    public List<String[]> getPrivateHistory(String user1, String user2, int limit) {
        if (user1 == null || user2 == null) {
            return new ArrayList<>();
        }
        String key = getPrivateKey(user1, user2);
        List<String[]> messages = privateMessages.getOrDefault(key, new ArrayList<>());
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    public void muteUser(String username) {
        userMuteStatus.put(username, true);
    }

    public void unmuteUser(String username) {
        userMuteStatus.put(username, false);
    }

    public List<String> getUserMentions(String username) {
        return userMentions.getOrDefault(username, new ArrayList<>());
    }

    public void clearUserMentions(String username) {
        userMentions.remove(username);
    }

    public int getUserMessageCount(String username) {
        return userMessageCounts.getOrDefault(username, 0);
    }

    public int getTotalMessageCount() {
        return totalMessageCount;
    }

    public Map<String, Integer> getAllUserMessageCounts() {
        Map<String, Integer> realCounts = new HashMap<>();
        for (Map.Entry<String, Integer> entry : userMessageCounts.entrySet()) {
            if (!entry.getKey().endsWith("_ratelimit")) {
                realCounts.put(entry.getKey(), entry.getValue());
            }
        }
        return realCounts;
    }

    public void printRoomStatistics(String room) {
        if (room == null || room.isEmpty()) {
            System.out.println(ANSI_RED + "Invalid room" + ANSI_RESET);
            return;
        }
        List<String[]> messages = roomMessages.getOrDefault(room, new ArrayList<>());
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Room Statistics: " + room + " ===" + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Total messages: " + messages.size() + ANSI_RESET);
        Map<String, Integer> perUserCount = new HashMap<>();
        int systemMessages = 0;
        int privateMessages_ = 0;
        int longMessages = 0;
        int shortMessages = 0;
        int mentionMessages = 0;
        for (String[] msg : messages) {
            if (msg == null || msg.length < 4) {
                continue;
            }
            String user = msg[0];
            String type = msg[3];
            String content = msg[2];
            if (type.equals("system") || Boolean.parseBoolean(msg[6])) {
                systemMessages++;
            } else if (type.equals("private")) {
                privateMessages_++;
            } else {
                int count = perUserCount.getOrDefault(user, 0);
                perUserCount.put(user, count + 1);
            }
            if (content.length() > 200) {
                longMessages++;
            } else if (content.length() < 10) {
                shortMessages++;
            }
            if (content.contains("@")) {
                mentionMessages++;
            }
        }
        System.out.println(ANSI_WHITE + "System messages: " + systemMessages + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Messages with mentions: " + mentionMessages + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Long messages (>200 chars): " + longMessages + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Short messages (<10 chars): " + shortMessages + ANSI_RESET);
        System.out.println(ANSI_CYAN + ANSI_BOLD + "Top contributors:" + ANSI_RESET);
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(perUserCount.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        int displayCount = Math.min(5, sorted.size());
        for (int i = 0; i < displayCount; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            String userColor = getUserColor(entry.getKey());
            System.out.println("  " + (i + 1) + ". " + userColor + ANSI_BOLD + entry.getKey() +
                    ANSI_RESET + ": " + entry.getValue() + " messages");
        }
    }

    private String getUserColor(String username) {
        if (!userColors.containsKey(username)) {
            int hash = Math.abs(username.hashCode());
            userColors.put(username, USER_COLORS[hash % USER_COLORS.length]);
        }
        return userColors.get(username);
    }

    private String getPrivateKey(String user1, String user2) {
        if (user1.compareTo(user2) < 0) {
            return user1 + ":" + user2;
        }
        return user2 + ":" + user1;
    }

    public void broadcastRoomEvent(String room, String eventType, String username, String detail) {
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        if (eventType == null || eventType.isEmpty()) {
            return;
        }
        if (eventType.equals("JOIN")) {
            String joinMsg = username + " has joined the room";
            if (detail != null && !detail.isEmpty()) {
                joinMsg = joinMsg + " (" + detail + ")";
            }
            System.out.println(ANSI_GREEN + "[" + timeStr + "] *** " + joinMsg + " ***" + ANSI_RESET);
            String[] eventRecord = {"JOIN", username, room, timeStr, detail != null ? detail : ""};
            globalEventLog.add(eventRecord);
            String[] msgRecord = {username, room, joinMsg, "system", String.valueOf(System.currentTimeMillis()), timeStr, "true", ""};
            if (!roomMessages.containsKey(room)) {
                roomMessages.put(room, new ArrayList<>());
            }
            roomMessages.get(room).add(msgRecord);
        } else if (eventType.equals("LEAVE")) {
            String leaveMsg = username + " has left the room";
            if (detail != null && !detail.isEmpty()) {
                leaveMsg = leaveMsg + " (" + detail + ")";
            }
            System.out.println(ANSI_YELLOW + "[" + timeStr + "] *** " + leaveMsg + " ***" + ANSI_RESET);
            String[] eventRecord = {"LEAVE", username, room, timeStr, detail != null ? detail : ""};
            globalEventLog.add(eventRecord);
            String[] msgRecord = {username, room, leaveMsg, "system", String.valueOf(System.currentTimeMillis()), timeStr, "true", ""};
            if (!roomMessages.containsKey(room)) {
                roomMessages.put(room, new ArrayList<>());
            }
            roomMessages.get(room).add(msgRecord);
        } else if (eventType.equals("KICKED")) {
            String kickMsg = username + " was kicked from the room";
            if (detail != null && !detail.isEmpty()) {
                kickMsg = kickMsg + ": " + detail;
            }
            System.out.println(ANSI_RED + "[" + timeStr + "] *** " + kickMsg + " ***" + ANSI_RESET);
            String[] eventRecord = {"KICKED", username, room, timeStr, detail != null ? detail : ""};
            globalEventLog.add(eventRecord);
            String[] msgRecord = {username, room, kickMsg, "system", String.valueOf(System.currentTimeMillis()), timeStr, "true", ""};
            if (!roomMessages.containsKey(room)) {
                roomMessages.put(room, new ArrayList<>());
            }
            roomMessages.get(room).add(msgRecord);
        } else if (eventType.equals("RENAMED")) {
            String renameMsg = "Room was renamed to: " + detail;
            System.out.println(ANSI_CYAN + "[" + timeStr + "] *** " + renameMsg + " ***" + ANSI_RESET);
            String[] eventRecord = {"RENAMED", username, room, timeStr, detail != null ? detail : ""};
            globalEventLog.add(eventRecord);
            String[] msgRecord = {username, room, renameMsg, "system", String.valueOf(System.currentTimeMillis()), timeStr, "true", ""};
            if (!roomMessages.containsKey(room)) {
                roomMessages.put(room, new ArrayList<>());
            }
            roomMessages.get(room).add(msgRecord);
        } else if (eventType.equals("TOPIC_CHANGED")) {
            String topicMsg = username + " changed the topic to: " + detail;
            System.out.println(ANSI_BLUE + "[" + timeStr + "] *** " + topicMsg + " ***" + ANSI_RESET);
            String[] eventRecord = {"TOPIC", username, room, timeStr, detail != null ? detail : ""};
            globalEventLog.add(eventRecord);
            String[] msgRecord = {username, room, topicMsg, "system", String.valueOf(System.currentTimeMillis()), timeStr, "true", ""};
            if (!roomMessages.containsKey(room)) {
                roomMessages.put(room, new ArrayList<>());
            }
            roomMessages.get(room).add(msgRecord);
        } else {
            System.out.println(ANSI_WHITE + "[" + timeStr + "] *** [" + eventType + "] " + username + ": " + detail + " ***" + ANSI_RESET);
            String[] eventRecord = {eventType, username, room, timeStr, detail != null ? detail : ""};
            globalEventLog.add(eventRecord);
        }
    }

    public void printMessageHistory(String room, int limit, String username) {
        List<String[]> history = getRoomHistory(room, limit);
        if (history.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No message history for room: " + room + ANSI_RESET);
            return;
        }
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Last " + history.size() + " messages in #" + room + " ===" + ANSI_RESET);
        for (String[] msg : history) {
            if (msg == null || msg.length < 7) {
                continue;
            }
            String msgUser = msg[0];
            String msgContent = msg[2];
            String msgTime = msg[5];
            boolean isSystemMsg = Boolean.parseBoolean(msg[6]);
            if (isSystemMsg) {
                System.out.println(ANSI_CYAN + "[" + msgTime + "] *** " + msgContent + " ***" + ANSI_RESET);
            } else if (msgUser.equals(username)) {
                System.out.println(ANSI_GREEN + "[" + msgTime + "] " + ANSI_BOLD + msgUser + ANSI_RESET +
                        ANSI_GREEN + ": " + msgContent + ANSI_RESET);
            } else {
                String userColor = getUserColor(msgUser);
                System.out.println(ANSI_WHITE + "[" + msgTime + "] " + userColor + ANSI_BOLD +
                        msgUser + ANSI_RESET + ANSI_WHITE + ": " + msgContent + ANSI_RESET);
            }
        }
        System.out.println(ANSI_CYAN + "=== End of history ===" + ANSI_RESET);
    }

    public void printSearchResults(List<String[]> results, String query) {
        if (results == null || results.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No results found for: " + query + ANSI_RESET);
            return;
        }
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Search results for '" + query + "' (" + results.size() + " found) ===" + ANSI_RESET);
        for (String[] msg : results) {
            if (msg == null || msg.length < 6) {
                continue;
            }
            String msgUser = msg[0];
            String msgRoom = msg[1];
            String msgContent = msg[2];
            String msgTime = msg[5];
            boolean isSystemMsg = msg.length > 6 && Boolean.parseBoolean(msg[6]);
            String userColor = getUserColor(msgUser);
            if (isSystemMsg) {
                System.out.println(ANSI_CYAN + "[" + msgTime + "] [#" + msgRoom + "] *** " + msgContent + " ***" + ANSI_RESET);
            } else {
                // Highlight the query in results
                String highlighted = msgContent.replaceAll("(?i)" + query, ANSI_YELLOW + ANSI_BOLD + "$0" + ANSI_RESET + ANSI_WHITE);
                System.out.println(ANSI_WHITE + "[" + msgTime + "] [#" + msgRoom + "] " + userColor + ANSI_BOLD +
                        msgUser + ANSI_RESET + ANSI_WHITE + ": " + highlighted + ANSI_RESET);
            }
        }
        System.out.println(ANSI_CYAN + "=== End of search results ===" + ANSI_RESET);
    }

    public void setRateLimitEnabled(boolean enabled) { this.rateLimitEnabled = enabled; }
    public void setProfanityFilterEnabled(boolean enabled) { this.profanityFilterEnabled = enabled; }
    public boolean isRateLimitEnabled() { return rateLimitEnabled; }
    public boolean isProfanityFilterEnabled() { return profanityFilterEnabled; }
    public List<String[]> getGlobalEventLog() { return globalEventLog; }
}
