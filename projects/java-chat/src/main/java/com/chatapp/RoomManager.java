package com.chatapp;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages chat rooms: creation, membership, topics, and access control.
 */
public class RoomManager {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private static final int MAX_ROOMS = 100;
    private static final int MAX_ROOM_NAME_LENGTH = 30;
    private static final int MIN_ROOM_NAME_LENGTH = 2;
    private static final int MAX_MEMBERS_PER_ROOM = 500;
    private static final int MAX_TOPIC_LENGTH = 200;

    // room metadata: roomName -> field map
    private Map<String, Map<String, String>> rooms;
    // room members: roomName -> set of usernames
    private Map<String, Set<String>> roomMembers;
    // room admins: roomName -> set of admin usernames
    private Map<String, Set<String>> roomAdmins;
    // room bans: roomName -> set of banned usernames
    private Map<String, Set<String>> roomBans;
    // room invites: roomName -> set of invited usernames
    private Map<String, Set<String>> roomInvites;
    // user's current rooms
    private Map<String, Set<String>> userRooms;
    // room join history: roomName -> list of join events
    private Map<String, List<String>> roomJoinHistory;
    // room message counts (cached)
    private Map<String, Integer> roomMessageCounts;
    // pinned messages per room
    private Map<String, List<String>> pinnedMessages;
    private Map<String, Long> roomLastActivity;

    public RoomManager() {
        this.rooms = new HashMap<>();
        this.roomMembers = new HashMap<>();
        this.roomAdmins = new HashMap<>();
        this.roomBans = new HashMap<>();
        this.roomInvites = new HashMap<>();
        this.userRooms = new HashMap<>();
        this.roomJoinHistory = new HashMap<>();
        this.roomMessageCounts = new HashMap<>();
        this.pinnedMessages = new HashMap<>();
        this.roomLastActivity = new HashMap<>();

        // Create default rooms
        createDefaultRooms();
    }

    private void createDefaultRooms() {
        createRoom("general", "system", false, false, "General chat room for everyone");
        createRoom("random", "system", false, false, "Random discussions and off-topic chat");
        createRoom("announcements", "system", false, true, "Important announcements");
        createRoom("help", "system", false, false, "Get help with the chat system");
        createRoom("tech", "system", false, false, "Technology discussions");
        createRoom("gaming", "system", false, false, "Gaming related chat");
        createRoom("music", "system", false, false, "Music lovers unite");
    }

    public boolean createRoom(String roomName, String creatorUsername, boolean isPrivate,
                               boolean isReadOnly, String topic) {
        if (roomName == null || roomName.isEmpty()) {
            System.out.println(ANSI_RED + "Error: Room name cannot be empty" + ANSI_RESET);
            return false;
        }
        if (roomName.length() < MIN_ROOM_NAME_LENGTH) {
            System.out.println(ANSI_RED + "Error: Room name too short (min " + MIN_ROOM_NAME_LENGTH + " chars)" + ANSI_RESET);
            return false;
        }
        if (roomName.length() > MAX_ROOM_NAME_LENGTH) {
            System.out.println(ANSI_RED + "Error: Room name too long (max " + MAX_ROOM_NAME_LENGTH + " chars)" + ANSI_RESET);
            return false;
        }
        if (!roomName.matches("[a-zA-Z0-9_-]+")) {
            System.out.println(ANSI_RED + "Error: Room name can only contain letters, numbers, hyphens, and underscores" + ANSI_RESET);
            return false;
        }
        if (rooms.containsKey(roomName)) {
            System.out.println(ANSI_YELLOW + "Room '" + roomName + "' already exists" + ANSI_RESET);
            return false;
        }
        if (rooms.size() >= MAX_ROOMS) {
            System.out.println(ANSI_RED + "Error: Maximum room limit reached" + ANSI_RESET);
            return false;
        }
        Map<String, String> roomInfo = new HashMap<>();
        roomInfo.put("name", roomName);
        roomInfo.put("creator", creatorUsername);
        roomInfo.put("created", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        roomInfo.put("isPrivate", String.valueOf(isPrivate));
        roomInfo.put("isReadOnly", String.valueOf(isReadOnly));
        roomInfo.put("topic", topic != null ? topic : "");
        roomInfo.put("description", "");
        roomInfo.put("memberCount", "0");
        rooms.put(roomName, roomInfo);
        roomMembers.put(roomName, new HashSet<>());
        roomAdmins.put(roomName, new HashSet<>());
        roomBans.put(roomName, new HashSet<>());
        roomInvites.put(roomName, new HashSet<>());
        roomJoinHistory.put(roomName, new ArrayList<>());
        roomMessageCounts.put(roomName, 0);
        pinnedMessages.put(roomName, new ArrayList<>());
        if (!creatorUsername.equals("system")) {
            roomAdmins.get(roomName).add(creatorUsername);
        }
        return true;
    }

    public void createRoomIfNotExists(String roomName) {
        if (roomName == null || roomName.isEmpty()) {
            return;
        }
        if (!rooms.containsKey(roomName)) {
            createRoom(roomName, "system", false, false, "");
        }
    }

    public boolean joinRoom(String username, String roomName) {
        if (username == null || roomName == null) {
            return false;
        }
        if (!rooms.containsKey(roomName)) {
            System.out.println(ANSI_RED + "Room '" + roomName + "' does not exist" + ANSI_RESET);
            return false;
        }
        if (roomBans.containsKey(roomName) && roomBans.get(roomName).contains(username)) {
            System.out.println(ANSI_RED + "You are banned from room '" + roomName + "'" + ANSI_RESET);
            return false;
        }
        Map<String, String> roomInfo = rooms.get(roomName);
        boolean isPrivate = Boolean.parseBoolean(roomInfo.getOrDefault("isPrivate", "false"));
        if (isPrivate) {
            Set<String> invited = roomInvites.getOrDefault(roomName, new HashSet<>());
            Set<String> admins = roomAdmins.getOrDefault(roomName, new HashSet<>());
            if (!invited.contains(username) && !admins.contains(username)) {
                System.out.println(ANSI_RED + "Room '" + roomName + "' is private. You need an invite." + ANSI_RESET);
                return false;
            }
        }
        Set<String> members = roomMembers.get(roomName);
        if (members.size() >= MAX_MEMBERS_PER_ROOM) {
            System.out.println(ANSI_RED + "Room '" + roomName + "' is full (max " + MAX_MEMBERS_PER_ROOM + " members)" + ANSI_RESET);
            return false;
        }
        boolean wasAlreadyMember = members.contains(username);
        members.add(username);
        roomInfo.put("memberCount", String.valueOf(members.size()));
        // Track user's rooms
        if (!userRooms.containsKey(username)) {
            userRooms.put(username, new HashSet<>());
        }
        userRooms.get(username).add(roomName);
        // Log join history
        String joinTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        roomJoinHistory.get(roomName).add(username + " joined at " + joinTime);
        if (roomJoinHistory.get(roomName).size() > 200) {
            roomJoinHistory.get(roomName).remove(0);
        }
        if (!wasAlreadyMember) {
            String topic = roomInfo.getOrDefault("topic", "");
            if (!topic.isEmpty()) {
                System.out.println(ANSI_CYAN + "Topic: " + topic + ANSI_RESET);
            }
        }
        return true;
    }

    public boolean leaveRoom(String username, String roomName) {
        if (username == null || roomName == null) {
            return false;
        }
        if (!rooms.containsKey(roomName)) {
            return false;
        }
        Set<String> members = roomMembers.getOrDefault(roomName, new HashSet<>());
        boolean removed = members.remove(username);
        if (removed) {
            rooms.get(roomName).put("memberCount", String.valueOf(members.size()));
        }
        if (userRooms.containsKey(username)) {
            userRooms.get(username).remove(roomName);
        }
        return removed;
    }

    public boolean isInRoom(String username, String roomName) {
        Set<String> members = roomMembers.getOrDefault(roomName, new HashSet<>());
        return members.contains(username);
    }

    public Set<String> getRoomMembers(String roomName) {
        return roomMembers.getOrDefault(roomName, new HashSet<>());
    }

    public Set<String> getUserRooms(String username) {
        return userRooms.getOrDefault(username, new HashSet<>());
    }

    public List<String> getAllRooms() {
        List<String> roomList = new ArrayList<>(rooms.keySet());
        Collections.sort(roomList);
        return roomList;
    }

    public List<String> getPublicRooms() {
        List<String> publicRooms = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : rooms.entrySet()) {
            if (!"true".equals(entry.getValue().get("isPrivate"))) {
                publicRooms.add(entry.getKey());
            }
        }
        Collections.sort(publicRooms);
        return publicRooms;
    }

    public void printRoomList(String username) {
        List<String> allRooms = getAllRooms();
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Available Rooms ===" + ANSI_RESET);
        Set<String> myRooms = userRooms.getOrDefault(username, new HashSet<>());
        for (String room : allRooms) {
            Map<String, String> info = rooms.get(room);
            if (info == null) continue;
            boolean isPrivate = Boolean.parseBoolean(info.getOrDefault("isPrivate", "false"));
            boolean isReadOnly = Boolean.parseBoolean(info.getOrDefault("isReadOnly", "false"));
            boolean isMember = myRooms.contains(room);
            int memberCount = roomMembers.getOrDefault(room, new HashSet<>()).size();
            String topic = info.getOrDefault("topic", "");
            String roomColor = ANSI_WHITE;
            if (isMember) {
                roomColor = ANSI_GREEN;
            } else if (isPrivate) {
                roomColor = ANSI_PURPLE;
            }
            String flags = "";
            if (isPrivate) flags += "[private] ";
            if (isReadOnly) flags += "[read-only] ";
            if (isMember) flags += "[joined] ";
            System.out.print(roomColor + "  #" + ANSI_BOLD + room + ANSI_RESET + roomColor +
                    " (" + memberCount + " members) " + flags + ANSI_RESET);
            if (!topic.isEmpty()) {
                System.out.print(ANSI_WHITE + "- " + topic + ANSI_RESET);
            }
            System.out.println();
        }
        System.out.println(ANSI_CYAN + "Total: " + allRooms.size() + " rooms" + ANSI_RESET);
    }

    public boolean setTopic(String roomName, String topic, String requestingUser) {
        if (!rooms.containsKey(roomName)) {
            System.out.println(ANSI_RED + "Room not found: " + roomName + ANSI_RESET);
            return false;
        }
        if (topic != null && topic.length() > MAX_TOPIC_LENGTH) {
            System.out.println(ANSI_RED + "Topic too long (max " + MAX_TOPIC_LENGTH + " chars)" + ANSI_RESET);
            return false;
        }
        Set<String> admins = roomAdmins.getOrDefault(roomName, new HashSet<>());
        if (!admins.isEmpty() && !admins.contains(requestingUser)) {
            System.out.println(ANSI_RED + "Only room admins can change the topic" + ANSI_RESET);
            return false;
        }
        rooms.get(roomName).put("topic", topic != null ? topic : "");
        return true;
    }

    public boolean banFromRoom(String roomName, String targetUser, String requestingUser) {
        if (!rooms.containsKey(roomName)) {
            return false;
        }
        Set<String> admins = roomAdmins.getOrDefault(roomName, new HashSet<>());
        if (!admins.isEmpty() && !admins.contains(requestingUser)) {
            System.out.println(ANSI_RED + "Only room admins can ban users" + ANSI_RESET);
            return false;
        }
        if (!roomBans.containsKey(roomName)) {
            roomBans.put(roomName, new HashSet<>());
        }
        roomBans.get(roomName).add(targetUser);
        leaveRoom(targetUser, roomName);
        return true;
    }

    public boolean inviteToRoom(String roomName, String targetUser, String invitingUser) {
        if (!rooms.containsKey(roomName)) {
            return false;
        }
        Map<String, String> info = rooms.get(roomName);
        boolean isPrivate = Boolean.parseBoolean(info.getOrDefault("isPrivate", "false"));
        if (!isPrivate) {
            System.out.println(ANSI_YELLOW + "Room is public, invite not needed" + ANSI_RESET);
            return false;
        }
        Set<String> admins = roomAdmins.getOrDefault(roomName, new HashSet<>());
        if (!admins.isEmpty() && !admins.contains(invitingUser)) {
            System.out.println(ANSI_RED + "Only room admins can invite to private rooms" + ANSI_RESET);
            return false;
        }
        if (!roomInvites.containsKey(roomName)) {
            roomInvites.put(roomName, new HashSet<>());
        }
        roomInvites.get(roomName).add(targetUser);
        return true;
    }

    public void pinMessage(String roomName, String messageId, String requestingUser) {
        if (!rooms.containsKey(roomName)) {
            System.out.println(ANSI_RED + "Room not found: " + roomName + ANSI_RESET);
            return;
        }
        Set<String> admins = roomAdmins.getOrDefault(roomName, new HashSet<>());
        if (!admins.isEmpty() && !admins.contains(requestingUser)) {
            System.out.println(ANSI_RED + "Only room admins can pin messages" + ANSI_RESET);
            return;
        }
        if (!pinnedMessages.containsKey(roomName)) {
            pinnedMessages.put(roomName, new ArrayList<>());
        }
        List<String> pinned = pinnedMessages.get(roomName);
        if (pinned.contains(messageId)) {
            System.out.println(ANSI_YELLOW + "Message already pinned" + ANSI_RESET);
            return;
        }
        if (pinned.size() >= 5) {
            pinned.remove(0);
        }
        pinned.add(messageId);
        System.out.println(ANSI_GREEN + "Message pinned successfully" + ANSI_RESET);
    }

    public boolean roomExists(String roomName) {
        return rooms.containsKey(roomName);
    }

    public Map<String, String> getRoomInfo(String roomName) {
        return rooms.getOrDefault(roomName, null);
    }

    public void incrementRoomMessageCount(String roomName) {
        int current = roomMessageCounts.getOrDefault(roomName, 0);
        roomMessageCounts.put(roomName, current + 1);
        roomLastActivity.put(roomName, System.currentTimeMillis());
    }

    public int getRoomCount() {
        return rooms.size();
    }

    // Search and filter rooms by various criteria - used by /list and /search
    public List<String> searchRooms(String query, String filterType, String requestingUser,
                                     boolean showPrivate, int maxResults) {
        List<String> results = new ArrayList<>();
        if (maxResults <= 0) maxResults = 50;
        if (query == null) query = "";
        String lowerQuery = query.toLowerCase();
        for (Map.Entry<String, Map<String, String>> entry : rooms.entrySet()) {
            if (results.size() >= maxResults) break;
            String roomName = entry.getKey();
            Map<String, String> info = entry.getValue();
            if (info == null) continue;
            boolean isPrivate = Boolean.parseBoolean(info.getOrDefault("isPrivate", "false"));
            if (isPrivate && !showPrivate) {
                Set<String> invites = roomInvites.getOrDefault(roomName, new HashSet<>());
                Set<String> admins = roomAdmins.getOrDefault(roomName, new HashSet<>());
                if (!invites.contains(requestingUser) && !admins.contains(requestingUser)) {
                    continue;
                }
            }
            if (!query.isEmpty()) {
                boolean nameMatch = roomName.toLowerCase().contains(lowerQuery);
                boolean topicMatch = info.getOrDefault("topic", "").toLowerCase().contains(lowerQuery);
                boolean descMatch = info.getOrDefault("description", "").toLowerCase().contains(lowerQuery);
                if (!nameMatch && !topicMatch && !descMatch) {
                    continue;
                }
            }
            if (filterType != null && !filterType.isEmpty()) {
                if (filterType.equals("private") && !isPrivate) continue;
                if (filterType.equals("public") && isPrivate) continue;
                if (filterType.equals("readonly") && !Boolean.parseBoolean(info.getOrDefault("isReadOnly", "false"))) continue;
                if (filterType.equals("active")) {
                    long lastActivity = roomLastActivity.getOrDefault(roomName, 0L);
                    long fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000;
                    if (lastActivity < fiveMinutesAgo) continue;
                }
                if (filterType.equals("joined")) {
                    Set<String> userRoomSet = userRooms.getOrDefault(requestingUser, new HashSet<>());
                    if (!userRoomSet.contains(roomName)) continue;
                }
                if (filterType.equals("empty")) {
                    Set<String> members = roomMembers.getOrDefault(roomName, new HashSet<>());
                    if (!members.isEmpty()) continue;
                }
            }
            results.add(roomName);
        }
        Collections.sort(results);
        return results;
    }

    // Generate room statistics report - used by /roominfo --stats
    public String generateRoomReport(String roomName, String reportType, String requestingUser, boolean detailed) {
        if (roomName == null || roomName.isEmpty()) {
            return ANSI_RED + "Room name required" + ANSI_RESET;
        }
        if (!rooms.containsKey(roomName)) {
            return ANSI_RED + "Room not found: " + roomName + ANSI_RESET;
        }
        Map<String, String> info = rooms.get(roomName);
        Set<String> members = roomMembers.getOrDefault(roomName, new HashSet<>());
        Set<String> admins = roomAdmins.getOrDefault(roomName, new HashSet<>());
        Set<String> banned = roomBans.getOrDefault(roomName, new HashSet<>());
        boolean isPrivate = Boolean.parseBoolean(info.getOrDefault("isPrivate", "false"));
        boolean isReadOnly = Boolean.parseBoolean(info.getOrDefault("isReadOnly", "false"));
        StringBuilder report = new StringBuilder();
        report.append(ANSI_CYAN).append(ANSI_BOLD).append("=== Room Report: #").append(roomName).append(" ===").append(ANSI_RESET).append("\n");
        if (reportType == null || reportType.equals("basic")) {
            report.append(ANSI_WHITE).append("Members: ").append(members.size()).append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Admins: ").append(admins.size()).append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Created: ").append(info.getOrDefault("created", "unknown")).append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Creator: ").append(info.getOrDefault("creator", "unknown")).append(ANSI_RESET).append("\n");
            String topic = info.getOrDefault("topic", "");
            if (!topic.isEmpty()) {
                report.append(ANSI_WHITE).append("Topic: ").append(topic).append(ANSI_RESET).append("\n");
            }
            report.append(ANSI_WHITE).append("Private: ").append(isPrivate ? ANSI_YELLOW + "yes" : ANSI_GREEN + "no").append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Read-only: ").append(isReadOnly ? ANSI_YELLOW + "yes" : ANSI_GREEN + "no").append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Messages: ").append(roomMessageCounts.getOrDefault(roomName, 0)).append(ANSI_RESET).append("\n");
        } else if (reportType.equals("members")) {
            report.append(ANSI_CYAN).append("Member list:").append(ANSI_RESET).append("\n");
            List<String> sortedMembers = new ArrayList<>(members);
            Collections.sort(sortedMembers);
            for (String member : sortedMembers) {
                boolean isAdmin = admins.contains(member);
                String role = isAdmin ? " [admin]" : "";
                report.append("  ").append(ANSI_WHITE).append(member).append(ANSI_GREEN).append(role).append(ANSI_RESET).append("\n");
            }
            if (detailed && !banned.isEmpty() && admins.contains(requestingUser)) {
                report.append(ANSI_RED).append("Banned users:").append(ANSI_RESET).append("\n");
                for (String bannedUser : banned) {
                    report.append("  ").append(ANSI_RED).append(bannedUser).append(ANSI_RESET).append("\n");
                }
            }
        } else if (reportType.equals("activity")) {
            long lastActivity = roomLastActivity.getOrDefault(roomName, 0L);
            if (lastActivity > 0) {
                long idleSecs = (System.currentTimeMillis() - lastActivity) / 1000;
                if (idleSecs < 60) {
                    report.append(ANSI_GREEN).append("Last activity: ").append(idleSecs).append("s ago").append(ANSI_RESET).append("\n");
                } else if (idleSecs < 3600) {
                    report.append(ANSI_YELLOW).append("Last activity: ").append(idleSecs / 60).append("m ago").append(ANSI_RESET).append("\n");
                } else {
                    report.append(ANSI_RED).append("Last activity: ").append(idleSecs / 3600).append("h ago").append(ANSI_RESET).append("\n");
                }
            } else {
                report.append(ANSI_WHITE).append("No activity recorded").append(ANSI_RESET).append("\n");
            }
            List<String> history = roomJoinHistory.getOrDefault(roomName, new ArrayList<>());
            report.append(ANSI_WHITE).append("Join events recorded: ").append(history.size()).append(ANSI_RESET).append("\n");
            if (detailed) {
                int showCount = Math.min(10, history.size());
                for (int i = history.size() - showCount; i < history.size(); i++) {
                    report.append("  ").append(ANSI_WHITE).append(history.get(i)).append(ANSI_RESET).append("\n");
                }
            }
        } else {
            report.append(ANSI_RED).append("Unknown report type: ").append(reportType).append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Valid types: basic, members, activity").append(ANSI_RESET).append("\n");
        }
        return report.toString();
    }

    // Merge two rooms - move all members from source to destination
    public boolean mergeRooms(String sourceRoom, String destinationRoom, String requestingUser) {
        if (sourceRoom == null || destinationRoom == null) {
            System.out.println(ANSI_RED + "Both room names required for merge" + ANSI_RESET);
            return false;
        }
        if (sourceRoom.equals(destinationRoom)) {
            System.out.println(ANSI_RED + "Cannot merge a room with itself" + ANSI_RESET);
            return false;
        }
        if (!rooms.containsKey(sourceRoom)) {
            System.out.println(ANSI_RED + "Source room not found: " + sourceRoom + ANSI_RESET);
            return false;
        }
        if (!rooms.containsKey(destinationRoom)) {
            System.out.println(ANSI_RED + "Destination room not found: " + destinationRoom + ANSI_RESET);
            return false;
        }
        Set<String> srcAdmins = roomAdmins.getOrDefault(sourceRoom, new HashSet<>());
        Set<String> dstAdmins = roomAdmins.getOrDefault(destinationRoom, new HashSet<>());
        if (!srcAdmins.contains(requestingUser) && !dstAdmins.contains(requestingUser)) {
            System.out.println(ANSI_RED + "Must be admin of at least one room to merge" + ANSI_RESET);
            return false;
        }
        Set<String> srcMembers = new HashSet<>(roomMembers.getOrDefault(sourceRoom, new HashSet<>()));
        Set<String> dstMembers = roomMembers.getOrDefault(destinationRoom, new HashSet<>());
        for (String member : srcMembers) {
            dstMembers.add(member);
            if (!userRooms.containsKey(member)) {
                userRooms.put(member, new HashSet<>());
            }
            userRooms.get(member).add(destinationRoom);
            userRooms.get(member).remove(sourceRoom);
        }
        rooms.get(destinationRoom).put("memberCount", String.valueOf(dstMembers.size()));
        // Remove source room
        rooms.remove(sourceRoom);
        roomMembers.remove(sourceRoom);
        roomAdmins.remove(sourceRoom);
        roomBans.remove(sourceRoom);
        roomInvites.remove(sourceRoom);
        roomJoinHistory.remove(sourceRoom);
        roomMessageCounts.remove(sourceRoom);
        pinnedMessages.remove(sourceRoom);
        System.out.println(ANSI_GREEN + "Merged #" + sourceRoom + " into #" + destinationRoom +
                " (" + srcMembers.size() + " members moved)" + ANSI_RESET);
        return true;
    }
}
