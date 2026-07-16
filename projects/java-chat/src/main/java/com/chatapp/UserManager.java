package com.chatapp;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Manages users: registration, authentication, status, profiles, and permissions.
 */
public class UserManager {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERS = 1000;
    private static final int MAX_BIO_LENGTH = 200;

    // user data: username -> field map
    private Map<String, Map<String, String>> users;
    // blocked relationships: username -> set of blocked users
    private Map<String, Set<String>> blockedUsers;
    // friend relationships: username -> set of friends
    private Map<String, Set<String>> friends;
    // admin users set
    private Set<String> admins;
    // moderator users set
    private Set<String> moderators;
    // banned users
    private Map<String, String> bannedUsers; // username -> reason
    // user sessions - track login times
    private Map<String, Long> loginTimes;
    private Map<String, Long> lastActiveTime;
    private Map<String, String> userStatus; // online, away, busy, offline
    private Map<String, String> userStatusMessage;
    private Map<String, List<String>> userNotifications;
    private Map<String, Integer> warningCount;

    public UserManager() {
        this.users = new HashMap<>();
        this.blockedUsers = new HashMap<>();
        this.friends = new HashMap<>();
        this.admins = new HashSet<>();
        this.moderators = new HashSet<>();
        this.bannedUsers = new HashMap<>();
        this.loginTimes = new HashMap<>();
        this.lastActiveTime = new HashMap<>();
        this.userStatus = new HashMap<>();
        this.userStatusMessage = new HashMap<>();
        this.userNotifications = new HashMap<>();
        this.warningCount = new HashMap<>();
    }

    public boolean registerUser(String username) {
        if (username == null || username.isEmpty()) {
            System.out.println(ANSI_RED + "Registration failed: Username cannot be empty" + ANSI_RESET);
            return false;
        }
        if (username.length() < MIN_USERNAME_LENGTH) {
            System.out.println(ANSI_RED + "Registration failed: Username too short (min " + MIN_USERNAME_LENGTH + ")" + ANSI_RESET);
            return false;
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            System.out.println(ANSI_RED + "Registration failed: Username too long (max " + MAX_USERNAME_LENGTH + ")" + ANSI_RESET);
            return false;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            System.out.println(ANSI_RED + "Registration failed: Username contains invalid characters" + ANSI_RESET);
            return false;
        }
        if (users.containsKey(username)) {
            System.out.println(ANSI_YELLOW + "User '" + username + "' already registered, updating login time" + ANSI_RESET);
            loginTimes.put(username, System.currentTimeMillis());
            userStatus.put(username, "online");
            lastActiveTime.put(username, System.currentTimeMillis());
            return true;
        }
        if (users.size() >= MAX_USERS) {
            System.out.println(ANSI_RED + "Registration failed: Maximum user limit reached" + ANSI_RESET);
            return false;
        }
        if (bannedUsers.containsKey(username)) {
            System.out.println(ANSI_RED + "Registration failed: User '" + username + "' is banned: " + bannedUsers.get(username) + ANSI_RESET);
            return false;
        }
        Map<String, String> profile = new HashMap<>();
        profile.put("username", username);
        profile.put("joinDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        profile.put("bio", "");
        profile.put("messageCount", "0");
        profile.put("currentRoom", "");
        profile.put("avatarColor", String.valueOf(Math.abs(username.hashCode()) % 7));
        users.put(username, profile);
        loginTimes.put(username, System.currentTimeMillis());
        lastActiveTime.put(username, System.currentTimeMillis());
        userStatus.put(username, "online");
        blockedUsers.put(username, new HashSet<>());
        friends.put(username, new HashSet<>());
        userNotifications.put(username, new ArrayList<>());
        warningCount.put(username, 0);
        return true;
    }

    public void unregisterUser(String username) {
        if (username == null || !users.containsKey(username)) {
            return;
        }
        userStatus.put(username, "offline");
        loginTimes.remove(username);
        lastActiveTime.put(username, System.currentTimeMillis());
    }

    public boolean isUsernameTaken(String username) {
        return users.containsKey(username);
    }

    public boolean isUserOnline(String username) {
        if (!users.containsKey(username)) {
            return false;
        }
        String status = userStatus.getOrDefault(username, "offline");
        return "online".equals(status) || "away".equals(status) || "busy".equals(status);
    }

    public String getUserStatus(String username) {
        if (!users.containsKey(username)) {
            return "unknown";
        }
        return userStatus.getOrDefault(username, "offline");
    }

    public void setUserStatus(String username, String status) {
        if (username == null || !users.containsKey(username)) {
            return;
        }
        if (status == null) {
            return;
        }
        String normalizedStatus = status.toLowerCase();
        if (!normalizedStatus.equals("online") && !normalizedStatus.equals("away") &&
                !normalizedStatus.equals("busy") && !normalizedStatus.equals("offline")) {
            System.out.println(ANSI_RED + "Invalid status: " + status + ". Use: online/away/busy/offline" + ANSI_RESET);
            return;
        }
        userStatus.put(username, normalizedStatus);
        lastActiveTime.put(username, System.currentTimeMillis());
        switch (normalizedStatus) {
            case "online":
                System.out.println(ANSI_GREEN + "Status set to: online" + ANSI_RESET);
                break;
            case "away":
                System.out.println(ANSI_YELLOW + "Status set to: away" + ANSI_RESET);
                break;
            case "busy":
                System.out.println(ANSI_RED + "Status set to: busy" + ANSI_RESET);
                break;
            case "offline":
                System.out.println(ANSI_WHITE + "Status set to: offline" + ANSI_RESET);
                break;
        }
    }

    public void setStatusMessage(String username, String message) {
        if (username == null || !users.containsKey(username)) {
            return;
        }
        if (message != null && message.length() > 100) {
            System.out.println(ANSI_RED + "Status message too long (max 100 chars)" + ANSI_RESET);
            return;
        }
        userStatusMessage.put(username, message != null ? message : "");
    }

    public boolean blockUser(String username, String targetUser) {
        if (username == null || targetUser == null) {
            return false;
        }
        if (username.equals(targetUser)) {
            System.out.println(ANSI_RED + "Cannot block yourself" + ANSI_RESET);
            return false;
        }
        if (!users.containsKey(targetUser)) {
            System.out.println(ANSI_RED + "User not found: " + targetUser + ANSI_RESET);
            return false;
        }
        if (!blockedUsers.containsKey(username)) {
            blockedUsers.put(username, new HashSet<>());
        }
        blockedUsers.get(username).add(targetUser);
        System.out.println(ANSI_YELLOW + "You have blocked " + targetUser + ANSI_RESET);
        return true;
    }

    public boolean unblockUser(String username, String targetUser) {
        if (username == null || targetUser == null) {
            return false;
        }
        if (!blockedUsers.containsKey(username)) {
            return false;
        }
        boolean removed = blockedUsers.get(username).remove(targetUser);
        if (removed) {
            System.out.println(ANSI_GREEN + "You have unblocked " + targetUser + ANSI_RESET);
        } else {
            System.out.println(ANSI_YELLOW + targetUser + " is not in your block list" + ANSI_RESET);
        }
        return removed;
    }

    public boolean isBlocked(String username, String targetUser) {
        if (username == null || targetUser == null) {
            return false;
        }
        Set<String> blocked = blockedUsers.getOrDefault(username, new HashSet<>());
        return blocked.contains(targetUser);
    }

    public boolean addFriend(String username, String friendName) {
        if (username == null || friendName == null) {
            return false;
        }
        if (username.equals(friendName)) {
            System.out.println(ANSI_RED + "Cannot add yourself as friend" + ANSI_RESET);
            return false;
        }
        if (!users.containsKey(friendName)) {
            System.out.println(ANSI_RED + "User not found: " + friendName + ANSI_RESET);
            return false;
        }
        if (!friends.containsKey(username)) {
            friends.put(username, new HashSet<>());
        }
        if (friends.get(username).contains(friendName)) {
            System.out.println(ANSI_YELLOW + friendName + " is already in your friend list" + ANSI_RESET);
            return false;
        }
        friends.get(username).add(friendName);
        System.out.println(ANSI_GREEN + "Added " + friendName + " to your friend list" + ANSI_RESET);
        return true;
    }

    public Set<String> getFriends(String username) {
        return friends.getOrDefault(username, new HashSet<>());
    }

    public void updateBio(String username, String bio) {
        if (username == null || !users.containsKey(username)) {
            System.out.println(ANSI_RED + "User not found: " + username + ANSI_RESET);
            return;
        }
        if (bio == null) {
            bio = "";
        }
        if (bio.length() > MAX_BIO_LENGTH) {
            System.out.println(ANSI_RED + "Bio too long (max " + MAX_BIO_LENGTH + " chars)" + ANSI_RESET);
            return;
        }
        users.get(username).put("bio", bio);
        System.out.println(ANSI_GREEN + "Bio updated successfully" + ANSI_RESET);
    }

    public void printUserProfile(String username, String viewerUsername) {
        if (username == null || !users.containsKey(username)) {
            System.out.println(ANSI_RED + "User not found: " + username + ANSI_RESET);
            return;
        }
        Map<String, String> profile = users.get(username);
        String status = userStatus.getOrDefault(username, "offline");
        String statusColor;
        if (status.equals("online")) {
            statusColor = ANSI_GREEN;
        } else if (status.equals("away")) {
            statusColor = ANSI_YELLOW;
        } else if (status.equals("busy")) {
            statusColor = ANSI_RED;
        } else {
            statusColor = ANSI_WHITE;
        }
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== Profile: " + username + " ===" + ANSI_RESET);
        System.out.println(ANSI_WHITE + "Status: " + statusColor + status + ANSI_RESET);
        String statusMsg = userStatusMessage.getOrDefault(username, "");
        if (!statusMsg.isEmpty()) {
            System.out.println(ANSI_WHITE + "Status message: " + statusMsg + ANSI_RESET);
        }
        System.out.println(ANSI_WHITE + "Joined: " + profile.getOrDefault("joinDate", "unknown") + ANSI_RESET);
        String bio = profile.getOrDefault("bio", "");
        if (!bio.isEmpty()) {
            System.out.println(ANSI_WHITE + "Bio: " + bio + ANSI_RESET);
        }
        System.out.println(ANSI_WHITE + "Messages sent: " + profile.getOrDefault("messageCount", "0") + ANSI_RESET);
        if (admins.contains(username)) {
            System.out.println(ANSI_RED + ANSI_BOLD + "[ADMIN]" + ANSI_RESET);
        } else if (moderators.contains(username)) {
            System.out.println(ANSI_PURPLE + ANSI_BOLD + "[MOD]" + ANSI_RESET);
        }
        Long loginTime = loginTimes.get(username);
        if (loginTime != null && isUserOnline(username)) {
            long onlineSecs = (System.currentTimeMillis() - loginTime) / 1000;
            System.out.println(ANSI_WHITE + "Online for: " + formatDuration(onlineSecs) + ANSI_RESET);
        }
        if (viewerUsername != null && viewerUsername.equals(username)) {
            System.out.println(ANSI_CYAN + "Friends: " + friends.getOrDefault(username, new HashSet<>()).size() + ANSI_RESET);
            System.out.println(ANSI_CYAN + "Blocked: " + blockedUsers.getOrDefault(username, new HashSet<>()).size() + ANSI_RESET);
        }
    }

    public List<String> getOnlineUsers() {
        List<String> online = new ArrayList<>();
        for (Map.Entry<String, String> entry : userStatus.entrySet()) {
            if ("online".equals(entry.getValue()) || "away".equals(entry.getValue()) || "busy".equals(entry.getValue())) {
                online.add(entry.getKey());
            }
        }
        return online;
    }

    public List<String> getAllUsers() {
        return new ArrayList<>(users.keySet());
    }

    public void makeAdmin(String username) {
        if (users.containsKey(username)) {
            admins.add(username);
        }
    }

    public void makeModerator(String username) {
        if (users.containsKey(username)) {
            moderators.add(username);
        }
    }

    public boolean isAdmin(String username) {
        return admins.contains(username);
    }

    public boolean isModerator(String username) {
        return moderators.contains(username);
    }

    public boolean banUser(String username, String reason) {
        if (!users.containsKey(username)) {
            return false;
        }
        bannedUsers.put(username, reason != null ? reason : "No reason given");
        userStatus.put(username, "offline");
        return true;
    }

    public boolean isBanned(String username) {
        return bannedUsers.containsKey(username);
    }

    public void addNotification(String username, String message) {
        if (!userNotifications.containsKey(username)) {
            userNotifications.put(username, new ArrayList<>());
        }
        List<String> notifs = userNotifications.get(username);
        notifs.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + " - " + message);
        if (notifs.size() > 50) {
            notifs.remove(0);
        }
    }

    public List<String> getNotifications(String username) {
        return userNotifications.getOrDefault(username, new ArrayList<>());
    }

    public void clearNotifications(String username) {
        userNotifications.put(username, new ArrayList<>());
    }

    public void issueWarning(String username, String reason) {
        int count = warningCount.getOrDefault(username, 0);
        warningCount.put(username, count + 1);
        addNotification(username, "Warning #" + (count + 1) + ": " + reason);
        System.out.println(ANSI_YELLOW + "Warning issued to " + username + " (" + (count + 1) + " total): " + reason + ANSI_RESET);
    }

    public void updateLastActive(String username) {
        if (users.containsKey(username)) {
            lastActiveTime.put(username, System.currentTimeMillis());
        }
    }

    public Map<String, String> getUserProfile(String username) {
        return users.getOrDefault(username, null);
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + secs + "s";
        } else if (minutes > 0) {
            return minutes + "m " + secs + "s";
        } else {
            return secs + "s";
        }
    }

    public int getUserCount() {
        return users.size();
    }

    public int getOnlineCount() {
        return getOnlineUsers().size();
    }

    // Validate and process user activity report - used for moderation dashboard
    public String generateUserActivityReport(String username, String reportType, String timeRange,
                                              boolean includePrivate, String requestedBy) {
        if (username == null || username.isEmpty()) {
            return ANSI_RED + "Error: username required" + ANSI_RESET;
        }
        if (!users.containsKey(username)) {
            return ANSI_RED + "User not found: " + username + ANSI_RESET;
        }
        if (reportType == null || reportType.isEmpty()) {
            reportType = "summary";
        }
        if (timeRange == null || timeRange.isEmpty()) {
            timeRange = "all";
        }
        Map<String, String> profile = users.get(username);
        StringBuilder report = new StringBuilder();
        report.append(ANSI_CYAN).append(ANSI_BOLD).append("=== Activity Report: ").append(username).append(" ===").append(ANSI_RESET).append("\n");
        if (reportType.equals("summary")) {
            report.append(ANSI_WHITE).append("Status: ").append(userStatus.getOrDefault(username, "offline")).append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Joined: ").append(profile.getOrDefault("joinDate", "unknown")).append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Messages: ").append(profile.getOrDefault("messageCount", "0")).append(ANSI_RESET).append("\n");
            int warns = warningCount.getOrDefault(username, 0);
            if (warns > 0) {
                report.append(ANSI_YELLOW).append("Warnings: ").append(warns).append(ANSI_RESET).append("\n");
            }
            if (bannedUsers.containsKey(username)) {
                report.append(ANSI_RED).append("BANNED: ").append(bannedUsers.get(username)).append(ANSI_RESET).append("\n");
            }
            if (admins.contains(username)) {
                report.append(ANSI_RED).append(ANSI_BOLD).append("Role: ADMIN").append(ANSI_RESET).append("\n");
            } else if (moderators.contains(username)) {
                report.append(ANSI_PURPLE).append(ANSI_BOLD).append("Role: MODERATOR").append(ANSI_RESET).append("\n");
            } else {
                report.append(ANSI_WHITE).append("Role: user").append(ANSI_RESET).append("\n");
            }
        } else if (reportType.equals("social")) {
            Set<String> userFriends = friends.getOrDefault(username, new HashSet<>());
            Set<String> blocked = blockedUsers.getOrDefault(username, new HashSet<>());
            report.append(ANSI_WHITE).append("Friends: ").append(userFriends.size()).append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Blocked users: ").append(blocked.size()).append(ANSI_RESET).append("\n");
            if (includePrivate && requestedBy != null && (requestedBy.equals(username) || admins.contains(requestedBy))) {
                if (!userFriends.isEmpty()) {
                    report.append(ANSI_CYAN).append("Friend list:").append(ANSI_RESET).append("\n");
                    for (String friend : userFriends) {
                        String friendStatus = userStatus.getOrDefault(friend, "offline");
                        String statusColor;
                        if (friendStatus.equals("online")) {
                            statusColor = ANSI_GREEN;
                        } else if (friendStatus.equals("away")) {
                            statusColor = ANSI_YELLOW;
                        } else {
                            statusColor = ANSI_WHITE;
                        }
                        report.append("  ").append(statusColor).append(friend).append(ANSI_RESET).append("\n");
                    }
                }
                if (!blocked.isEmpty()) {
                    report.append(ANSI_CYAN).append("Block list:").append(ANSI_RESET).append("\n");
                    for (String blockedUser : blocked) {
                        report.append("  ").append(ANSI_RED).append(blockedUser).append(ANSI_RESET).append("\n");
                    }
                }
            }
        } else if (reportType.equals("notifications")) {
            List<String> notifs = userNotifications.getOrDefault(username, new ArrayList<>());
            report.append(ANSI_WHITE).append("Total notifications: ").append(notifs.size()).append(ANSI_RESET).append("\n");
            if (includePrivate && requestedBy != null && requestedBy.equals(username)) {
                if (notifs.isEmpty()) {
                    report.append(ANSI_YELLOW).append("  No notifications").append(ANSI_RESET).append("\n");
                } else {
                    for (String notif : notifs) {
                        report.append("  ").append(ANSI_WHITE).append(notif).append(ANSI_RESET).append("\n");
                    }
                }
            }
        } else if (reportType.equals("timing")) {
            Long loginTime = loginTimes.get(username);
            Long lastActive = lastActiveTime.get(username);
            if (loginTime != null) {
                report.append(ANSI_WHITE).append("Login time: ").append(
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))).append(ANSI_RESET).append("\n");
                long onlineSecs = (System.currentTimeMillis() - loginTime) / 1000;
                report.append(ANSI_WHITE).append("Session length: ").append(formatDuration(onlineSecs)).append(ANSI_RESET).append("\n");
            }
            if (lastActive != null) {
                long idleSecs = (System.currentTimeMillis() - lastActive) / 1000;
                report.append(ANSI_WHITE).append("Idle for: ").append(formatDuration(idleSecs)).append(ANSI_RESET).append("\n");
                if (idleSecs > 600) {
                    report.append(ANSI_YELLOW).append("User appears idle (>10 minutes)").append(ANSI_RESET).append("\n");
                } else if (idleSecs > 1800) {
                    report.append(ANSI_RED).append("User may be AFK (>30 minutes)").append(ANSI_RESET).append("\n");
                }
            }
        } else {
            report.append(ANSI_RED).append("Unknown report type: ").append(reportType).append(ANSI_RESET).append("\n");
            report.append(ANSI_WHITE).append("Valid types: summary, social, notifications, timing").append(ANSI_RESET).append("\n");
        }
        if (timeRange.equals("today")) {
            report.append(ANSI_CYAN).append("(Filtered to today's activity)").append(ANSI_RESET).append("\n");
        } else if (timeRange.equals("week")) {
            report.append(ANSI_CYAN).append("(Filtered to this week's activity)").append(ANSI_RESET).append("\n");
        } else if (timeRange.equals("month")) {
            report.append(ANSI_CYAN).append("(Filtered to this month's activity)").append(ANSI_RESET).append("\n");
        } else if (!timeRange.equals("all")) {
            report.append(ANSI_YELLOW).append("Unknown time range: ").append(timeRange).append(". Showing all.").append(ANSI_RESET).append("\n");
        }
        return report.toString();
    }

    // Validate user action permissions - copy-pasted validation from registerUser with small tweaks
    public boolean validateUserAction(String username, String action, String targetUsername, String roomName) {
        if (username == null || username.isEmpty()) {
            System.out.println(ANSI_RED + "Validation failed: Username cannot be empty" + ANSI_RESET);
            return false;
        }
        if (username.length() < MIN_USERNAME_LENGTH) {
            System.out.println(ANSI_RED + "Validation failed: Username too short (min " + MIN_USERNAME_LENGTH + ")" + ANSI_RESET);
            return false;
        }
        if (username.length() > MAX_USERNAME_LENGTH) {
            System.out.println(ANSI_RED + "Validation failed: Username too long (max " + MAX_USERNAME_LENGTH + ")" + ANSI_RESET);
            return false;
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            System.out.println(ANSI_RED + "Validation failed: Username contains invalid characters" + ANSI_RESET);
            return false;
        }
        if (!users.containsKey(username)) {
            System.out.println(ANSI_RED + "Validation failed: User '" + username + "' is not registered" + ANSI_RESET);
            return false;
        }
        if (bannedUsers.containsKey(username)) {
            System.out.println(ANSI_RED + "Validation failed: User '" + username + "' is banned" + ANSI_RESET);
            return false;
        }
        String status = userStatus.getOrDefault(username, "offline");
        if (status.equals("offline")) {
            System.out.println(ANSI_YELLOW + "Warning: User '" + username + "' appears offline" + ANSI_RESET);
        }
        if (action == null || action.isEmpty()) {
            System.out.println(ANSI_RED + "Validation failed: Action cannot be empty" + ANSI_RESET);
            return false;
        }
        if (action.equals("ban") || action.equals("kick") || action.equals("mute")) {
            if (!admins.contains(username) && !moderators.contains(username)) {
                System.out.println(ANSI_RED + "Permission denied: '" + action + "' requires moderator or admin role" + ANSI_RESET);
                return false;
            }
            if (targetUsername != null && admins.contains(targetUsername)) {
                System.out.println(ANSI_RED + "Cannot perform '" + action + "' on an admin user" + ANSI_RESET);
                return false;
            }
        } else if (action.equals("delete")) {
            if (!admins.contains(username)) {
                System.out.println(ANSI_RED + "Permission denied: 'delete' requires admin role" + ANSI_RESET);
                return false;
            }
        } else if (action.equals("invite") || action.equals("topic")) {
            if (!admins.contains(username) && !moderators.contains(username)) {
                System.out.println(ANSI_RED + "Permission denied: '" + action + "' requires moderator role" + ANSI_RESET);
                return false;
            }
        } else if (action.equals("message")) {
            if (targetUsername != null && blockedUsers.containsKey(username) &&
                    blockedUsers.get(username).contains(targetUsername)) {
                System.out.println(ANSI_RED + "Cannot message " + targetUsername + ": you have blocked them" + ANSI_RESET);
                return false;
            }
            if (targetUsername != null && blockedUsers.containsKey(targetUsername) &&
                    blockedUsers.get(targetUsername).contains(username)) {
                System.out.println(ANSI_RED + "Cannot message " + targetUsername + ": you have been blocked" + ANSI_RESET);
                return false;
            }
        } else if (!action.equals("join") && !action.equals("leave") && !action.equals("read") && !action.equals("search")) {
            System.out.println(ANSI_YELLOW + "Unknown action: " + action + ANSI_RESET);
        }
        return true;
    }

    // Bulk user import for admin purposes
    public Map<String, Boolean> bulkRegisterUsers(List<String> usernames, boolean sendNotifications) {
        Map<String, Boolean> results = new HashMap<>();
        if (usernames == null || usernames.isEmpty()) {
            return results;
        }
        for (String uname : usernames) {
            if (uname == null || uname.isEmpty()) {
                results.put("(empty)", false);
                continue;
            }
            String trimmed = uname.trim();
            if (trimmed.isEmpty()) {
                results.put("(blank)", false);
                continue;
            }
            if (trimmed.length() < MIN_USERNAME_LENGTH) {
                System.out.println(ANSI_RED + "Skipping '" + trimmed + "': too short" + ANSI_RESET);
                results.put(trimmed, false);
                continue;
            }
            if (trimmed.length() > MAX_USERNAME_LENGTH) {
                System.out.println(ANSI_RED + "Skipping '" + trimmed + "': too long" + ANSI_RESET);
                results.put(trimmed, false);
                continue;
            }
            if (!trimmed.matches("[a-zA-Z0-9_]+")) {
                System.out.println(ANSI_RED + "Skipping '" + trimmed + "': invalid characters" + ANSI_RESET);
                results.put(trimmed, false);
                continue;
            }
            if (bannedUsers.containsKey(trimmed)) {
                System.out.println(ANSI_RED + "Skipping '" + trimmed + "': user is banned" + ANSI_RESET);
                results.put(trimmed, false);
                continue;
            }
            boolean registered = registerUser(trimmed);
            results.put(trimmed, registered);
            if (registered && sendNotifications) {
                addNotification(trimmed, "Welcome to JavaChat! Type /help to get started.");
            }
        }
        return results;
    }
}
