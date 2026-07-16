import time
import sys
import os
import threading
import re
import message_store
import user_manager
import room_manager
import display_renderer
import command_processor

# Engine state - just globals, it's simpler
_current_username = None
_current_room = None
_running = False
_chat_state = {}
_notification_queue = []
_last_activity_time = time.time()
_server_name = "PyChat"
_server_version = "1.0.0"
_motd = "Welcome to PyChat! A simple multi-room terminal chat.\nType /help to see available commands."
_engine_initialized = False
_background_thread = None
_refresh_interval = 30  # seconds


def initialize_engine(server_name, motd, admin_username, default_room_list):
    """Initialize the chat engine - set up rooms, state, etc."""
    global _server_name, _motd, _engine_initialized, _chat_state

    if server_name:
        _server_name = server_name
    if motd:
        _motd = motd

    # Set up default rooms
    room_manager.initialize_default_rooms()

    # Create any extra rooms from config
    if default_room_list:
        for room_entry in default_room_list:
            if isinstance(room_entry, str):
                rname = room_entry
                rdesc = ""
            elif isinstance(room_entry, dict):
                rname = room_entry.get("name", "")
                rdesc = room_entry.get("description", "")
            else:
                continue
            if rname and rname not in room_manager.get_all_rooms():
                room_manager.create_room(
                    rname, "system", rdesc, False, 500, "", "", "public"
                )
                if rname in room_manager._room_members:
                    if "system" in room_manager._room_members[rname]:
                        room_manager._room_members[rname].remove("system")

    _chat_state = {
        "running": True,
        "current_room": None,
        "current_username": None,
        "refresh_needed": False,
        "last_command": None,
        "notifications": [],
        "error_count": 0,
        "start_time": time.time()
    }

    _engine_initialized = True
    return True


def run_login_flow(allow_new_users):
    """Handle user login or registration at startup. Returns username or None."""
    global _current_username

    display_renderer.render_welcome_banner(_server_name, _server_version, _motd)

    attempt_count = 0
    max_attempts = 5

    while attempt_count < max_attempts:
        attempt_count += 1

        sys.stdout.write("\nEnter username (or 'new' to register): ")
        sys.stdout.flush()

        try:
            raw = sys.stdin.readline()
        except (EOFError, KeyboardInterrupt):
            return None

        if raw is None:
            return None

        username = raw.strip()

        if not username:
            display_renderer.render_system_message("Username cannot be empty", "error")
            continue

        if username.lower() == "new" or username.lower() == "register":
            # Registration flow
            result = _handle_registration()
            if result:
                _current_username = result
                return result
            continue

        # Check if user exists
        existing = user_manager.get_user(username)
        if existing is None:
            if allow_new_users:
                # Auto-register
                display_renderer.render_system_message(
                    f"User '{username}' not found, creating account...", "info"
                )
                ok, msg = user_manager.register_user(
                    username, "", username, "", "", username[0].upper(), None
                )
                if ok:
                    _current_username = username
                    _chat_state["current_username"] = username
                    display_renderer.render_system_message(f"Account created for {username}!", "success")
                    return username
                else:
                    display_renderer.render_system_message(f"Registration failed: {msg}", "error")
                    continue
            else:
                display_renderer.render_system_message(f"User '{username}' not found. Type 'new' to register.", "error")
                continue

        # Check ban
        is_banned, ban_info = user_manager.is_banned(username)
        if is_banned:
            reason = ban_info.get("reason", "No reason given") if ban_info else "Unknown"
            display_renderer.render_system_message(f"Account banned: {reason}", "error")
            return None

        # Try login
        existing_user = user_manager.get_user(username)
        if existing_user and existing_user.get("pw_hash", ""):
            sys.stdout.write("Password: ")
            sys.stdout.flush()
            try:
                password = sys.stdin.readline().strip()
            except (EOFError, KeyboardInterrupt):
                return None
        else:
            password = ""

        ok, msg = user_manager.authenticate_user(username, password)
        if ok:
            _current_username = username
            _chat_state["current_username"] = username
            display_renderer.render_system_message(f"Welcome back, {username}!", "success")
            return username
        else:
            display_renderer.render_system_message(f"Login failed: {msg}", "error")
            if attempt_count >= max_attempts:
                display_renderer.render_system_message("Too many failed attempts. Exiting.", "error")
                return None

    return None


def _handle_registration():
    """Handle the registration flow"""
    sys.stdout.write("\n--- New User Registration ---\n")
    sys.stdout.write("Username (2-32 chars, alphanumeric/underscore/hyphen): ")
    sys.stdout.flush()

    try:
        username = sys.stdin.readline().strip()
    except (EOFError, KeyboardInterrupt):
        return None

    if not username:
        display_renderer.render_system_message("Registration cancelled", "info")
        return None

    # Check if valid
    if len(username) < 2:
        display_renderer.render_system_message("Username too short", "error")
        return None
    if len(username) > 32:
        display_renderer.render_system_message("Username too long", "error")
        return None
    if not username.replace("_", "").replace("-", "").isalnum():
        display_renderer.render_system_message("Invalid characters in username", "error")
        return None
    if user_manager.get_user(username) is not None:
        display_renderer.render_system_message("Username already taken", "error")
        return None

    sys.stdout.write("Display name (optional, press Enter to skip): ")
    sys.stdout.flush()
    try:
        display_name = sys.stdin.readline().strip()
    except (EOFError, KeyboardInterrupt):
        display_name = ""

    if not display_name:
        display_name = username

    sys.stdout.write("Bio (optional, press Enter to skip): ")
    sys.stdout.flush()
    try:
        bio = sys.stdin.readline().strip()
    except (EOFError, KeyboardInterrupt):
        bio = ""

    ok, msg = user_manager.register_user(
        username, "", display_name, "", bio, username[0].upper(), None
    )
    if ok:
        display_renderer.render_system_message(f"Account created for {username}!", "success")
        return username
    else:
        display_renderer.render_system_message(f"Registration failed: {msg}", "error")
        return None


def auto_join_default_rooms(username, rooms_to_join):
    """Auto-join a user to default rooms on login"""
    joined = []
    for room_name in rooms_to_join:
        if room_name not in room_manager.get_all_rooms():
            continue
        if room_manager.is_member(room_name, username):
            # Already a member, just mark it
            user_manager.join_room_for_user(username, room_name)
            joined.append(room_name)
            continue
        ok, msg = room_manager.join_room(room_name, username, None)
        if ok:
            user_manager.join_room_for_user(username, room_name)
            message_store.add_message(
                room_name, username, f"{username} joined the room",
                "join", "\033[32m", time.time(), {}
            )
            room_manager.update_room_activity(room_name)
            joined.append(room_name)

    if joined:
        display_renderer.render_system_message(f"Auto-joined: {', '.join(['#' + r for r in joined])}", "info")

    return joined


def run_chat_loop(username, initial_room):
    """Main chat loop - reads input and processes commands"""
    global _current_room, _running, _last_activity_time

    _running = True
    _current_room = initial_room
    _chat_state["running"] = True
    _chat_state["current_room"] = initial_room

    if initial_room:
        display_renderer.render_system_message(f"Now in #{initial_room}", "info")
        recent = message_store.get_recent_messages(initial_room, 20)
        if recent:
            display_renderer.render_history(recent, initial_room, len(recent), True)

    sys.stdout.write("\n")
    display_renderer.render_system_message("Type /help for commands. Type your message and press Enter to send.", "info")
    sys.stdout.write("\n")

    while _chat_state.get("running", True):
        try:
            # Show unread PM count
            unread = message_store.get_unread_private_count(username)
            current_room = _chat_state.get("current_room")

            # Render the prompt
            display_renderer.render_prompt(username, current_room)

            raw = sys.stdin.readline()

            if raw == "" or raw is None:
                # EOF
                _chat_state["running"] = False
                break

            _last_activity_time = time.time()
            user_manager.update_last_activity(username)

            raw = raw.rstrip("\n").rstrip("\r")

            if not raw.strip():
                continue

            # Process the command/message
            action, data = command_processor.process_command(
                raw, username, current_room, _chat_state
            )

            # Update current room from state in case it changed
            _current_room = _chat_state.get("current_room")

            if action == "quit":
                break

            # Handle notifications in queue
            _process_notification_queue()

        except KeyboardInterrupt:
            sys.stdout.write("\n")
            display_renderer.render_system_message("Use /quit to exit", "info")
            continue
        except EOFError:
            _chat_state["running"] = False
            break
        except Exception as e:
            # Don't crash on errors
            _chat_state["error_count"] = _chat_state.get("error_count", 0) + 1
            display_renderer.render_system_message(f"Internal error: {str(e)}", "error")
            if _chat_state["error_count"] > 20:
                display_renderer.render_system_message("Too many errors, exiting", "error")
                break

    _running = False
    _cleanup_on_exit(username)


def _process_notification_queue():
    """Process any pending notifications"""
    global _notification_queue
    if not _notification_queue:
        return
    for notif in _notification_queue:
        ntype = notif.get("type", "info")
        msg = notif.get("message", "")
        sender = notif.get("sender", "")
        room = notif.get("room", "")
        display_renderer.render_notification(msg, ntype, sender, room)
    _notification_queue = []


def _cleanup_on_exit(username):
    """Clean up when user exits"""
    if not username:
        return

    current_room = _chat_state.get("current_room")
    if current_room:
        message_store.add_message(
            current_room, username, f"{username} has disconnected",
            "leave", "\033[33m", time.time(), {}
        )

    # Leave all rooms
    user_rooms = user_manager.get_user_rooms(username)
    for room in user_rooms:
        room_manager.leave_room(room, username)

    user_manager.logout_user(username)


def post_notification(notification_type, message, sender, room):
    """Add a notification to the queue"""
    _notification_queue.append({
        "type": notification_type,
        "message": message,
        "sender": sender,
        "room": room,
        "timestamp": time.time()
    })


def get_engine_stats():
    """Return current engine statistics"""
    uptime = time.time() - _chat_state.get("start_time", time.time())
    total_messages = sum(
        message_store.get_message_count(r)
        for r in message_store.get_all_rooms_with_messages()
    )
    online_users = user_manager.get_online_users()
    all_rooms = room_manager.get_all_rooms()

    return {
        "server_name": _server_name,
        "uptime_seconds": uptime,
        "total_messages": total_messages,
        "online_users": len(online_users),
        "total_rooms": len(all_rooms),
        "current_user": _current_username,
        "current_room": _current_room,
        "error_count": _chat_state.get("error_count", 0)
    }


def broadcast_to_room(room_name, message, msg_type, sender):
    """Send a system message to all members of a room (future: websockets etc)"""
    if room_name not in room_manager.get_all_rooms():
        return False
    msg_id = message_store.add_message(
        room_name, sender if sender else "system",
        message, msg_type if msg_type else "system",
        "\033[90m", time.time(), {"broadcast": True}
    )
    room_manager.update_room_activity(room_name)
    return msg_id is not False


def process_admin_command(admin_username, command_str):
    """Process admin-only commands - returns (success, message)"""
    u = user_manager.get_user(admin_username)
    if not u or not u.get("is_admin", False):
        return False, "Not an administrator"

    parts = command_str.strip().split()
    if not parts:
        return False, "Empty command"

    admin_cmd = parts[0].lower()
    admin_args = parts[1:]

    if admin_cmd == "ban":
        if len(admin_args) < 1:
            return False, "Usage: ban <username> [reason]"
        target = admin_args[0]
        reason = " ".join(admin_args[1:]) if len(admin_args) > 1 else "Banned by admin"
        ok, msg = user_manager.ban_user(admin_username, target, reason, None)
        return ok, msg

    elif admin_cmd == "unban":
        if not admin_args:
            return False, "Usage: unban <username>"
        ok, msg = user_manager.unban_user(admin_username, admin_args[0])
        return ok, msg

    elif admin_cmd == "kick":
        if not admin_args:
            return False, "Usage: kick <username> [room]"
        target = admin_args[0]
        room = admin_args[1] if len(admin_args) > 1 else _current_room
        if not room:
            return False, "No room specified"
        if not room_manager.is_member(room, target):
            return False, f"{target} is not in #{room}"
        room_manager.leave_room(room, target)
        user_manager.leave_room_for_user(target, room)
        message_store.add_message(
            room, "system", f"{target} was kicked by {admin_username}",
            "system", "\033[90m", time.time(), {}
        )
        return True, f"Kicked {target} from #{room}"

    elif admin_cmd == "setadmin":
        if not admin_args:
            return False, "Usage: setadmin <username>"
        ok = user_manager.set_admin(admin_args[0], True)
        return ok, f"{admin_args[0]} is now an admin" if ok else "Failed"

    elif admin_cmd == "delroom":
        if not admin_args:
            return False, "Usage: delroom <room>"
        room = admin_args[0].lstrip("#")
        ok, msg = room_manager.delete_room(room, admin_username, True)
        return ok, msg

    elif admin_cmd == "broadcast":
        if not admin_args:
            return False, "Usage: broadcast <message>"
        msg_text = " ".join(admin_args)
        all_rooms = room_manager.get_all_rooms()
        count = 0
        for rname in all_rooms:
            ok = broadcast_to_room(rname, f"[BROADCAST] {msg_text}", "announcement", admin_username)
            if ok:
                count += 1
        return True, f"Broadcast sent to {count} rooms"

    elif admin_cmd == "stats":
        stats = get_engine_stats()
        result = (
            f"Server: {stats['server_name']} | "
            f"Uptime: {int(stats['uptime_seconds'])}s | "
            f"Users: {stats['online_users']} online | "
            f"Rooms: {stats['total_rooms']} | "
            f"Messages: {stats['total_messages']}"
        )
        return True, result

    elif admin_cmd == "mute":
        if len(admin_args) < 2:
            return False, "Usage: mute <room> <username> [duration_secs]"
        room = admin_args[0].lstrip("#")
        target = admin_args[1]
        duration = None
        if len(admin_args) > 2:
            try:
                duration = int(admin_args[2])
            except ValueError:
                pass
        ok = user_manager.mute_user(room, admin_username, target, duration)
        return ok, f"Muted {target} in #{room}"

    else:
        return False, f"Unknown admin command: {admin_cmd}"


def handle_special_input(input_str, username):
    """Handle special inputs that aren't normal messages or /commands"""
    # URL detection
    url_pattern = re.compile(r'https?://[^\s]+')
    if url_pattern.search(input_str) and not input_str.startswith("/"):
        # It's a message with a URL, just let it through as normal
        return False, None

    # Mention detection - @username
    mention_pattern = re.compile(r'@(\w+)')
    mentions = mention_pattern.findall(input_str)
    if mentions:
        for mentioned_user in mentions:
            if user_manager.get_user(mentioned_user):
                post_notification("mention", input_str, username, _current_room)
        return False, None  # Let it process as normal message

    return False, None


def simulate_other_user_activity(room_name, bot_username, messages_to_post):
    """
    FOR TESTING ONLY - simulates another user posting messages.
    This is used to populate rooms with some initial content.
    """
    if not room_name or not bot_username:
        return

    if not room_manager.is_member(room_name, bot_username):
        room_manager.join_room(room_name, bot_username, None)

    for msg_text in messages_to_post:
        if not msg_text:
            continue
        message_store.add_message(
            room_name, bot_username, msg_text, "normal",
            "\033[94m", time.time() - len(messages_to_post) * 60,
            {"simulated": True}
        )
        room_manager.update_room_activity(room_name)
        time.sleep(0.001)  # tiny delay to get different timestamps


def populate_demo_data(admin_user):
    """Populate rooms with some demo messages so it feels like an active server"""
    demo_users_data = [
        ("alice", "Alice", "hey everyone!"),
        ("bob", "Bob", "what's up"),
        ("charlie", "Charlie", "just testing this chat app"),
        ("diana", "Diana", "looks pretty cool"),
        ("eve", "Eve", "agreed, nice interface"),
    ]

    demo_messages_general = [
        ("alice", "hey everyone, welcome to the server!"),
        ("bob", "hey alice! glad to be here"),
        ("charlie", "this is pretty cool, how did you set it up?"),
        ("alice", "just wrote it in Python, nothing special"),
        ("diana", "nice work, very clean"),
        ("bob", "yeah the colors look great"),
        ("eve", "any plans to add more features?"),
        ("alice", "maybe file sharing eventually, for now it's just chat"),
        ("charlie", "that would be awesome"),
        ("diana", "i'll definitely use this for our dev team"),
    ]

    demo_messages_random = [
        ("bob", "anyone watching the game tonight?"),
        ("charlie", "what game?"),
        ("bob", "football, big match"),
        ("eve", "I might catch the second half"),
        ("alice", "not into sports but hope your team wins!"),
        ("diana", "lol same"),
    ]

    # Register demo users
    for uname, display, _ in demo_users_data:
        if user_manager.get_user(uname) is None:
            user_manager.register_user(uname, "", display, "", "", uname[0].upper(), None)

    # Post messages to general
    base_time = time.time() - 3600
    for i, (uname, msg) in enumerate(demo_messages_general):
        if not room_manager.is_member("general", uname):
            room_manager.join_room("general", uname, None)
        message_store.add_message(
            "general", uname, msg, "normal",
            user_manager.get_user_color(uname),
            base_time + (i * 120), {}
        )
        room_manager.update_room_activity("general")

    # Post messages to random
    for i, (uname, msg) in enumerate(demo_messages_random):
        if not room_manager.is_member("random", uname):
            room_manager.join_room("random", uname, None)
        message_store.add_message(
            "random", uname, msg, "normal",
            user_manager.get_user_color(uname),
            base_time + (i * 180), {}
        )
        room_manager.update_room_activity("random")
