package com.chatapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JavaChat TUI Application.
 *
 * // TODO: Add tests
 */
public class ChatApplicationTest {

    private ChatApplication app;
    private ChatEngine chatEngine;
    private UserManager userManager;
    private RoomManager roomManager;
    private MessageStore messageStore;
    private CommandProcessor commandProcessor;

    @BeforeEach
    public void setUp() {
        chatEngine = new ChatEngine();
        userManager = new UserManager();
        roomManager = new RoomManager();
        messageStore = new MessageStore();
        commandProcessor = new CommandProcessor(chatEngine, userManager, roomManager, messageStore);
    }

    // TODO: Add tests
}
