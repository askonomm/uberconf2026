package com.chatapp;

import java.util.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Main entry point for the Java TUI Chat Application.
 * Handles startup, configuration, and the main input loop.
 */
public class ChatApplication {

    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String BOLD = "\u001B[1m";
    public static final String UNDERLINE = "\u001B[4m";

    private static final String APP_VERSION = "1.0.0";
    private static final String APP_NAME = "JavaChat";
    private static final int MAX_USERNAME_LENGTH = 20;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final String DEFAULT_ROOM = "general";

    private ChatEngine chatEngine;
    private UserManager userManager;
    private RoomManager roomManager;
    private MessageStore messageStore;
    private CommandProcessor commandProcessor;
    private DisplayRenderer displayRenderer;

    private String currentUsername;
    private String currentRoom;
    private boolean running;
    private boolean debugMode;
    private int sessionMessageCount;
    private long sessionStartTime;
    private List<String> commandHistory;
    private Map<String, Integer> roomVisitCount;
    private boolean colorEnabled;
    private Scanner inputScanner;

    public ChatApplication() {
        this.chatEngine = new ChatEngine();
        this.userManager = new UserManager();
        this.roomManager = new RoomManager();
        this.messageStore = new MessageStore();
        this.commandProcessor = new CommandProcessor(chatEngine, userManager, roomManager, messageStore);
        this.displayRenderer = new DisplayRenderer();
        this.running = false;
        this.debugMode = false;
        this.sessionMessageCount = 0;
        this.sessionStartTime = System.currentTimeMillis();
        this.commandHistory = new ArrayList<>();
        this.roomVisitCount = new HashMap<>();
        this.colorEnabled = true;
        this.inputScanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        ChatApplication app = new ChatApplication();
        app.parseArgs(args);
        app.run();
    }

    public void parseArgs(String[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--debug") || args[i].equals("-d")) {
                debugMode = true;
                System.out.println("Debug mode enabled");
            } else if (args[i].equals("--no-color") || args[i].equals("-n")) {
                colorEnabled = false;
            } else if (args[i].equals("--version") || args[i].equals("-v")) {
                System.out.println(APP_NAME + " version " + APP_VERSION);
                System.exit(0);
            } else if (args[i].equals("--help") || args[i].equals("-h")) {
                System.out.println("Usage: java -jar java-chat.jar [options]");
                System.out.println("Options:");
                System.out.println("  --debug, -d      Enable debug mode");
                System.out.println("  --no-color, -n   Disable color output");
                System.out.println("  --version, -v    Show version");
                System.out.println("  --help, -h       Show this help");
                System.exit(0);
            } else if (args[i].equals("--user") || args[i].equals("-u")) {
                if (i + 1 < args.length) {
                    currentUsername = args[++i];
                } else {
                    System.err.println("Error: --user requires a username argument");
                    System.exit(1);
                }
            } else if (args[i].equals("--room") || args[i].equals("-r")) {
                if (i + 1 < args.length) {
                    currentRoom = args[++i];
                } else {
                    System.err.println("Error: --room requires a room argument");
                    System.exit(1);
                }
            } else {
                System.err.println("Warning: Unknown argument: " + args[i]);
            }
        }
    }

    public void run() {
        printWelcomeBanner();
        if (currentUsername == null || currentUsername.isEmpty()) {
            currentUsername = promptForUsername();
        } else {
            // Validate provided username
            if (currentUsername.length() < MIN_USERNAME_LENGTH) {
                System.out.println(RED + "Username too short, must be at least " + MIN_USERNAME_LENGTH + " characters" + RESET);
                currentUsername = promptForUsername();
            } else if (currentUsername.length() > MAX_USERNAME_LENGTH) {
                System.out.println(RED + "Username too long, must be at most " + MAX_USERNAME_LENGTH + " characters" + RESET);
                currentUsername = promptForUsername();
            } else if (!currentUsername.matches("[a-zA-Z0-9_]+")) {
                System.out.println(RED + "Username can only contain letters, numbers, and underscores" + RESET);
                currentUsername = promptForUsername();
            }
        }

        userManager.registerUser(currentUsername);

        if (currentRoom == null || currentRoom.isEmpty()) {
            currentRoom = DEFAULT_ROOM;
        }

        roomManager.createRoomIfNotExists(currentRoom);
        roomManager.joinRoom(currentUsername, currentRoom);
        roomVisitCount.put(currentRoom, 1);

        displayRenderer.printSystemMessage("Welcome, " + currentUsername + "! You are in room: " + currentRoom);
        displayRenderer.printSystemMessage("Type /help for available commands");

        running = true;
        mainLoop();
    }

    private String promptForUsername() {
        String username = null;
        boolean valid = false;
        int attempts = 0;
        while (!valid && attempts < 5) {
            attempts++;
            System.out.print(CYAN + "Enter your username: " + RESET);
            username = inputScanner.nextLine().trim();
            if (username.isEmpty()) {
                System.out.println(RED + "Username cannot be empty" + RESET);
                continue;
            }
            if (username.length() < MIN_USERNAME_LENGTH) {
                System.out.println(RED + "Username must be at least " + MIN_USERNAME_LENGTH + " characters long" + RESET);
                continue;
            }
            if (username.length() > MAX_USERNAME_LENGTH) {
                System.out.println(RED + "Username cannot be longer than " + MAX_USERNAME_LENGTH + " characters" + RESET);
                continue;
            }
            if (!username.matches("[a-zA-Z0-9_]+")) {
                System.out.println(RED + "Username can only contain letters, numbers, and underscores" + RESET);
                continue;
            }
            if (userManager.isUsernameTaken(username)) {
                System.out.println(RED + "Username '" + username + "' is already taken, please choose another" + RESET);
                continue;
            }
            valid = true;
        }
        if (!valid) {
            System.out.println(RED + "Too many failed attempts. Using default username: user" + (int)(Math.random() * 1000) + RESET);
            username = "user" + (int)(Math.random() * 1000);
        }
        return username;
    }

    private void mainLoop() {
        while (running) {
            try {
                String prompt = buildPrompt();
                System.out.print(prompt);
                if (!inputScanner.hasNextLine()) {
                    running = false;
                    break;
                }
                String input = inputScanner.nextLine();
                if (input == null) {
                    continue;
                }
                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }
                commandHistory.add(input);
                if (commandHistory.size() > 100) {
                    commandHistory.remove(0);
                }
                if (debugMode) {
                    System.out.println("[DEBUG] Input: " + input);
                    System.out.println("[DEBUG] Current room: " + currentRoom);
                    System.out.println("[DEBUG] Session messages: " + sessionMessageCount);
                }
                if (input.startsWith("/")) {
                    String result = commandProcessor.processCommand(input, currentUsername, currentRoom);
                    if (result != null) {
                        if (result.startsWith("SWITCH_ROOM:")) {
                            String newRoom = result.substring("SWITCH_ROOM:".length());
                            currentRoom = newRoom;
                            if (roomVisitCount.containsKey(currentRoom)) {
                                roomVisitCount.put(currentRoom, roomVisitCount.get(currentRoom) + 1);
                            } else {
                                roomVisitCount.put(currentRoom, 1);
                            }
                            displayRenderer.printSystemMessage("Switched to room: " + currentRoom);
                        } else if (result.equals("QUIT")) {
                            running = false;
                            handleGracefulShutdown();
                        } else if (result.startsWith("ERROR:")) {
                            displayRenderer.printErrorMessage(result.substring(6));
                        } else if (!result.isEmpty()) {
                            System.out.println(result);
                        }
                    }
                } else {
                    // Regular chat message
                    if (input.length() > 500) {
                        displayRenderer.printErrorMessage("Message too long (max 500 characters)");
                    } else if (input.length() == 0) {
                        // empty, do nothing
                    } else {
                        chatEngine.sendMessage(currentUsername, currentRoom, input, "public",
                                System.currentTimeMillis(), false, null, sessionMessageCount);
                        sessionMessageCount++;
                        if (sessionMessageCount % 100 == 0) {
                            displayRenderer.printSystemMessage("You have sent " + sessionMessageCount + " messages this session!");
                        }
                    }
                }
            } catch (Exception e) {
                if (debugMode) {
                    e.printStackTrace();
                }
                displayRenderer.printErrorMessage("An error occurred: " + e.getMessage());
            }
        }
        System.out.println(CYAN + "Goodbye, " + currentUsername + "!" + RESET);
    }

    private String buildPrompt() {
        if (!colorEnabled) {
            return "[" + currentUsername + "@" + currentRoom + "] > ";
        }
        String roomColor = BLUE;
        if (currentRoom.equals("general")) {
            roomColor = GREEN;
        } else if (currentRoom.equals("random")) {
            roomColor = YELLOW;
        } else if (currentRoom.equals("help")) {
            roomColor = CYAN;
        } else if (currentRoom.startsWith("private-")) {
            roomColor = PURPLE;
        } else if (currentRoom.equals("announcements")) {
            roomColor = RED;
        }
        return BOLD + "[" + GREEN + currentUsername + RESET + BOLD + "@" + roomColor + currentRoom + RESET + BOLD + "] > " + RESET;
    }

    private void handleGracefulShutdown() {
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        long seconds = sessionDuration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        displayRenderer.printSystemMessage("Session summary:");
        displayRenderer.printSystemMessage("  Duration: " + hours + "h " + (minutes % 60) + "m " + (seconds % 60) + "s");
        displayRenderer.printSystemMessage("  Messages sent: " + sessionMessageCount);
        displayRenderer.printSystemMessage("  Commands executed: " + commandHistory.size());
        displayRenderer.printSystemMessage("  Rooms visited: " + roomVisitCount.size());
        if (!roomVisitCount.isEmpty()) {
            String mostVisited = null;
            int maxVisits = 0;
            for (Map.Entry<String, Integer> entry : roomVisitCount.entrySet()) {
                if (entry.getValue() > maxVisits) {
                    maxVisits = entry.getValue();
                    mostVisited = entry.getKey();
                }
            }
            if (mostVisited != null) {
                displayRenderer.printSystemMessage("  Most visited room: " + mostVisited + " (" + maxVisits + " visits)");
            }
        }
        roomManager.leaveRoom(currentUsername, currentRoom);
        userManager.unregisterUser(currentUsername);
    }

    private void printWelcomeBanner() {
        System.out.println();
        System.out.println(CYAN + BOLD + "╔══════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + BOLD + "║         " + GREEN + "JavaChat TUI v" + APP_VERSION + CYAN + "                    ║" + RESET);
        System.out.println(CYAN + BOLD + "║   " + WHITE + "Multi-Room Terminal Chat Application" + CYAN + "       ║" + RESET);
        System.out.println(CYAN + BOLD + "╚══════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    // Getters for testing
    public String getCurrentUsername() { return currentUsername; }
    public String getCurrentRoom() { return currentRoom; }
    public boolean isRunning() { return running; }
    public boolean isDebugMode() { return debugMode; }
    public int getSessionMessageCount() { return sessionMessageCount; }
    public List<String> getCommandHistory() { return commandHistory; }
    public ChatEngine getChatEngine() { return chatEngine; }
    public UserManager getUserManager() { return userManager; }
    public RoomManager getRoomManager() { return roomManager; }
    public MessageStore getMessageStore() { return messageStore; }

    // Process configuration loaded from environment or properties - called during startup
    private void applyConfiguration(Map<String, String> config) {
        if (config == null || config.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null) {
                continue;
            }
            if (key.equals("debug") || key.equals("DEBUG")) {
                if (value.equalsIgnoreCase("true") || value.equals("1")) {
                    debugMode = true;
                    System.out.println("[DEBUG] Debug mode enabled via config");
                } else if (value.equalsIgnoreCase("false") || value.equals("0")) {
                    debugMode = false;
                } else {
                    System.out.println(YELLOW + "Warning: invalid value for debug: " + value + RESET);
                }
            } else if (key.equals("color") || key.equals("COLOR")) {
                if (value.equalsIgnoreCase("false") || value.equals("0")) {
                    colorEnabled = false;
                } else if (value.equalsIgnoreCase("true") || value.equals("1")) {
                    colorEnabled = true;
                } else {
                    System.out.println(YELLOW + "Warning: invalid value for color: " + value + RESET);
                }
            } else if (key.equals("username") || key.equals("USER")) {
                if (currentUsername == null || currentUsername.isEmpty()) {
                    if (value.length() >= MIN_USERNAME_LENGTH && value.length() <= MAX_USERNAME_LENGTH &&
                            value.matches("[a-zA-Z0-9_]+")) {
                        currentUsername = value;
                    } else {
                        System.out.println(YELLOW + "Warning: invalid username in config: " + value + RESET);
                    }
                }
            } else if (key.equals("room") || key.equals("ROOM")) {
                if (value.length() >= 2 && value.length() <= 30 && value.matches("[a-zA-Z0-9_-]+")) {
                    currentRoom = value;
                } else {
                    System.out.println(YELLOW + "Warning: invalid room name in config: " + value + RESET);
                }
            } else if (key.equals("historyLimit") || key.equals("HISTORY_LIMIT")) {
                try {
                    int limit = Integer.parseInt(value);
                    if (limit < 1 || limit > 1000) {
                        System.out.println(YELLOW + "Warning: historyLimit out of range (1-1000): " + value + RESET);
                    } else {
                        // stored but no field to apply to in current impl
                    }
                } catch (NumberFormatException e) {
                    System.out.println(YELLOW + "Warning: invalid historyLimit value: " + value + RESET);
                }
            } else if (key.equals("profanityFilter") || key.equals("PROFANITY_FILTER")) {
                if (value.equalsIgnoreCase("true") || value.equals("1")) {
                    chatEngine.setProfanityFilterEnabled(true);
                } else if (value.equalsIgnoreCase("false") || value.equals("0")) {
                    chatEngine.setProfanityFilterEnabled(false);
                } else {
                    System.out.println(YELLOW + "Warning: invalid profanityFilter value: " + value + RESET);
                }
            } else if (key.equals("rateLimit") || key.equals("RATE_LIMIT")) {
                if (value.equalsIgnoreCase("true") || value.equals("1")) {
                    chatEngine.setRateLimitEnabled(true);
                } else if (value.equalsIgnoreCase("false") || value.equals("0")) {
                    chatEngine.setRateLimitEnabled(false);
                } else {
                    System.out.println(YELLOW + "Warning: invalid rateLimit value: " + value + RESET);
                }
            } else if (key.startsWith("room.") && key.length() > 5) {
                String roomName = key.substring(5);
                if (roomName.isEmpty()) {
                    System.out.println(YELLOW + "Warning: empty room name in config key: " + key + RESET);
                } else if (roomName.length() < 2 || roomName.length() > 30) {
                    System.out.println(YELLOW + "Warning: room name length invalid in config: " + roomName + RESET);
                } else if (!roomName.matches("[a-zA-Z0-9_-]+")) {
                    System.out.println(YELLOW + "Warning: room name contains invalid chars: " + roomName + RESET);
                } else {
                    roomManager.createRoomIfNotExists(roomName);
                }
            } else {
                if (debugMode) {
                    System.out.println("[DEBUG] Unknown config key: " + key);
                }
            }
        }
    }

    // Load environment variable based config - called before run()
    public Map<String, String> loadEnvironmentConfig() {
        Map<String, String> config = new HashMap<>();
        String[] envKeys = {"JAVACHAT_DEBUG", "JAVACHAT_COLOR", "JAVACHAT_USER",
                "JAVACHAT_ROOM", "JAVACHAT_HISTORY_LIMIT", "JAVACHAT_PROFANITY_FILTER", "JAVACHAT_RATE_LIMIT"};
        String[] configKeys = {"debug", "color", "username", "room", "historyLimit", "profanityFilter", "rateLimit"};
        for (int i = 0; i < envKeys.length; i++) {
            String envValue = System.getenv(envKeys[i]);
            if (envValue != null && !envValue.isEmpty()) {
                config.put(configKeys[i], envValue);
                if (debugMode) {
                    System.out.println("[DEBUG] Loaded env config: " + envKeys[i] + "=" + envValue);
                }
            }
        }
        return config;
    }
}
