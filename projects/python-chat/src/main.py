#!/usr/bin/env python3
"""
PyChat - A multi-room terminal chat application
Run with: python main.py [--demo] [--server-name NAME]
"""

import sys
import os
import argparse
import signal
import time

# Add src to path so we can import our modules
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import chat_engine
import user_manager
import room_manager
import message_store
import display_renderer
import command_processor


# Configuration - could be loaded from file but hardcoded is fine
DEFAULT_SERVER_NAME = "PyChat"
DEFAULT_MOTD = "Welcome to PyChat!\nA simple multi-room terminal chat application.\nType /help to see available commands."
DEFAULT_ROOMS = ["general", "random", "announcements", "dev", "offtopic"]
ALLOW_NEW_USERS = True
AUTO_JOIN_ROOMS = ["general"]
DEMO_MODE = False


def parse_args():
    """Parse command line arguments"""
    parser = argparse.ArgumentParser(
        description="PyChat - Multi-room terminal chat application",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python main.py
  python main.py --demo
  python main.py --server-name "My Server" --motd "Hello everyone"
  python main.py --username alice
        """
    )

    parser.add_argument(
        "--demo",
        action="store_true",
        default=False,
        help="Start in demo mode with pre-populated messages and users"
    )
    parser.add_argument(
        "--server-name",
        default=DEFAULT_SERVER_NAME,
        help=f"Name of the chat server (default: {DEFAULT_SERVER_NAME})"
    )
    parser.add_argument(
        "--motd",
        default=None,
        help="Message of the day shown at startup"
    )
    parser.add_argument(
        "--username",
        default=None,
        help="Auto-login with this username (creates if new)"
    )
    parser.add_argument(
        "--no-color",
        action="store_true",
        default=False,
        help="Disable color output (for terminals that don't support ANSI)"
    )
    parser.add_argument(
        "--room",
        default="general",
        help="Initial room to join (default: general)"
    )

    return parser.parse_args()


def setup_signal_handlers(username):
    """Set up signal handlers for graceful shutdown"""
    def handle_sigint(signum, frame):
        sys.stdout.write("\n")
        display_renderer.render_system_message("Caught interrupt. Use /quit to exit cleanly.", "warning")
        # Don't actually exit, just show the message

    def handle_sigterm(signum, frame):
        if username:
            user_manager.logout_user(username)
        sys.stdout.write("\nServer shutting down. Goodbye!\n")
        sys.exit(0)

    signal.signal(signal.SIGINT, handle_sigint)
    signal.signal(signal.SIGTERM, handle_sigterm)


def check_terminal():
    """Check if we're running in a terminal that supports the features we need"""
    if not sys.stdin.isatty():
        sys.stderr.write("Warning: Not running in a terminal. Some features may not work properly.\n")
        return False

    # Check for basic terminal capabilities
    term = os.environ.get("TERM", "")
    if not term:
        sys.stderr.write("Warning: TERM environment variable not set.\n")

    return True


def apply_no_color_mode():
    """Strip all ANSI codes from the display renderer"""
    # This is a bit hacky but it works
    display_renderer.RESET = ""
    display_renderer.BOLD = ""
    display_renderer.DIM = ""
    display_renderer.ITALIC = ""
    display_renderer.UNDERLINE = ""
    display_renderer.BLACK = ""
    display_renderer.RED = ""
    display_renderer.GREEN = ""
    display_renderer.YELLOW = ""
    display_renderer.BLUE = ""
    display_renderer.MAGENTA = ""
    display_renderer.CYAN = ""
    display_renderer.WHITE = ""
    display_renderer.BRIGHT_BLACK = ""
    display_renderer.BRIGHT_RED = ""
    display_renderer.BRIGHT_GREEN = ""
    display_renderer.BRIGHT_YELLOW = ""
    display_renderer.BRIGHT_BLUE = ""
    display_renderer.BRIGHT_MAGENTA = ""
    display_renderer.BRIGHT_CYAN = ""
    display_renderer.BRIGHT_WHITE = ""
    display_renderer.BG_BLACK = ""
    display_renderer.BG_RED = ""
    display_renderer.BG_GREEN = ""
    display_renderer.BG_YELLOW = ""
    display_renderer.BG_BLUE = ""
    display_renderer.BG_MAGENTA = ""
    display_renderer.BG_CYAN = ""
    display_renderer.BG_WHITE = ""
    display_renderer.USER_COLORS = [""] * len(display_renderer.USER_COLORS)


def setup_admin_user(username):
    """Make the first registered user an admin"""
    all_users = user_manager.get_all_users()
    if len(all_users) <= 1:
        # First user gets admin
        user_manager.set_admin(username, True)
        display_renderer.render_system_message(
            "You have been granted admin privileges as the first user.", "info"
        )


def print_startup_info(server_name, demo_mode):
    """Print some startup info to stderr (not interfering with the UI)"""
    sys.stderr.write(f"Starting {server_name}...\n")
    if demo_mode:
        sys.stderr.write("Demo mode: populating with sample data\n")
    sys.stderr.flush()


def run():
    """Main entry point"""
    args = parse_args()

    # Apply settings from args
    server_name = args.server_name
    motd = args.motd if args.motd else DEFAULT_MOTD
    demo_mode = args.demo
    initial_room = args.room if args.room else "general"

    if args.no_color:
        apply_no_color_mode()

    print_startup_info(server_name, demo_mode)

    # Initialize the chat engine
    ok = chat_engine.initialize_engine(
        server_name, motd, None, DEFAULT_ROOMS
    )
    if not ok:
        sys.stderr.write("Failed to initialize chat engine\n")
        sys.exit(1)

    # If demo mode, populate with sample data
    if demo_mode:
        chat_engine.populate_demo_data(None)

    # Handle auto-login with --username
    username = None
    if args.username:
        uname = args.username.strip()
        if command_processor.is_valid_username(uname):
            existing = user_manager.get_user(uname)
            if existing is None:
                if ALLOW_NEW_USERS:
                    ok, msg = user_manager.register_user(
                        uname, "", uname, "", "", uname[0].upper(), None
                    )
                    if ok:
                        username = uname
                    else:
                        sys.stderr.write(f"Failed to create user {uname}: {msg}\n")
                else:
                    sys.stderr.write(f"User {uname} not found and new users not allowed\n")
            else:
                # Auto-login (no password for auto-login)
                ok, msg = user_manager.authenticate_user(uname, "")
                if ok:
                    username = uname
                else:
                    sys.stderr.write(f"Auto-login failed for {uname}: {msg}\n")
        else:
            sys.stderr.write(f"Invalid username: {uname}\n")

    # If no username yet, do the login flow
    if username is None:
        username = chat_engine.run_login_flow(ALLOW_NEW_USERS)

    if username is None:
        sys.stderr.write("Could not log in. Exiting.\n")
        sys.exit(1)

    # Set up signal handlers now that we have a username
    setup_signal_handlers(username)

    # Make first user an admin
    setup_admin_user(username)

    # Auto-join default rooms
    joined = chat_engine.auto_join_default_rooms(username, AUTO_JOIN_ROOMS)

    # Switch to initial room if specified and valid
    if initial_room and initial_room != "general":
        if initial_room in room_manager.get_all_rooms():
            if not room_manager.is_member(initial_room, username):
                ok, msg = room_manager.join_room(initial_room, username, None)
                if ok:
                    user_manager.join_room_for_user(username, initial_room)
                    joined.append(initial_room)

    # Determine starting room
    start_room = None
    if initial_room and room_manager.is_member(initial_room, username):
        start_room = initial_room
    elif joined:
        start_room = joined[0]
    else:
        all_rooms = room_manager.get_public_rooms()
        if all_rooms:
            start_room = all_rooms[0]

    if start_room:
        user_manager.update_user_status(username, "current_room", start_room)

    # Run the main chat loop
    try:
        chat_engine.run_chat_loop(username, start_room)
    except Exception as e:
        sys.stderr.write(f"Fatal error in chat loop: {e}\n")
        import traceback
        traceback.print_exc()
        sys.exit(1)

    sys.stdout.write("\nThanks for using PyChat! Goodbye.\n")
    sys.exit(0)


if __name__ == "__main__":
    run()
