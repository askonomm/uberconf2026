import time
import sys
import re
import message_store
import user_manager
import room_manager
import display_renderer


# Command definitions
COMMANDS = {
    "/help": {"usage": "[command]", "description": "Show available commands or help for a specific command", "example": "/help join"},
    "/join": {"usage": "<room> [password]", "description": "Join a chat room", "example": "/join general"},
    "/leave": {"usage": "[room]", "description": "Leave the current or specified room", "example": "/leave general"},
    "/msg": {"usage": "<user> <message>", "description": "Send a private message to a user", "example": "/msg alice hello there"},
    "/list": {"usage": "[rooms|users]", "description": "List rooms or users", "example": "/list rooms"},
    "/history": {"usage": "[count]", "description": "Show message history", "example": "/history 50"},
    "/search": {"usage": "<query> [room]", "description": "Search messages", "example": "/search hello"},
    "/quit": {"usage": "", "description": "Quit the chat application", "example": "/quit"},
    "/create": {"usage": "<room> [description]", "description": "Create a new room", "example": "/create myroom My cool room"},
    "/nick": {"usage": "<newname>", "description": "Change your display name", "example": "/nick CoolDude"},
    "/me": {"usage": "<action>", "description": "Perform an action", "example": "/me waves hello"},
    "/who": {"usage": "[room]", "description": "See who is in a room", "example": "/who general"},
    "/pm": {"usage": "<user> [count]", "description": "View private messages with a user", "example": "/pm alice"},
    "/rooms": {"usage": "", "description": "List all available rooms", "example": "/rooms"},
    "/topic": {"usage": "[room] [text]", "description": "View or set room topic", "example": "/topic general Chat here!"},
    "/info": {"usage": "[room]", "description": "Get detailed room info", "example": "/info general"},
    "/profile": {"usage": "[user]", "description": "View user profile", "example": "/profile alice"},
    "/clear": {"usage": "", "description": "Clear the screen", "example": "/clear"},
    "/status": {"usage": "<message>", "description": "Set your status message", "example": "/status coding away"},
    "/color": {"usage": "<color>", "description": "Set your name color (red/green/blue/yellow/magenta/cyan/white)", "example": "/color blue"},
}


def process_command(raw_input, current_username, current_room, chat_state):
    """
    Main command dispatcher. Takes raw input string and returns (action, data) tuple.
    chat_state is a dict with 'running', 'refresh_needed', etc.
    This function handles all the /commands and returns what happened.
    """
    if raw_input is None or raw_input.strip() == "":
        return "empty", None

    stripped = raw_input.strip()

    if not stripped.startswith("/"):
        # Regular message - send to current room
        if current_room is None or current_room == "":
            display_renderer.render_system_message("You're not in any room. Use /join <room> to join one.", "warning")
            return "no_room", None

        # Check if user is muted
        if user_manager.is_muted(current_room, current_username):
            display_renderer.render_system_message("You are muted in this room.", "error")
            return "muted", None

        # Check if user is banned from room
        room_data = room_manager.get_room(current_room)
        if room_data and current_username in room_data.get("banned_users", []):
            display_renderer.render_system_message("You are banned from this room.", "error")
            return "banned", None

        # Send message
        content = stripped
        if len(content) > 2000:
            display_renderer.render_system_message("Message too long (max 2000 chars)", "warning")
            return "too_long", None

        user_color = user_manager.get_user_color(current_username)
        msg_id = message_store.add_message(
            current_room, current_username, content, "normal",
            user_color, time.time(), {}
        )
        if msg_id:
            room_manager.update_room_activity(current_room)
            user_manager.increment_message_count(current_username)
            display_renderer.render_message(
                current_username, content, time.time(), "normal",
                True, True, False, None
            )
            return "message_sent", msg_id
        else:
            display_renderer.render_system_message("Failed to send message", "error")
            return "error", None

    # Parse command
    parts = stripped.split()
    cmd = parts[0].lower()
    args = parts[1:] if len(parts) > 1 else []

    # /quit
    if cmd == "/quit" or cmd == "/exit" or cmd == "/q":
        if current_room:
            message_store.add_message(
                current_room, current_username, f"{current_username} has quit",
                "leave", "\033[33m", time.time(), {}
            )
            room_manager.leave_room(current_room, current_username)
        user_manager.logout_user(current_username)
        display_renderer.render_system_message("Goodbye!", "info")
        chat_state["running"] = False
        return "quit", None

    # /help
    elif cmd == "/help":
        if args:
            # Help for specific command
            specific_cmd = args[0] if args[0].startswith("/") else "/" + args[0]
            if specific_cmd in COMMANDS:
                cmd_data = COMMANDS[specific_cmd]
                display_renderer.render_system_message(f"Help for {specific_cmd}:", "info")
                sys.stdout.write(f"  Usage: {specific_cmd} {cmd_data['usage']}\n")
                sys.stdout.write(f"  {cmd_data['description']}\n")
                if cmd_data.get('example'):
                    sys.stdout.write(f"  Example: {cmd_data['example']}\n")
            else:
                display_renderer.render_system_message(f"Unknown command: {specific_cmd}", "error")
        else:
            commands_list = []
            for c, info in COMMANDS.items():
                commands_list.append({
                    "command": c,
                    "usage": info["usage"],
                    "description": info["description"],
                    "example": info.get("example", "")
                })
            display_renderer.render_help(commands_list)
        return "help", None

    # /join
    elif cmd == "/join":
        if not args:
            display_renderer.render_system_message("Usage: /join <room> [password]", "error")
            return "usage_error", None

        target_room = args[0].lstrip("#")
        password = args[1] if len(args) > 1 else None

        # Check if room exists
        if target_room not in room_manager.get_all_rooms():
            display_renderer.render_system_message(f"Room #{target_room} does not exist. Use /create to create it.", "warning")
            return "room_not_found", None

        # Check if already in room
        if room_manager.is_member(target_room, current_username):
            # Just switch to that room
            user_manager.update_user_status(current_username, "current_room", target_room)
            chat_state["current_room"] = target_room
            display_renderer.render_system_message(f"Switched to #{target_room}", "info")
            recent = message_store.get_recent_messages(target_room, 20)
            if recent:
                display_renderer.render_history(recent, target_room, len(recent), True)
            return "switch_room", target_room

        success, msg = room_manager.join_room(target_room, current_username, password)
        if not success:
            display_renderer.render_system_message(msg, "error")
            return "join_failed", None

        user_manager.join_room_for_user(current_username, target_room)
        chat_state["current_room"] = target_room

        # Post join message
        message_store.add_message(
            target_room, current_username, f"{current_username} joined the room",
            "join", "\033[32m", time.time(), {}
        )
        room_manager.update_room_activity(target_room)

        room_info = room_manager.get_room(target_room)
        welcome = room_info.get("welcome_message", f"Welcome to #{target_room}!") if room_info else f"Welcome to #{target_room}!"
        display_renderer.render_system_message(welcome, "success")

        recent = message_store.get_recent_messages(target_room, 15)
        if recent:
            display_renderer.render_history(recent, target_room, len(recent), True)

        return "joined", target_room

    # /leave
    elif cmd == "/leave":
        if args:
            target_room = args[0].lstrip("#")
        else:
            target_room = current_room

        if target_room is None:
            display_renderer.render_system_message("You're not in any room", "warning")
            return "no_room", None

        if not room_manager.is_member(target_room, current_username):
            display_renderer.render_system_message(f"You're not in #{target_room}", "warning")
            return "not_in_room", None

        message_store.add_message(
            target_room, current_username, f"{current_username} left the room",
            "leave", "\033[33m", time.time(), {}
        )
        room_manager.leave_room(target_room, current_username)
        user_manager.leave_room_for_user(current_username, target_room)

        if chat_state.get("current_room") == target_room:
            # Find another room to switch to
            user_rooms = user_manager.get_user_rooms(current_username)
            if user_rooms:
                new_room = user_rooms[-1]
                chat_state["current_room"] = new_room
                user_manager.update_user_status(current_username, "current_room", new_room)
                display_renderer.render_system_message(f"Left #{target_room}, now in #{new_room}", "info")
            else:
                chat_state["current_room"] = None
                user_manager.update_user_status(current_username, "current_room", None)
                display_renderer.render_system_message(f"Left #{target_room}. You're not in any room.", "info")

        return "left", target_room

    # /msg or /pm (private message send)
    elif cmd == "/msg" or cmd == "/m":
        if len(args) < 2:
            display_renderer.render_system_message("Usage: /msg <user> <message>", "error")
            return "usage_error", None

        target_user = args[0]
        message_content = " ".join(args[1:])

        if target_user == current_username:
            display_renderer.render_system_message("Cannot send PM to yourself", "warning")
            return "self_pm", None

        if user_manager.get_user(target_user) is None:
            display_renderer.render_system_message(f"User '{target_user}' not found", "error")
            return "user_not_found", None

        if user_manager.is_blocked(current_username, target_user):
            display_renderer.render_system_message(f"You are blocked by {target_user}", "error")
            return "blocked", None

        if len(message_content) > 2000:
            display_renderer.render_system_message("Message too long (max 2000 chars)", "warning")
            return "too_long", None

        msg_id = message_store.add_private_message(
            current_username, target_user, message_content,
            time.time(), {}
        )
        if msg_id:
            display_renderer.render_message(
                current_username, message_content, time.time(), "private",
                True, True, False, target_user
            )
            return "pm_sent", msg_id
        else:
            display_renderer.render_system_message("Failed to send PM", "error")
            return "error", None

    # /pm (view private messages)
    elif cmd == "/pm":
        if not args:
            display_renderer.render_system_message("Usage: /pm <user> [count]", "error")
            return "usage_error", None

        other_user = args[0]
        count = 20
        if len(args) > 1:
            try:
                count = int(args[1])
                if count < 1:
                    count = 20
                if count > 100:
                    count = 100
            except ValueError:
                pass

        if user_manager.get_user(other_user) is None:
            display_renderer.render_system_message(f"User '{other_user}' not found", "error")
            return "user_not_found", None

        msgs = message_store.get_private_messages(current_username, other_user, count, 0)
        message_store.mark_private_messages_read(other_user, current_username)
        display_renderer.render_private_messages(msgs, other_user, current_username)
        return "pm_view", other_user

    # /list
    elif cmd == "/list":
        what = args[0].lower() if args else "rooms"

        if what == "rooms" or what == "room":
            public_rooms = room_manager.get_public_rooms()
            rooms_data = []
            for rname in public_rooms:
                info = room_manager.get_room_info(rname)
                if info:
                    rooms_data.append(info)
            rooms_data.sort(key=lambda x: x.get("member_count", 0), reverse=True)
            display_renderer.render_room_list(rooms_data, current_room, current_username)
            return "list_rooms", None

        elif what == "users" or what == "user":
            if current_room is None:
                display_renderer.render_system_message("You're not in a room", "warning")
                return "no_room", None
            members = room_manager.get_room_members(current_room)
            users_data = []
            for uname in members:
                udata = user_manager.get_user_stats(uname)
                if udata:
                    udata["is_moderator"] = room_manager.is_moderator(current_room, uname)
                    users_data.append(udata)
            display_renderer.render_user_list(users_data, current_room)
            return "list_users", None

        elif what == "online":
            online_users = user_manager.get_online_users()
            display_renderer.render_system_message(f"Online users ({len(online_users)}): {', '.join(online_users)}", "info")
            return "list_online", None

        elif what == "all":
            all_rooms = room_manager.get_all_rooms()
            rooms_data = []
            for rname in all_rooms:
                info = room_manager.get_room_info(rname)
                if info:
                    rooms_data.append(info)
            rooms_data.sort(key=lambda x: x.get("member_count", 0), reverse=True)
            display_renderer.render_room_list(rooms_data, current_room, current_username)
            return "list_all_rooms", None

        else:
            display_renderer.render_system_message("Usage: /list [rooms|users|online|all]", "warning")
            return "usage_error", None

    # /rooms shortcut
    elif cmd == "/rooms":
        public_rooms = room_manager.get_public_rooms()
        rooms_data = []
        for rname in public_rooms:
            info = room_manager.get_room_info(rname)
            if info:
                rooms_data.append(info)
        rooms_data.sort(key=lambda x: x.get("member_count", 0), reverse=True)
        display_renderer.render_room_list(rooms_data, current_room, current_username)
        return "list_rooms", None

    # /history
    elif cmd == "/history" or cmd == "/hist":
        if current_room is None:
            display_renderer.render_system_message("You're not in any room", "warning")
            return "no_room", None

        count = 30
        room_target = current_room

        if args:
            # Could be count or room name
            try:
                count = int(args[0])
                if count < 1:
                    count = 10
                if count > 200:
                    count = 200
            except ValueError:
                # Maybe it's a room name
                potential_room = args[0].lstrip("#")
                if room_manager.get_room(potential_room):
                    room_target = potential_room
                    if len(args) > 1:
                        try:
                            count = int(args[1])
                            if count < 1:
                                count = 10
                            if count > 200:
                                count = 200
                        except ValueError:
                            pass
                else:
                    display_renderer.render_system_message(f"Invalid count or room: {args[0]}", "warning")

        msgs = message_store.get_recent_messages(room_target, count)
        display_renderer.render_history(msgs, room_target, len(msgs), True)
        return "history", None

    # /search
    elif cmd == "/search" or cmd == "/find" or cmd == "/grep":
        if not args:
            display_renderer.render_system_message("Usage: /search <query> [room]", "error")
            return "usage_error", None

        query = args[0]
        room_target = None

        if len(args) > 1:
            potential_room = args[1].lstrip("#")
            if room_manager.get_room(potential_room):
                room_target = potential_room
            else:
                # Maybe the rest is part of the query
                query = " ".join(args)

        if room_target:
            results = message_store.search_messages(room_target, query, 20, False, False)
            display_renderer.render_search_results(results, query, room_target)
        elif current_room:
            results = message_store.search_messages(current_room, query, 20, False, False)
            display_renderer.render_search_results(results, query, current_room)
        else:
            # Search all rooms user is in
            user_rooms = user_manager.get_user_rooms(current_username)
            if not user_rooms:
                display_renderer.render_system_message("You're not in any room", "warning")
                return "no_room", None
            results = message_store.search_all_rooms(query, user_rooms, 10, False)
            display_renderer.render_search_results(results, query, None)

        return "search", None

    # /create
    elif cmd == "/create":
        if not args:
            display_renderer.render_system_message("Usage: /create <room> [description]", "error")
            return "usage_error", None

        new_room = args[0].lstrip("#")
        description = " ".join(args[1:]) if len(args) > 1 else ""

        success, msg = room_manager.create_room(
            new_room, current_username, description, False, 500,
            "", "", "public"
        )
        if not success:
            display_renderer.render_system_message(msg, "error")
            return "create_failed", None

        # Auto-join the new room
        room_manager.join_room(new_room, current_username, None)
        user_manager.join_room_for_user(current_username, new_room)
        chat_state["current_room"] = new_room
        message_store.add_message(
            new_room, current_username, f"Room #{new_room} created by {current_username}",
            "system", "\033[90m", time.time(), {}
        )

        display_renderer.render_system_message(f"Room #{new_room} created and joined!", "success")
        return "created", new_room

    # /nick
    elif cmd == "/nick":
        if not args:
            display_renderer.render_system_message("Usage: /nick <newname>", "error")
            return "usage_error", None

        new_nick = args[0]
        if len(new_nick) < 1 or len(new_nick) > 50:
            display_renderer.render_system_message("Display name must be 1-50 characters", "error")
            return "invalid_nick", None

        user_manager.update_user_status(current_username, "display_name", new_nick)
        display_renderer.render_system_message(f"Display name changed to '{new_nick}'", "success")
        return "nick_changed", new_nick

    # /me (action)
    elif cmd == "/me":
        if not args:
            display_renderer.render_system_message("Usage: /me <action>", "error")
            return "usage_error", None

        if current_room is None:
            display_renderer.render_system_message("You're not in any room", "warning")
            return "no_room", None

        action_text = " ".join(args)
        msg_id = message_store.add_message(
            current_room, current_username, action_text, "action",
            "\033[93m", time.time(), {}
        )
        if msg_id:
            room_manager.update_room_activity(current_room)
            display_renderer.render_message(
                current_username, action_text, time.time(), "action",
                True, True, False, None
            )
        return "action", None

    # /who
    elif cmd == "/who":
        target_room = args[0].lstrip("#") if args else current_room
        if target_room is None:
            display_renderer.render_system_message("Specify a room or join one first", "warning")
            return "no_room", None

        if not room_manager.get_room(target_room):
            display_renderer.render_system_message(f"Room #{target_room} not found", "error")
            return "room_not_found", None

        members = room_manager.get_room_members(target_room)
        users_data = []
        for uname in members:
            udata = user_manager.get_user_stats(uname)
            if udata:
                udata["is_moderator"] = room_manager.is_moderator(target_room, uname)
                users_data.append(udata)
        display_renderer.render_user_list(users_data, target_room)
        return "who", None

    # /topic
    elif cmd == "/topic":
        if not args:
            # Show current topic
            if current_room is None:
                display_renderer.render_system_message("You're not in any room", "warning")
                return "no_room", None
            room_data = room_manager.get_room(current_room)
            if room_data:
                topic = room_data.get("topic", "")
                if topic:
                    display_renderer.render_system_message(f"Topic for #{current_room}: {topic}", "info")
                else:
                    display_renderer.render_system_message(f"No topic set for #{current_room}", "info")
            return "topic_view", None

        # Check if first arg is a room or part of topic
        potential_room = args[0].lstrip("#")
        if room_manager.get_room(potential_room) and len(args) > 1:
            target_room = potential_room
            new_topic = " ".join(args[1:])
        else:
            target_room = current_room
            new_topic = " ".join(args)

        if target_room is None:
            display_renderer.render_system_message("You're not in any room", "warning")
            return "no_room", None

        if not room_manager.is_moderator(target_room, current_username):
            u = user_manager.get_user(current_username)
            if u and not u.get("is_admin", False):
                display_renderer.render_system_message("Only moderators can set the topic", "error")
                return "permission_denied", None

        success, msg = room_manager.update_room_setting(
            target_room, "topic", new_topic, current_username, False
        )
        if success:
            message_store.add_message(
                target_room, current_username,
                f"{current_username} changed topic to: {new_topic}",
                "system", "\033[90m", time.time(), {}
            )
            display_renderer.render_system_message(f"Topic updated: {new_topic}", "success")
        else:
            display_renderer.render_system_message(msg, "error")
        return "topic_set", None

    # /info
    elif cmd == "/info":
        target_room = args[0].lstrip("#") if args else current_room
        if target_room is None:
            display_renderer.render_system_message("Specify a room or join one first", "warning")
            return "no_room", None

        room_data = room_manager.get_room_info(target_room)
        if room_data is None:
            display_renderer.render_system_message(f"Room #{target_room} not found", "error")
            return "room_not_found", None

        display_renderer.render_room_info(room_data)
        return "info", None

    # /profile
    elif cmd == "/profile" or cmd == "/whois":
        target_user = args[0] if args else current_username
        user_data = user_manager.get_user_stats(target_user)
        if user_data is None:
            display_renderer.render_system_message(f"User '{target_user}' not found", "error")
            return "user_not_found", None
        u = user_manager.get_user(target_user)
        if u:
            user_data["bio"] = u.get("bio", "")
        display_renderer.render_user_profile(user_data)
        return "profile", None

    # /clear
    elif cmd == "/clear":
        display_renderer.clear_screen()
        return "clear", None

    # /status
    elif cmd == "/status":
        if not args:
            # Show current status
            u = user_manager.get_user(current_username)
            if u:
                display_renderer.render_system_message(f"Your status: online", "info")
            return "status_view", None

        new_status = " ".join(args)
        if len(new_status) > 100:
            display_renderer.render_system_message("Status too long (max 100 chars)", "warning")
            return "too_long", None

        user_manager.update_user_status(current_username, "status_message", new_status)
        display_renderer.render_system_message(f"Status set to: {new_status}", "success")
        return "status_set", None

    # /color
    elif cmd == "/color":
        color_map = {
            "red": "\033[91m", "green": "\033[92m", "blue": "\033[94m",
            "yellow": "\033[93m", "magenta": "\033[95m", "cyan": "\033[96m",
            "white": "\033[97m", "orange": "\033[33m", "default": "\033[37m"
        }
        if not args:
            display_renderer.render_system_message(
                f"Available colors: {', '.join(color_map.keys())}", "info"
            )
            return "color_list", None

        color_name = args[0].lower()
        if color_name not in color_map:
            display_renderer.render_system_message(
                f"Unknown color '{color_name}'. Options: {', '.join(color_map.keys())}", "error"
            )
            return "invalid_color", None

        color_code = color_map[color_name]
        user_manager.update_user_status(current_username, "color", color_code)
        # Update color assignment cache
        display_renderer._color_assignments[current_username] = color_code
        display_renderer.render_system_message(f"Color set to {color_name}", "success")
        return "color_set", None

    else:
        display_renderer.render_system_message(
            f"Unknown command: {cmd}. Type /help for available commands.", "error"
        )
        return "unknown_command", cmd


def get_command_suggestions(partial_input):
    """Get autocomplete suggestions for partial command input"""
    if not partial_input or not partial_input.startswith("/"):
        return []
    suggestions = []
    for cmd in COMMANDS.keys():
        if cmd.startswith(partial_input.lower()):
            suggestions.append(cmd)
    return suggestions


def validate_command_args(cmd, args):
    """Validate that a command has the right number of arguments"""
    required_args = {
        "/join": 1, "/msg": 2, "/create": 1, "/nick": 1,
        "/me": 1, "/search": 1, "/ban": 1
    }
    if cmd not in required_args:
        return True, None
    min_args = required_args[cmd]
    if len(args) < min_args:
        return False, f"{cmd} requires at least {min_args} argument(s)"
    return True, None


def parse_command_string(raw):
    """Parse a raw command string into (command, args) - handles quoted strings"""
    if not raw or not raw.strip():
        return None, []
    raw = raw.strip()
    if not raw.startswith("/"):
        return None, [raw]
    parts = []
    current = ""
    in_quotes = False
    quote_char = None
    i = 0
    while i < len(raw):
        ch = raw[i]
        if ch in ('"', "'") and not in_quotes:
            in_quotes = True
            quote_char = ch
        elif ch == quote_char and in_quotes:
            in_quotes = False
            quote_char = None
        elif ch == " " and not in_quotes:
            if current:
                parts.append(current)
                current = ""
        else:
            current += ch
        i += 1
    if current:
        parts.append(current)
    if not parts:
        return None, []
    return parts[0].lower(), parts[1:]


def is_valid_room_name(name):
    if name is None or name == "":
        return False
    if len(name) < 2 or len(name) > 64:
        return False
    if not name.replace("-", "").replace("_", "").isalnum():
        return False
    return True


def is_valid_username(name):
    if name is None or name == "":
        return False
    if len(name) < 2 or len(name) > 32:
        return False
    if not name.replace("_", "").replace("-", "").isalnum():
        return False
    return True
