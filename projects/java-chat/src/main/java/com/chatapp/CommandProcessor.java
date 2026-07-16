package com.chatapp;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles all slash-command parsing and execution.
 * Routes commands to the appropriate subsystem and returns results.
 */
public class CommandProcessor {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private ChatEngine chatEngine;
    private UserManager userManager;
    private RoomManager roomManager;
    private MessageStore messageStore;
    private Map<String, Integer> commandUsageCount;
    private List<String> commandLog;
    private Map<String, Long> lastCommandTime;
    private boolean commandLoggingEnabled;

    public CommandProcessor(ChatEngine chatEngine, UserManager userManager,
                             RoomManager roomManager, MessageStore messageStore) {
        this.chatEngine = chatEngine;
        this.userManager = userManager;
        this.roomManager = roomManager;
        this.messageStore = messageStore;
        this.commandUsageCount = new HashMap<>();
        this.commandLog = new ArrayList<>();
        this.lastCommandTime = new HashMap<>();
        this.commandLoggingEnabled = true;
    }

    // Main command dispatch - parses and routes all / commands
    public String processCommand(String input, String username, String currentRoom) {
        if (input == null || input.isEmpty() || !input.startsWith("/")) {
            return "ERROR:Not a command";
        }
        String trimmed = input.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        // Log command
        if (commandLoggingEnabled) {
            String logEntry = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) +
                    " [" + username + "] " + command;
            commandLog.add(logEntry);
            if (commandLog.size() > 500) {
                commandLog.remove(0);
            }
            int usage = commandUsageCount.getOrDefault(command, 0);
            commandUsageCount.put(command, usage + 1);
            lastCommandTime.put(command, System.currentTimeMillis());
        }
        // Route to appropriate handler
        if (command.equals("/help") || command.equals("/h") || command.equals("/?")) {
            return handleHelp(args, username, currentRoom);
        } else if (command.equals("/quit") || command.equals("/exit") || command.equals("/q")) {
            return handleQuit(args, username, currentRoom);
        } else if (command.equals("/join") || command.equals("/j")) {
            return handleJoin(args, username, currentRoom);
        } else if (command.equals("/leave") || command.equals("/part")) {
            return handleLeave(args, username, currentRoom);
        } else if (command.equals("/msg") || command.equals("/pm") || command.equals("/whisper") || command.equals("/w")) {
            return handlePrivateMessage(args, username, currentRoom);
        } else if (command.equals("/list") || command.equals("/rooms") || command.equals("/ls")) {
            return handleList(args, username, currentRoom);
        } else if (command.equals("/history") || command.equals("/hist") || command.equals("/log")) {
            return handleHistory(args, username, currentRoom);
        } else if (command.equals("/search") || command.equals("/find") || command.equals("/grep")) {
            return handleSearch(args, username, currentRoom);
        } else if (command.equals("/users") || command.equals("/who") || command.equals("/online")) {
            return handleUsers(args, username, currentRoom);
        } else if (command.equals("/create") || command.equals("/newroom") || command.equals("/mkroom")) {
            return handleCreateRoom(args, username, currentRoom);
        } else if (command.equals("/topic") || command.equals("/settopic")) {
            return handleTopic(args, username, currentRoom);
        } else if (command.equals("/status") || command.equals("/setstatus")) {
            return handleStatus(args, username, currentRoom);
        } else if (command.equals("/profile") || command.equals("/whois") || command.equals("/info")) {
            return handleProfile(args, username, currentRoom);
        } else if (command.equals("/block") || command.equals("/ignore")) {
            return handleBlock(args, username, currentRoom);
        } else if (command.equals("/unblock") || command.equals("/unignore")) {
            return handleUnblock(args, username, currentRoom);
        } else if (command.equals("/stats") || command.equals("/statistics")) {
            return handleStats(args, username, currentRoom);
        } else if (command.equals("/roominfo") || command.equals("/ri")) {
            return handleRoomInfo(args, username, currentRoom);
        } else if (command.equals("/notifications") || command.equals("/notifs") || command.equals("/alerts")) {
            return handleNotifications(args, username, currentRoom);
        } else if (command.equals("/me") || command.equals("/action")) {
            return handleMeAction(args, username, currentRoom);
        } else if (command.equals("/clear") || command.equals("/cls")) {
            return handleClear(args, username, currentRoom);
        } else if (command.equals("/bio") || command.equals("/setbio")) {
            return handleBio(args, username, currentRoom);
        } else if (command.equals("/addfriend") || command.equals("/friend")) {
            return handleAddFriend(args, username, currentRoom);
        } else if (command.equals("/mentions") || command.equals("/m")) {
            return handleMentions(args, username, currentRoom);
        } else {
            return ANSI_RED + "Unknown command: " + command + ". Type /help for a list of commands." + ANSI_RESET;
        }
    }

    private String handleHelp(String args, String username, String currentRoom) {
        StringBuilder sb = new StringBuilder();
        sb.append(ANSI_CYAN).append(ANSI_BOLD).append("=== JavaChat Commands ===").append(ANSI_RESET).append("\n");
        if (args == null || args.isEmpty()) {
            sb.append(ANSI_GREEN).append(ANSI_BOLD).append("Navigation:").append(ANSI_RESET).append("\n");
            sb.append(ANSI_WHITE).append("  /join <room>      ").append(ANSI_RESET).append("Join or switch to a room\n");
            sb.append(ANSI_WHITE).append("  /leave [room]     ").append(ANSI_RESET).append("Leave current or specified room\n");
            sb.append(ANSI_WHITE).append("  /list             ").append(ANSI_RESET).append("List all available rooms\n");
            sb.append(ANSI_GREEN).append(ANSI_BOLD).append("Messaging:").append(ANSI_RESET).append("\n");
            sb.append(ANSI_WHITE).append("  /msg <user> <msg> ").append(ANSI_RESET).append("Send a private message\n");
            sb.append(ANSI_WHITE).append("  /me <action>      ").append(ANSI_RESET).append("Perform an action (e.g. /me waves)\n");
            sb.append(ANSI_WHITE).append("  /history [n]      ").append(ANSI_RESET).append("Show last n messages (default 20)\n");
            sb.append(ANSI_WHITE).append("  /search <query>   ").append(ANSI_RESET).append("Search messages in current room\n");
            sb.append(ANSI_WHITE).append("  /mentions         ").append(ANSI_RESET).append("Show recent @mentions of you\n");
            sb.append(ANSI_GREEN).append(ANSI_BOLD).append("Users:").append(ANSI_RESET).append("\n");
            sb.append(ANSI_WHITE).append("  /users            ").append(ANSI_RESET).append("List online users\n");
            sb.append(ANSI_WHITE).append("  /profile [user]   ").append(ANSI_RESET).append("View user profile\n");
            sb.append(ANSI_WHITE).append("  /status <status>  ").append(ANSI_RESET).append("Set status (online/away/busy)\n");
            sb.append(ANSI_WHITE).append("  /bio <text>       ").append(ANSI_RESET).append("Set your bio\n");
            sb.append(ANSI_WHITE).append("  /block <user>     ").append(ANSI_RESET).append("Block a user\n");
            sb.append(ANSI_WHITE).append("  /unblock <user>   ").append(ANSI_RESET).append("Unblock a user\n");
            sb.append(ANSI_WHITE).append("  /addfriend <user> ").append(ANSI_RESET).append("Add a user to friends\n");
            sb.append(ANSI_GREEN).append(ANSI_BOLD).append("Rooms:").append(ANSI_RESET).append("\n");
            sb.append(ANSI_WHITE).append("  /create <name>    ").append(ANSI_RESET).append("Create a new room\n");
            sb.append(ANSI_WHITE).append("  /topic <text>     ").append(ANSI_RESET).append("Set room topic\n");
            sb.append(ANSI_WHITE).append("  /roominfo [room]  ").append(ANSI_RESET).append("Show room info\n");
            sb.append(ANSI_GREEN).append(ANSI_BOLD).append("Other:").append(ANSI_RESET).append("\n");
            sb.append(ANSI_WHITE).append("  /stats            ").append(ANSI_RESET).append("Show chat statistics\n");
            sb.append(ANSI_WHITE).append("  /notifications    ").append(ANSI_RESET).append("Show notifications\n");
            sb.append(ANSI_WHITE).append("  /clear            ").append(ANSI_RESET).append("Clear the screen\n");
            sb.append(ANSI_WHITE).append("  /quit             ").append(ANSI_RESET).append("Exit the chat\n");
            sb.append(ANSI_CYAN).append("Type /help <command> for detailed help on a specific command").append(ANSI_RESET);
        } else {
            String helpCmd = args.trim().toLowerCase();
            if (helpCmd.startsWith("/")) {
                helpCmd = helpCmd.substring(1);
            }
            if (helpCmd.equals("join") || helpCmd.equals("j")) {
                sb.append(ANSI_WHITE).append("Usage: /join <roomname>").append(ANSI_RESET).append("\n");
                sb.append("Join the specified room. If the room doesn't exist, you'll be prompted to create it.\n");
                sb.append("Aliases: /j\n");
                sb.append("Example: /join general");
            } else if (helpCmd.equals("msg") || helpCmd.equals("pm") || helpCmd.equals("whisper")) {
                sb.append(ANSI_WHITE).append("Usage: /msg <username> <message>").append(ANSI_RESET).append("\n");
                sb.append("Send a private message to the specified user.\n");
                sb.append("Aliases: /pm, /whisper, /w\n");
                sb.append("Example: /msg alice Hey, how are you?");
            } else if (helpCmd.equals("search") || helpCmd.equals("find")) {
                sb.append(ANSI_WHITE).append("Usage: /search <query> [--room <room>] [--user <user>]").append(ANSI_RESET).append("\n");
                sb.append("Search messages in the current room (or specified room).\n");
                sb.append("Aliases: /find, /grep\n");
                sb.append("Example: /search hello world");
            } else if (helpCmd.equals("history") || helpCmd.equals("hist")) {
                sb.append(ANSI_WHITE).append("Usage: /history [count] [--room <room>]").append(ANSI_RESET).append("\n");
                sb.append("Show message history. Default shows last 20 messages.\n");
                sb.append("Aliases: /hist, /log\n");
                sb.append("Example: /history 50");
            } else {
                sb.append(ANSI_YELLOW).append("No detailed help for: ").append(args).append(ANSI_RESET);
            }
        }
        return sb.toString();
    }

    private String handleQuit(String args, String username, String currentRoom) {
        return "QUIT";
    }

    private String handleJoin(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            return ANSI_RED + "Usage: /join <roomname>" + ANSI_RESET;
        }
        String roomName = args.trim().split("\\s+")[0].toLowerCase();
        if (roomName.isEmpty()) {
            return ANSI_RED + "Usage: /join <roomname>" + ANSI_RESET;
        }
        if (roomName.length() < 2) {
            return ANSI_RED + "Room name too short (min 2 chars)" + ANSI_RESET;
        }
        if (roomName.length() > 30) {
            return ANSI_RED + "Room name too long (max 30 chars)" + ANSI_RESET;
        }
        if (!roomName.matches("[a-zA-Z0-9_-]+")) {
            return ANSI_RED + "Room name can only contain letters, numbers, hyphens, underscores" + ANSI_RESET;
        }
        if (roomName.equals(currentRoom)) {
            return ANSI_YELLOW + "You are already in room '" + roomName + "'" + ANSI_RESET;
        }
        if (!roomManager.roomExists(roomName)) {
            System.out.print(ANSI_YELLOW + "Room '" + roomName + "' does not exist. Create it? [y/N]: " + ANSI_RESET);
            Scanner sc = new Scanner(System.in);
            String answer = sc.nextLine().trim();
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                boolean created = roomManager.createRoom(roomName, username, false, false, "");
                if (!created) {
                    return ANSI_RED + "Failed to create room '" + roomName + "'" + ANSI_RESET;
                }
                System.out.println(ANSI_GREEN + "Room '" + roomName + "' created!" + ANSI_RESET);
            } else {
                return ANSI_YELLOW + "Join cancelled." + ANSI_RESET;
            }
        }
        // Leave current room
        if (currentRoom != null && !currentRoom.isEmpty()) {
            roomManager.leaveRoom(username, currentRoom);
            chatEngine.broadcastRoomEvent(currentRoom, "LEAVE", username, null);
        }
        boolean joined = roomManager.joinRoom(username, roomName);
        if (!joined) {
            return ANSI_RED + "Failed to join room '" + roomName + "'" + ANSI_RESET;
        }
        chatEngine.broadcastRoomEvent(roomName, "JOIN", username, null);
        userManager.updateLastActive(username);
        return "SWITCH_ROOM:" + roomName;
    }

    private String handleLeave(String args, String username, String currentRoom) {
        String roomToLeave = currentRoom;
        if (args != null && !args.trim().isEmpty()) {
            roomToLeave = args.trim().split("\\s+")[0].toLowerCase();
        }
        if (roomToLeave == null || roomToLeave.isEmpty()) {
            return ANSI_RED + "You are not in any room" + ANSI_RESET;
        }
        if (!roomManager.isInRoom(username, roomToLeave)) {
            return ANSI_RED + "You are not in room '" + roomToLeave + "'" + ANSI_RESET;
        }
        roomManager.leaveRoom(username, roomToLeave);
        chatEngine.broadcastRoomEvent(roomToLeave, "LEAVE", username, null);
        userManager.updateLastActive(username);
        // Join general if we left current room
        if (roomToLeave.equals(currentRoom)) {
            String fallback = "general";
            if (!roomToLeave.equals(fallback)) {
                roomManager.joinRoom(username, fallback);
                chatEngine.broadcastRoomEvent(fallback, "JOIN", username, "from " + roomToLeave);
                return "SWITCH_ROOM:" + fallback;
            }
        }
        return ANSI_GREEN + "Left room: " + roomToLeave + ANSI_RESET;
    }

    private String handlePrivateMessage(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            return ANSI_RED + "Usage: /msg <username> <message>" + ANSI_RESET;
        }
        String[] parts = args.trim().split("\\s+", 2);
        if (parts.length < 2) {
            return ANSI_RED + "Usage: /msg <username> <message>" + ANSI_RESET;
        }
        String targetUser = parts[0];
        String message = parts[1];
        if (targetUser.isEmpty()) {
            return ANSI_RED + "Username cannot be empty" + ANSI_RESET;
        }
        if (message.isEmpty()) {
            return ANSI_RED + "Message cannot be empty" + ANSI_RESET;
        }
        if (message.length() > 500) {
            return ANSI_RED + "Message too long (max 500 chars)" + ANSI_RESET;
        }
        if (targetUser.equals(username)) {
            return ANSI_RED + "Cannot send private message to yourself" + ANSI_RESET;
        }
        if (!userManager.isUsernameTaken(targetUser)) {
            return ANSI_RED + "User '" + targetUser + "' not found" + ANSI_RESET;
        }
        if (!userManager.isUserOnline(targetUser)) {
            return ANSI_YELLOW + "User '" + targetUser + "' appears to be offline. Message may not be seen immediately." + ANSI_RESET;
        }
        if (userManager.isBlocked(targetUser, username)) {
            return ANSI_RED + "You have been blocked by " + targetUser + ANSI_RESET;
        }
        if (userManager.isBlocked(username, targetUser)) {
            return ANSI_RED + "You have blocked " + targetUser + ". Unblock them first." + ANSI_RESET;
        }
        chatEngine.sendPrivateMessage(username, targetUser, message, System.currentTimeMillis());
        messageStore.storePrivateMessage(username, targetUser, message, System.currentTimeMillis());
        userManager.addNotification(targetUser, "Private message from " + username);
        userManager.updateLastActive(username);
        return "";
    }

    private String handleList(String args, String username, String currentRoom) {
        roomManager.printRoomList(username);
        return "";
    }

    private String handleHistory(String args, String username, String currentRoom) {
        int limit = 20;
        String room = currentRoom;
        if (args != null && !args.trim().isEmpty()) {
            String[] parts = args.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("--room") || parts[i].equals("-r")) {
                    if (i + 1 < parts.length) {
                        room = parts[++i];
                    }
                } else {
                    try {
                        limit = Integer.parseInt(parts[i]);
                        if (limit < 1) limit = 1;
                        if (limit > 200) limit = 200;
                    } catch (NumberFormatException e) {
                        return ANSI_RED + "Invalid count: " + parts[i] + ANSI_RESET;
                    }
                }
            }
        }
        if (!roomManager.roomExists(room)) {
            return ANSI_RED + "Room not found: " + room + ANSI_RESET;
        }
        chatEngine.printMessageHistory(room, limit, username);
        userManager.updateLastActive(username);
        return "";
    }

    private String handleSearch(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            return ANSI_RED + "Usage: /search <query> [--room <room>] [--user <user>] [--type <type>]" + ANSI_RESET;
        }
        String query = null;
        String room = currentRoom;
        String searchUser = null;
        String searchType = "contains";
        String[] parts = args.trim().split("\\s+");
        List<String> queryParts = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("--room") || parts[i].equals("-r")) {
                if (i + 1 < parts.length) {
                    room = parts[++i];
                } else {
                    return ANSI_RED + "--room requires a room argument" + ANSI_RESET;
                }
            } else if (parts[i].equals("--user") || parts[i].equals("-u")) {
                if (i + 1 < parts.length) {
                    searchUser = parts[++i];
                } else {
                    return ANSI_RED + "--user requires a username argument" + ANSI_RESET;
                }
            } else if (parts[i].equals("--type") || parts[i].equals("-t")) {
                if (i + 1 < parts.length) {
                    searchType = parts[++i];
                    if (!searchType.equals("contains") && !searchType.equals("startsWith") &&
                            !searchType.equals("endsWith") && !searchType.equals("exact") && !searchType.equals("regex")) {
                        return ANSI_RED + "Invalid search type. Use: contains/startsWith/endsWith/exact/regex" + ANSI_RESET;
                    }
                } else {
                    return ANSI_RED + "--type requires a type argument" + ANSI_RESET;
                }
            } else if (parts[i].equals("--all") || parts[i].equals("-a")) {
                room = "*";
            } else {
                queryParts.add(parts[i]);
            }
        }
        if (queryParts.isEmpty()) {
            return ANSI_RED + "Usage: /search <query>" + ANSI_RESET;
        }
        query = String.join(" ", queryParts);
        if (query.length() < 2) {
            return ANSI_RED + "Search query too short (min 2 chars)" + ANSI_RESET;
        }
        if (query.length() > 100) {
            return ANSI_RED + "Search query too long (max 100 chars)" + ANSI_RESET;
        }
        List<String[]> results = chatEngine.searchMessages(room, query, searchType, searchUser, 0, 0);
        chatEngine.printSearchResults(results, query);
        userManager.updateLastActive(username);
        return "";
    }

    private String handleUsers(String args, String username, String currentRoom) {
        List<String> onlineUsers = userManager.getOnlineUsers();
        Set<String> roomMembers = roomManager.getRoomMembers(currentRoom);
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Online Users ===" + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Total online: " + onlineUsers.size() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "In this room (" + currentRoom + "): " + roomMembers.size() + ANSI_RESET);
        System.out.println(ANSI_CYAN + ANSI_BOLD + "In #" + currentRoom + ":" + ANSI_RESET);
        for (String member : roomMembers) {
            String status = userManager.getUserStatus(member);
            String statusColor;
            String statusIcon;
            if (status.equals("online")) {
                statusColor = ANSI_GREEN;
                statusIcon = "●";
            } else if (status.equals("away")) {
                statusColor = ANSI_YELLOW;
                statusIcon = "◑";
            } else if (status.equals("busy")) {
                statusColor = ANSI_RED;
                statusIcon = "○";
            } else {
                statusColor = ANSI_WHITE;
                statusIcon = "○";
            }
            String marker = member.equals(username) ? " (you)" : "";
            System.out.println("  " + statusColor + statusIcon + " " + ANSI_BOLD + member + ANSI_RESET + marker);
        }
        if (args != null && args.contains("--all")) {
            System.out.println(ANSI_CYAN + ANSI_BOLD + "All online users:" + ANSI_RESET);
            for (String user : onlineUsers) {
                if (!roomMembers.contains(user)) {
                    String status = userManager.getUserStatus(user);
                    String statusColor = status.equals("online") ? ANSI_GREEN : status.equals("away") ? ANSI_YELLOW : ANSI_RED;
                    System.out.println("  " + statusColor + "● " + ANSI_BOLD + user + ANSI_RESET);
                }
            }
        }
        userManager.updateLastActive(username);
        return "";
    }

    private String handleCreateRoom(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            return ANSI_RED + "Usage: /create <roomname> [--private] [--readonly] [--topic <text>]" + ANSI_RESET;
        }
        String[] parts = args.trim().split("\\s+");
        String roomName = parts[0].toLowerCase();
        boolean isPrivate = false;
        boolean isReadOnly = false;
        String topic = "";
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equals("--private") || parts[i].equals("-p")) {
                isPrivate = true;
            } else if (parts[i].equals("--readonly") || parts[i].equals("-ro")) {
                isReadOnly = true;
            } else if (parts[i].equals("--topic") || parts[i].equals("-t")) {
                if (i + 1 < parts.length) {
                    StringBuilder topicBuilder = new StringBuilder();
                    for (int j = i + 1; j < parts.length; j++) {
                        if (topicBuilder.length() > 0) topicBuilder.append(" ");
                        topicBuilder.append(parts[j]);
                    }
                    topic = topicBuilder.toString();
                    break;
                }
            }
        }
        if (roomName.length() < 2 || roomName.length() > 30) {
            return ANSI_RED + "Room name must be 2-30 characters" + ANSI_RESET;
        }
        if (!roomName.matches("[a-zA-Z0-9_-]+")) {
            return ANSI_RED + "Room name can only contain letters, numbers, hyphens, underscores" + ANSI_RESET;
        }
        boolean created = roomManager.createRoom(roomName, username, isPrivate, isReadOnly, topic);
        if (!created) {
            return ANSI_RED + "Failed to create room '" + roomName + "'" + ANSI_RESET;
        }
        roomManager.joinRoom(username, roomName);
        chatEngine.broadcastRoomEvent(roomName, "JOIN", username, "room created");
        userManager.updateLastActive(username);
        System.out.println(ANSI_GREEN + "Room '" + roomName + "' created successfully!" + ANSI_RESET);
        return "SWITCH_ROOM:" + roomName;
    }

    private String handleTopic(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            Map<String, String> info = roomManager.getRoomInfo(currentRoom);
            if (info == null) {
                return ANSI_RED + "Room not found" + ANSI_RESET;
            }
            String topic = info.getOrDefault("topic", "");
            if (topic.isEmpty()) {
                return ANSI_YELLOW + "No topic set for #" + currentRoom + ANSI_RESET;
            }
            return ANSI_CYAN + "Topic for #" + currentRoom + ": " + topic + ANSI_RESET;
        }
        if (args.length() > 200) {
            return ANSI_RED + "Topic too long (max 200 chars)" + ANSI_RESET;
        }
        boolean success = roomManager.setTopic(currentRoom, args.trim(), username);
        if (success) {
            chatEngine.broadcastRoomEvent(currentRoom, "TOPIC_CHANGED", username, args.trim());
            userManager.updateLastActive(username);
            return ANSI_GREEN + "Topic updated for #" + currentRoom + ANSI_RESET;
        }
        return ANSI_RED + "Failed to set topic" + ANSI_RESET;
    }

    private String handleStatus(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            String current = userManager.getUserStatus(username);
            return ANSI_CYAN + "Your current status: " + current + ANSI_RESET;
        }
        String[] parts = args.trim().split("\\s+", 2);
        String newStatus = parts[0].toLowerCase();
        String message = parts.length > 1 ? parts[1] : null;
        userManager.setUserStatus(username, newStatus);
        if (message != null && !message.isEmpty()) {
            userManager.setStatusMessage(username, message);
        }
        userManager.updateLastActive(username);
        return "";
    }

    private String handleProfile(String args, String username, String currentRoom) {
        String targetUser = username;
        if (args != null && !args.trim().isEmpty()) {
            targetUser = args.trim().split("\\s+")[0];
        }
        userManager.printUserProfile(targetUser, username);
        userManager.updateLastActive(username);
        return "";
    }

    private String handleBlock(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            return ANSI_RED + "Usage: /block <username>" + ANSI_RESET;
        }
        String targetUser = args.trim().split("\\s+")[0];
        userManager.blockUser(username, targetUser);
        userManager.updateLastActive(username);
        return "";
    }

    private String handleUnblock(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            return ANSI_RED + "Usage: /unblock <username>" + ANSI_RESET;
        }
        String targetUser = args.trim().split("\\s+")[0];
        userManager.unblockUser(username, targetUser);
        userManager.updateLastActive(username);
        return "";
    }

    private String handleStats(String args, String username, String currentRoom) {
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Chat Statistics ===" + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Total messages sent: " + chatEngine.getTotalMessageCount() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Total rooms: " + roomManager.getRoomCount() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Online users: " + userManager.getOnlineCount() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Registered users: " + userManager.getUserCount() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Your messages: " + chatEngine.getUserMessageCount(username) + ANSI_RESET);
        Map<String, Integer> allCounts = chatEngine.getAllUserMessageCounts();
        if (!allCounts.isEmpty()) {
            System.out.println(ANSI_CYAN + ANSI_BOLD + "Top message senders:" + ANSI_RESET);
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(allCounts.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            int display = Math.min(5, sorted.size());
            for (int i = 0; i < display; i++) {
                System.out.println("  " + (i + 1) + ". " + sorted.get(i).getKey() + ": " + sorted.get(i).getValue() + " messages");
            }
        }
        messageStore.printStorageStats();
        System.out.println(ANSI_CYAN + ANSI_BOLD + "Command usage:" + ANSI_RESET);
        List<Map.Entry<String, Integer>> cmdSorted = new ArrayList<>(commandUsageCount.entrySet());
        cmdSorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        int cmdDisplay = Math.min(5, cmdSorted.size());
        for (int i = 0; i < cmdDisplay; i++) {
            System.out.println("  " + cmdSorted.get(i).getKey() + ": " + cmdSorted.get(i).getValue() + " uses");
        }
        userManager.updateLastActive(username);
        return "";
    }

    private String handleRoomInfo(String args, String username, String currentRoom) {
        String room = currentRoom;
        if (args != null && !args.trim().isEmpty()) {
            room = args.trim().split("\\s+")[0].toLowerCase();
        }
        if (!roomManager.roomExists(room)) {
            return ANSI_RED + "Room not found: " + room + ANSI_RESET;
        }
        Map<String, String> info = roomManager.getRoomInfo(room);
        Set<String> members = roomManager.getRoomMembers(room);
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Room Info: #" + room + " ===" + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Members: " + members.size() + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Created: " + info.getOrDefault("created", "unknown") + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Creator: " + info.getOrDefault("creator", "unknown") + ANSI_RESET);
        String topic = info.getOrDefault("topic", "");
        if (!topic.isEmpty()) {
            System.out.println(ANSI_WHITE + "Topic: " + topic + ANSI_RESET);
        }
        boolean isPrivate = Boolean.parseBoolean(info.getOrDefault("isPrivate", "false"));
        boolean isReadOnly = Boolean.parseBoolean(info.getOrDefault("isReadOnly", "false"));
        System.out.println(ANSI_WHITE + "Private: " + (isPrivate ? ANSI_YELLOW + "yes" : ANSI_GREEN + "no") + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Read-only: " + (isReadOnly ? ANSI_YELLOW + "yes" : ANSI_GREEN + "no") + ANSI_RESET);
        chatEngine.printRoomStatistics(room);
        userManager.updateLastActive(username);
        return "";
    }

    private String handleNotifications(String args, String username, String currentRoom) {
        List<String> notifs = userManager.getNotifications(username);
        if (notifs.isEmpty()) {
            return ANSI_YELLOW + "No notifications" + ANSI_RESET;
        }
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Notifications (" + notifs.size() + ") ===" + ANSI_RESET);
        for (String notif : notifs) {
            System.out.println(ANSI_WHITE + "  • " + notif + ANSI_RESET);
        }
        if (args != null && args.trim().equals("--clear")) {
            userManager.clearNotifications(username);
            System.out.println(ANSI_GREEN + "Notifications cleared" + ANSI_RESET);
        }
        int unread = messageStore.getUnreadCount(username);
        if (unread > 0) {
            System.out.println(ANSI_YELLOW + "You have " + unread + " unread private message(s)" + ANSI_RESET);
        }
        userManager.updateLastActive(username);
        return "";
    }

    private String handleMeAction(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            return ANSI_RED + "Usage: /me <action>" + ANSI_RESET;
        }
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String actionText = username + " " + args.trim();
        System.out.println(ANSI_PURPLE + "[" + timeStr + "] * " + ANSI_BOLD + actionText + ANSI_RESET);
        chatEngine.sendMessage(username, currentRoom, "* " + actionText, "action",
                System.currentTimeMillis(), false, null, 0);
        userManager.updateLastActive(username);
        return "";
    }

    private String handleClear(String args, String username, String currentRoom) {
        // ANSI escape to clear screen
        System.out.print("\033[H\033[2J");
        System.out.flush();
        System.out.println(ANSI_CYAN + "Screen cleared. You are in #" + currentRoom + ANSI_RESET);
        return "";
    }

    private String handleBio(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            Map<String, String> profile = userManager.getUserProfile(username);
            if (profile == null) {
                return ANSI_RED + "Profile not found" + ANSI_RESET;
            }
            String bio = profile.getOrDefault("bio", "");
            if (bio.isEmpty()) {
                return ANSI_YELLOW + "You have not set a bio yet. Use /bio <text> to set one." + ANSI_RESET;
            }
            return ANSI_CYAN + "Your bio: " + bio + ANSI_RESET;
        }
        if (args.trim().length() > 200) {
            return ANSI_RED + "Bio too long (max 200 chars)" + ANSI_RESET;
        }
        userManager.updateBio(username, args.trim());
        userManager.updateLastActive(username);
        return "";
    }

    private String handleAddFriend(String args, String username, String currentRoom) {
        if (args == null || args.trim().isEmpty()) {
            return ANSI_RED + "Usage: /addfriend <username>" + ANSI_RESET;
        }
        String targetUser = args.trim().split("\\s+")[0];
        if (targetUser.isEmpty()) {
            return ANSI_RED + "Username cannot be empty" + ANSI_RESET;
        }
        if (targetUser.equals(username)) {
            return ANSI_RED + "Cannot add yourself as a friend" + ANSI_RESET;
        }
        if (!userManager.isUsernameTaken(targetUser)) {
            return ANSI_RED + "User '" + targetUser + "' not found" + ANSI_RESET;
        }
        userManager.addFriend(username, targetUser);
        userManager.addNotification(targetUser, username + " added you as a friend");
        userManager.updateLastActive(username);
        return "";
    }

    private String handleMentions(String args, String username, String currentRoom) {
        List<String> mentions = chatEngine.getUserMentions(username);
        if (mentions.isEmpty()) {
            return ANSI_YELLOW + "No recent mentions" + ANSI_RESET;
        }
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Recent Mentions ===" + ANSI_RESET);
        for (String mention : mentions) {
            System.out.println(ANSI_YELLOW + "  • " + mention + ANSI_RESET);
        }
        if (args != null && args.trim().equals("--clear")) {
            chatEngine.clearUserMentions(username);
            System.out.println(ANSI_GREEN + "Mentions cleared" + ANSI_RESET);
        }
        userManager.updateLastActive(username);
        return "";
    }

    public List<String> getCommandLog() {
        return commandLog;
    }

    public Map<String, Integer> getCommandUsageCount() {
        return commandUsageCount;
    }
}
