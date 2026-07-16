package com.chatapp;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Handles all terminal rendering, formatting, and display concerns.
 * Responsible for drawing the UI, formatting messages, and printing output.
 */
public class DisplayRenderer {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_UNDERLINE = "\u001B[4m";
    private static final String ANSI_BG_BLUE = "\u001B[44m";
    private static final String ANSI_BG_RED = "\u001B[41m";
    private static final String ANSI_BG_GREEN = "\u001B[42m";

    private static final int DEFAULT_TERMINAL_WIDTH = 80;
    private static final int MAX_TERMINAL_WIDTH = 220;

    private int terminalWidth;
    private boolean colorEnabled;
    private boolean timestampsEnabled;
    private boolean compactMode;
    private int messageDisplayCount;
    private String lastSender;
    private Map<String, String> userColorCache;
    private List<String> displayHistory;
    private boolean separatorsEnabled;
    private String dateFormat;
    private String timeFormat;
    private static final String[] USER_COLORS = {
        ANSI_RED, ANSI_GREEN, ANSI_YELLOW, ANSI_BLUE, ANSI_PURPLE, ANSI_CYAN, ANSI_WHITE
    };

    public DisplayRenderer() {
        this.terminalWidth = DEFAULT_TERMINAL_WIDTH;
        this.colorEnabled = true;
        this.timestampsEnabled = true;
        this.compactMode = false;
        this.messageDisplayCount = 0;
        this.lastSender = null;
        this.userColorCache = new HashMap<>();
        this.displayHistory = new ArrayList<>();
        this.separatorsEnabled = true;
        this.dateFormat = "yyyy-MM-dd";
        this.timeFormat = "HH:mm:ss";
        detectTerminalWidth();
    }

    private void detectTerminalWidth() {
        try {
            String columns = System.getenv("COLUMNS");
            if (columns != null && !columns.isEmpty()) {
                int w = Integer.parseInt(columns);
                if (w > 20 && w <= MAX_TERMINAL_WIDTH) {
                    terminalWidth = w;
                }
            }
        } catch (Exception e) {
            terminalWidth = DEFAULT_TERMINAL_WIDTH;
        }
    }

    public void printMessage(String username, String content, String room, String type,
                              long timestamp, boolean isSystem) {
        messageDisplayCount++;
        String timeStr = formatTimestamp(timestamp);
        if (isSystem || "system".equals(type)) {
            printSystemMessage(content);
            return;
        }
        if ("action".equals(type)) {
            printActionMessage(username, content, timeStr);
            return;
        }
        if ("private".equals(type)) {
            printPrivateMessage(username, content, timeStr, false);
            return;
        }
        String userColor = getCachedUserColor(username);
        if (compactMode && username.equals(lastSender)) {
            System.out.println(ANSI_WHITE + "  " + content + ANSI_RESET);
        } else {
            if (!compactMode && separatorsEnabled && messageDisplayCount > 1 &&
                    !username.equals(lastSender)) {
                // no separator, just continue
            }
            System.out.println(ANSI_WHITE + "[" + timeStr + "] " + userColor + ANSI_BOLD +
                    username + ANSI_RESET + ANSI_WHITE + ": " + content + ANSI_RESET);
        }
        lastSender = username;
        displayHistory.add("[" + timeStr + "] " + username + ": " + content);
        if (displayHistory.size() > 1000) {
            displayHistory.remove(0);
        }
    }

    public void printSystemMessage(String content) {
        if (content == null) return;
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
        System.out.println(ANSI_CYAN + "[" + timeStr + "] *** " + content + " ***" + ANSI_RESET);
        displayHistory.add("[" + timeStr + "] [SYSTEM] " + content);
        if (displayHistory.size() > 1000) {
            displayHistory.remove(0);
        }
    }

    public void printErrorMessage(String content) {
        if (content == null) return;
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
        System.out.println(ANSI_RED + "[" + timeStr + "] [ERROR] " + content + ANSI_RESET);
    }

    public void printWarningMessage(String content) {
        if (content == null) return;
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
        System.out.println(ANSI_YELLOW + "[" + timeStr + "] [WARN] " + content + ANSI_RESET);
    }

    public void printSuccessMessage(String content) {
        if (content == null) return;
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
        System.out.println(ANSI_GREEN + "[" + timeStr + "] " + content + ANSI_RESET);
    }

    public void printPrivateMessage(String fromUser, String content, String timeStr, boolean isIncoming) {
        if (fromUser == null || content == null) return;
        if (timeStr == null) {
            timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
        }
        String fromColor = getCachedUserColor(fromUser);
        if (isIncoming) {
            System.out.println(ANSI_PURPLE + "[" + timeStr + "] [PM from " + fromColor + ANSI_BOLD +
                    fromUser + ANSI_RESET + ANSI_PURPLE + "] " + content + ANSI_RESET);
        } else {
            System.out.println(ANSI_PURPLE + "[" + timeStr + "] [PM to " + fromColor + ANSI_BOLD +
                    fromUser + ANSI_RESET + ANSI_PURPLE + "] " + content + ANSI_RESET);
        }
        displayHistory.add("[" + timeStr + "] [PM] " + fromUser + ": " + content);
        if (displayHistory.size() > 1000) {
            displayHistory.remove(0);
        }
    }

    private void printActionMessage(String username, String content, String timeStr) {
        if (username == null || content == null) return;
        if (timeStr == null) {
            timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
        }
        System.out.println(ANSI_PURPLE + "[" + timeStr + "] * " + ANSI_BOLD +
                username + " " + content + ANSI_RESET);
        displayHistory.add("[" + timeStr + "] * " + username + " " + content);
        if (displayHistory.size() > 1000) {
            displayHistory.remove(0);
        }
    }

    public void printSeparator() {
        if (!separatorsEnabled) return;
        StringBuilder sb = new StringBuilder();
        sb.append(ANSI_BLUE);
        for (int i = 0; i < Math.min(terminalWidth, 80); i++) {
            sb.append("─");
        }
        sb.append(ANSI_RESET);
        System.out.println(sb.toString());
    }

    public void printHeader(String title) {
        if (title == null) return;
        int padding = Math.max(0, (terminalWidth - title.length() - 4) / 2);
        StringBuilder sb = new StringBuilder();
        sb.append(ANSI_CYAN).append(ANSI_BOLD);
        for (int i = 0; i < padding; i++) sb.append("═");
        sb.append(" ").append(title).append(" ");
        for (int i = 0; i < padding; i++) sb.append("═");
        sb.append(ANSI_RESET);
        System.out.println(sb.toString());
    }

    public void printTable(List<String[]> rows, String[] headers) {
        if (rows == null || headers == null) return;
        int[] colWidths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            colWidths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < Math.min(row.length, headers.length); i++) {
                if (row[i] != null && row[i].length() > colWidths[i]) {
                    colWidths[i] = row[i].length();
                }
            }
        }
        // Print header row
        StringBuilder headerLine = new StringBuilder(ANSI_CYAN + ANSI_BOLD);
        StringBuilder separatorLine = new StringBuilder(ANSI_BLUE);
        for (int i = 0; i < headers.length; i++) {
            headerLine.append(padRight(headers[i], colWidths[i] + 2));
            for (int j = 0; j < colWidths[i] + 2; j++) separatorLine.append("─");
        }
        headerLine.append(ANSI_RESET);
        separatorLine.append(ANSI_RESET);
        System.out.println(headerLine);
        System.out.println(separatorLine);
        // Print data rows
        for (String[] row : rows) {
            StringBuilder rowLine = new StringBuilder(ANSI_WHITE);
            for (int i = 0; i < headers.length; i++) {
                String cell = i < row.length && row[i] != null ? row[i] : "";
                rowLine.append(padRight(cell, colWidths[i] + 2));
            }
            rowLine.append(ANSI_RESET);
            System.out.println(rowLine);
        }
    }

    public void printProgress(int current, int total, String label) {
        if (total <= 0) return;
        int barWidth = Math.min(40, terminalWidth - 20);
        int filled = (int) ((double) current / total * barWidth);
        filled = Math.max(0, Math.min(filled, barWidth));
        StringBuilder bar = new StringBuilder();
        bar.append(ANSI_CYAN).append("[");
        bar.append(ANSI_GREEN);
        for (int i = 0; i < filled; i++) bar.append("█");
        bar.append(ANSI_WHITE);
        for (int i = filled; i < barWidth; i++) bar.append("░");
        bar.append(ANSI_CYAN).append("] ");
        int percentage = (int) ((double) current / total * 100);
        bar.append(ANSI_BOLD).append(percentage).append("%");
        if (label != null && !label.isEmpty()) {
            bar.append(" ").append(label);
        }
        bar.append(ANSI_RESET);
        System.out.println(bar);
    }

    public void printBoxedMessage(String title, String content, String color) {
        if (content == null) return;
        String useColor = color != null ? color : ANSI_CYAN;
        String[] lines = content.split("\n");
        int maxWidth = title != null ? title.length() : 0;
        for (String line : lines) {
            if (line.length() > maxWidth) maxWidth = line.length();
        }
        maxWidth = Math.min(maxWidth + 4, terminalWidth - 2);
        StringBuilder topBorder = new StringBuilder(useColor + "┌");
        for (int i = 0; i < maxWidth; i++) topBorder.append("─");
        topBorder.append("┐" + ANSI_RESET);
        System.out.println(topBorder);
        if (title != null && !title.isEmpty()) {
            System.out.println(useColor + "│ " + ANSI_BOLD + padRight(title, maxWidth - 2) + ANSI_RESET + useColor + " │" + ANSI_RESET);
            StringBuilder midBorder = new StringBuilder(useColor + "├");
            for (int i = 0; i < maxWidth; i++) midBorder.append("─");
            midBorder.append("┤" + ANSI_RESET);
            System.out.println(midBorder);
        }
        for (String line : lines) {
            String truncated = line.length() > maxWidth - 2 ? line.substring(0, maxWidth - 5) + "..." : line;
            System.out.println(useColor + "│ " + ANSI_WHITE + padRight(truncated, maxWidth - 2) + ANSI_RESET + useColor + " │" + ANSI_RESET);
        }
        StringBuilder bottomBorder = new StringBuilder(useColor + "└");
        for (int i = 0; i < maxWidth; i++) bottomBorder.append("─");
        bottomBorder.append("┘" + ANSI_RESET);
        System.out.println(bottomBorder);
    }

    public void printNotificationBadge(int count, String type) {
        if (count <= 0) return;
        String color;
        String label;
        if (type == null || type.equals("message")) {
            color = ANSI_BG_BLUE;
            label = "MSG";
        } else if (type.equals("mention")) {
            color = ANSI_BG_RED;
            label = "@";
        } else if (type.equals("system")) {
            color = ANSI_BG_GREEN;
            label = "SYS";
        } else {
            color = ANSI_BG_BLUE;
            label = type.toUpperCase();
        }
        System.out.println(color + ANSI_BOLD + " " + label + " " + count + " " + ANSI_RESET);
    }

    public String formatMessageForLog(String username, String content, String room, long timestamp) {
        String timeStr = formatTimestamp(timestamp);
        return "[" + timeStr + "] [" + room + "] " + username + ": " + content;
    }

    public String colorizeText(String text, String colorCode) {
        if (!colorEnabled || text == null) return text;
        return colorCode + text + ANSI_RESET;
    }

    public String highlightText(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) return text;
        return text.replaceAll("(?i)" + keyword,
                ANSI_YELLOW + ANSI_BOLD + "$0" + ANSI_RESET + ANSI_WHITE);
    }

    public void printRoomHeader(String roomName, String topic, int memberCount) {
        System.out.println();
        System.out.println(ANSI_CYAN + ANSI_BOLD + "━━━ #" + roomName + " (" + memberCount + " members)" + ANSI_RESET);
        if (topic != null && !topic.isEmpty()) {
            System.out.println(ANSI_WHITE + "    Topic: " + topic + ANSI_RESET);
        }
        System.out.println();
    }

    public void printMemberList(Set<String> members, String currentRoom, Map<String, String> statusMap) {
        if (members == null || members.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No members in this room" + ANSI_RESET);
            return;
        }
        System.out.println(ANSI_CYAN + ANSI_BOLD + "Members in #" + currentRoom + " (" + members.size() + "):" + ANSI_RESET);
        List<String> sortedMembers = new ArrayList<>(members);
        Collections.sort(sortedMembers);
        for (String member : sortedMembers) {
            String status = statusMap != null ? statusMap.getOrDefault(member, "offline") : "online";
            String statusColor;
            String statusIcon;
            if ("online".equals(status)) {
                statusColor = ANSI_GREEN;
                statusIcon = "●";
            } else if ("away".equals(status)) {
                statusColor = ANSI_YELLOW;
                statusIcon = "◑";
            } else if ("busy".equals(status)) {
                statusColor = ANSI_RED;
                statusIcon = "◌";
            } else {
                statusColor = ANSI_WHITE;
                statusIcon = "○";
            }
            String memberColor = getCachedUserColor(member);
            System.out.println("  " + statusColor + statusIcon + " " + memberColor + ANSI_BOLD + member + ANSI_RESET);
        }
    }

    public void printPaginatedHistory(List<String[]> messages, int page, int pageSize, String username) {
        if (messages == null || messages.isEmpty()) {
            System.out.println(ANSI_YELLOW + "No messages to display" + ANSI_RESET);
            return;
        }
        int totalPages = (messages.size() + pageSize - 1) / pageSize;
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, messages.size());
        System.out.println(ANSI_CYAN + ANSI_BOLD + "=== History (page " + (currentPage + 1) + "/" + totalPages + ") ===" + ANSI_RESET);
        for (int i = start; i < end; i++) {
            String[] msg = messages.get(i);
            if (msg == null || msg.length < 6) continue;
            String msgUser = msg[0];
            String msgContent = msg[2];
            String msgTime = msg[5];
            boolean isSystemMsg = msg.length > 6 && Boolean.parseBoolean(msg[6]);
            if (isSystemMsg) {
                System.out.println(ANSI_CYAN + "[" + msgTime + "] *** " + msgContent + " ***" + ANSI_RESET);
            } else if (msgUser.equals(username)) {
                System.out.println(ANSI_GREEN + "[" + msgTime + "] " + ANSI_BOLD + msgUser +
                        ANSI_RESET + ANSI_GREEN + ": " + msgContent + ANSI_RESET);
            } else {
                String userColor = getCachedUserColor(msgUser);
                System.out.println(ANSI_WHITE + "[" + msgTime + "] " + userColor + ANSI_BOLD +
                        msgUser + ANSI_RESET + ANSI_WHITE + ": " + msgContent + ANSI_RESET);
            }
        }
        if (totalPages > 1) {
            System.out.println(ANSI_BLUE + "Page " + (currentPage + 1) + " of " + totalPages +
                    ". Use /history --page <n> to navigate." + ANSI_RESET);
        }
    }

    // Settings
    public void setColorEnabled(boolean enabled) { this.colorEnabled = enabled; }
    public void setTimestampsEnabled(boolean enabled) { this.timestampsEnabled = enabled; }
    public void setCompactMode(boolean compact) { this.compactMode = compact; }
    public void setSeparatorsEnabled(boolean enabled) { this.separatorsEnabled = enabled; }
    public void setTerminalWidth(int width) {
        if (width > 20 && width <= MAX_TERMINAL_WIDTH) {
            this.terminalWidth = width;
        }
    }
    public int getTerminalWidth() { return terminalWidth; }
    public int getMessageDisplayCount() { return messageDisplayCount; }

    private String getCachedUserColor(String username) {
        if (!userColorCache.containsKey(username)) {
            int hash = Math.abs(username.hashCode());
            userColorCache.put(username, USER_COLORS[hash % USER_COLORS.length]);
        }
        return userColorCache.get(username);
    }

    private String formatTimestamp(long timestamp) {
        if (!timestampsEnabled) return "";
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat));
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s;
        StringBuilder sb = new StringBuilder(s);
        for (int i = s.length(); i < n; i++) sb.append(' ');
        return sb.toString();
    }

    // Full dashboard rendering - shows room status, online users, and recent messages
    public void renderFullDashboard(String currentRoom, String currentUser, List<String[]> recentMessages,
                                     Set<String> onlineUsers, Map<String, String> userStatuses,
                                     Map<String, String> roomInfo, int unreadCount) {
        System.out.println();
        // Header bar
        StringBuilder headerBar = new StringBuilder();
        headerBar.append(ANSI_BG_BLUE).append(ANSI_BOLD).append(ANSI_WHITE);
        headerBar.append(" JavaChat ");
        if (currentRoom != null && !currentRoom.isEmpty()) {
            headerBar.append("| #").append(currentRoom).append(" ");
        }
        if (currentUser != null && !currentUser.isEmpty()) {
            headerBar.append("| ").append(currentUser).append(" ");
        }
        if (unreadCount > 0) {
            headerBar.append("| ").append(ANSI_YELLOW).append("[").append(unreadCount).append(" unread]").append(ANSI_WHITE);
        }
        headerBar.append(ANSI_RESET);
        System.out.println(headerBar);
        // Room topic if available
        if (roomInfo != null && !roomInfo.isEmpty()) {
            String topic = roomInfo.getOrDefault("topic", "");
            if (topic != null && !topic.isEmpty()) {
                System.out.println(ANSI_BLUE + "  Topic: " + ANSI_WHITE + topic + ANSI_RESET);
            }
            String memberCount = roomInfo.getOrDefault("memberCount", "0");
            System.out.println(ANSI_BLUE + "  Members: " + ANSI_WHITE + memberCount + ANSI_RESET);
        }
        printSeparator();
        // Recent messages section
        if (recentMessages != null && !recentMessages.isEmpty()) {
            System.out.println(ANSI_CYAN + ANSI_BOLD + "Recent Messages:" + ANSI_RESET);
            for (int i = 0; i < recentMessages.size(); i++) {
                String[] msg = recentMessages.get(i);
                if (msg == null || msg.length < 3) continue;
                String msgUser = msg.length > 0 ? msg[0] : "unknown";
                String msgContent = msg.length > 2 ? msg[2] : "";
                String msgTime = msg.length > 5 ? msg[5] : "??:??:??";
                boolean isSystem = msg.length > 6 && Boolean.parseBoolean(msg[6]);
                String msgType = msg.length > 3 ? msg[3] : "public";
                if (isSystem || "system".equals(msgType)) {
                    System.out.println(ANSI_CYAN + "  [" + msgTime + "] *** " + msgContent + " ***" + ANSI_RESET);
                } else if ("action".equals(msgType)) {
                    String userColor = getCachedUserColor(msgUser);
                    System.out.println(ANSI_PURPLE + "  [" + msgTime + "] * " + userColor + ANSI_BOLD + msgContent + ANSI_RESET);
                } else if ("private".equals(msgType)) {
                    System.out.println(ANSI_PURPLE + "  [" + msgTime + "] [PM] " + msgContent + ANSI_RESET);
                } else {
                    String userColor = getCachedUserColor(msgUser);
                    boolean isSelf = msgUser.equals(currentUser);
                    if (isSelf) {
                        System.out.println(ANSI_GREEN + "  [" + msgTime + "] " + ANSI_BOLD + msgUser +
                                ANSI_RESET + ANSI_GREEN + ": " + msgContent + ANSI_RESET);
                    } else if (msgContent.contains("@" + currentUser)) {
                        System.out.println(ANSI_YELLOW + "  [" + msgTime + "] " + userColor + ANSI_BOLD + msgUser +
                                ANSI_RESET + ANSI_YELLOW + ": " + msgContent + ANSI_RESET);
                    } else {
                        System.out.println(ANSI_WHITE + "  [" + msgTime + "] " + userColor + ANSI_BOLD + msgUser +
                                ANSI_RESET + ANSI_WHITE + ": " + msgContent + ANSI_RESET);
                    }
                }
            }
        } else {
            System.out.println(ANSI_YELLOW + "  No recent messages" + ANSI_RESET);
        }
        printSeparator();
        // Online users sidebar (compact)
        if (onlineUsers != null && !onlineUsers.isEmpty()) {
            System.out.println(ANSI_CYAN + ANSI_BOLD + "Online (" + onlineUsers.size() + "):" + ANSI_RESET);
            int shown = 0;
            int maxShow = 8;
            List<String> sorted = new ArrayList<>(onlineUsers);
            Collections.sort(sorted);
            for (String user : sorted) {
                if (shown >= maxShow) {
                    System.out.println(ANSI_WHITE + "  ... and " + (onlineUsers.size() - maxShow) + " more" + ANSI_RESET);
                    break;
                }
                String status = userStatuses != null ? userStatuses.getOrDefault(user, "online") : "online";
                String statusColor;
                String icon;
                if (status.equals("online")) {
                    statusColor = ANSI_GREEN;
                    icon = "●";
                } else if (status.equals("away")) {
                    statusColor = ANSI_YELLOW;
                    icon = "◑";
                } else if (status.equals("busy")) {
                    statusColor = ANSI_RED;
                    icon = "◌";
                } else {
                    statusColor = ANSI_WHITE;
                    icon = "○";
                }
                String userColor = getCachedUserColor(user);
                boolean isSelf = user.equals(currentUser);
                System.out.println("  " + statusColor + icon + " " + userColor + ANSI_BOLD + user +
                        ANSI_RESET + (isSelf ? ANSI_CYAN + " (you)" + ANSI_RESET : ""));
                shown++;
            }
        }
        System.out.println();
    }
}
