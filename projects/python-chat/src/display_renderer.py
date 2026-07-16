import time
import sys
from datetime import datetime

# ANSI color codes - just constants, fine
RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
ITALIC = "\033[3m"
UNDERLINE = "\033[4m"

# Foreground colors
BLACK = "\033[30m"
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
BLUE = "\033[34m"
MAGENTA = "\033[35m"
CYAN = "\033[36m"
WHITE = "\033[37m"
BRIGHT_BLACK = "\033[90m"
BRIGHT_RED = "\033[91m"
BRIGHT_GREEN = "\033[92m"
BRIGHT_YELLOW = "\033[93m"
BRIGHT_BLUE = "\033[94m"
BRIGHT_MAGENTA = "\033[95m"
BRIGHT_CYAN = "\033[96m"
BRIGHT_WHITE = "\033[97m"

# Background colors
BG_BLACK = "\033[40m"
BG_RED = "\033[41m"
BG_GREEN = "\033[42m"
BG_YELLOW = "\033[43m"
BG_BLUE = "\033[44m"
BG_MAGENTA = "\033[45m"
BG_CYAN = "\033[46m"
BG_WHITE = "\033[47m"

# Username color pool
USER_COLORS = [BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW, BRIGHT_BLUE,
               BRIGHT_MAGENTA, BRIGHT_CYAN, GREEN, YELLOW, BLUE, MAGENTA, CYAN]

_color_assignments = {}
_color_index = 0
_terminal_width = 80
_compact_mode = False
_show_timestamps = True
_current_theme = "default"


def get_terminal_width():
    try:
        import shutil
        return shutil.get_terminal_size().columns
    except Exception:
        return 80


def assign_user_color(username):
    global _color_index
    if username not in _color_assignments:
        _color_assignments[username] = USER_COLORS[_color_index % len(USER_COLORS)]
        _color_index += 1
    return _color_assignments[username]


def clear_screen():
    sys.stdout.write("\033[2J\033[H")
    sys.stdout.flush()


def move_cursor(row, col):
    sys.stdout.write(f"\033[{row};{col}H")
    sys.stdout.flush()


def render_header(current_room, username, online_count, total_rooms, unread_pm_count, server_name):
    width = get_terminal_width()
    sys.stdout.write("\033[H")  # Move to top

    # Top border
    border = "=" * width
    sys.stdout.write(f"{BOLD}{BRIGHT_CYAN}{border}{RESET}\n")

    # Server name and room info
    if current_room:
        room_part = f"#{current_room}"
    else:
        room_part = "no room"

    if unread_pm_count and unread_pm_count > 0:
        pm_indicator = f" {BRIGHT_RED}[{unread_pm_count} PM]{RESET}"
    else:
        pm_indicator = ""

    title_line = f"{BOLD}{server_name}{RESET} | {BRIGHT_GREEN}{username}{RESET} | Room: {BRIGHT_YELLOW}{room_part}{RESET}{pm_indicator}"
    title_line += f" | {BRIGHT_CYAN}{online_count} online{RESET} | {DIM}{total_rooms} rooms{RESET}"

    sys.stdout.write(f" {title_line}\n")
    sys.stdout.write(f"{BOLD}{BRIGHT_CYAN}{border}{RESET}\n")
    sys.stdout.flush()


def render_message(username, content, timestamp, msg_type, is_self, show_timestamp, compact, extra_info):
    color = assign_user_color(username)
    width = get_terminal_width()
    output = ""

    if msg_type == "system":
        output = f"{DIM}{BRIGHT_BLACK}  * {content}{RESET}\n"
    elif msg_type == "join":
        output = f"{GREEN}  --> {username} joined the room{RESET}\n"
    elif msg_type == "leave":
        output = f"{YELLOW}  <-- {username} left the room{RESET}\n"
    elif msg_type == "private":
        if is_self:
            output = f"{MAGENTA}  [PM to {extra_info}] {content}{RESET}\n"
        else:
            output = f"{BRIGHT_MAGENTA}  [PM from {username}] {content}{RESET}\n"
    elif msg_type == "action":
        output = f"{ITALIC}{BRIGHT_YELLOW}  * {username} {content}{RESET}\n"
    elif msg_type == "announcement":
        output = f"{BOLD}{BRIGHT_RED}  [ANNOUNCEMENT] {content}{RESET}\n"
    else:
        # Normal message
        if show_timestamp and timestamp:
            dt = datetime.fromtimestamp(timestamp)
            if compact:
                time_str = dt.strftime("%H:%M")
            else:
                time_str = dt.strftime("%H:%M:%S")
            ts_part = f"{DIM}[{time_str}]{RESET} "
        else:
            ts_part = ""

        if is_self:
            name_part = f"{BOLD}{BRIGHT_WHITE}{username}{RESET}"
        else:
            name_part = f"{BOLD}{color}{username}{RESET}"

        output = f"  {ts_part}{name_part}: {content}\n"

    sys.stdout.write(output)
    sys.stdout.flush()


def render_room_list(rooms_data, current_room, username):
    width = get_terminal_width()
    sys.stdout.write(f"\n{BOLD}{BRIGHT_CYAN}Available Rooms:{RESET}\n")
    sys.stdout.write(f"{DIM}{'─' * min(50, width)}{RESET}\n")

    if not rooms_data:
        sys.stdout.write(f"{DIM}  No rooms available{RESET}\n")
    else:
        for room_info in rooms_data:
            name = room_info.get("name", "unknown")
            member_count = room_info.get("member_count", 0)
            description = room_info.get("description", "")
            is_private = room_info.get("is_private", False)
            topic = room_info.get("topic", "")

            if name == current_room:
                marker = f"{BRIGHT_GREEN}>{RESET}"
            else:
                marker = " "

            if is_private:
                lock_icon = f"{YELLOW}[private]{RESET}"
            else:
                lock_icon = f"{DIM}[public]{RESET}"

            desc_str = ""
            if topic:
                desc_str = f" - {DIM}{topic[:40]}{RESET}"
            elif description:
                desc_str = f" - {DIM}{description[:40]}{RESET}"

            sys.stdout.write(
                f"  {marker} {BOLD}{BRIGHT_YELLOW}#{name}{RESET} "
                f"{BRIGHT_BLACK}({member_count} members){RESET} "
                f"{lock_icon}{desc_str}\n"
            )

    sys.stdout.write(f"{DIM}{'─' * min(50, width)}{RESET}\n")
    sys.stdout.flush()


def render_user_list(users_data, current_room):
    width = get_terminal_width()
    sys.stdout.write(f"\n{BOLD}{BRIGHT_CYAN}Users in #{current_room}:{RESET}\n")
    sys.stdout.write(f"{DIM}{'─' * min(40, width)}{RESET}\n")

    if not users_data:
        sys.stdout.write(f"{DIM}  No users in this room{RESET}\n")
    else:
        for user_info in users_data:
            uname = user_info.get("username", "unknown")
            display = user_info.get("display_name", uname)
            online = user_info.get("online", False)
            is_mod = user_info.get("is_moderator", False)
            status_msg = user_info.get("status_message", "")

            color = assign_user_color(uname)

            if online:
                status_dot = f"{GREEN}●{RESET}"
            else:
                status_dot = f"{BRIGHT_BLACK}○{RESET}"

            if is_mod:
                mod_badge = f" {YELLOW}[mod]{RESET}"
            else:
                mod_badge = ""

            status_str = ""
            if status_msg:
                status_str = f" {DIM}- {status_msg[:30]}{RESET}"

            sys.stdout.write(
                f"  {status_dot} {BOLD}{color}{display}{RESET}{mod_badge}{status_str}\n"
            )

    sys.stdout.write(f"{DIM}{'─' * min(40, width)}{RESET}\n")
    sys.stdout.flush()


def render_help(commands_list):
    width = get_terminal_width()
    sys.stdout.write(f"\n{BOLD}{BRIGHT_CYAN}Available Commands:{RESET}\n")
    sys.stdout.write(f"{DIM}{'─' * min(60, width)}{RESET}\n")

    for cmd_info in commands_list:
        cmd = cmd_info.get("command", "")
        usage = cmd_info.get("usage", "")
        description = cmd_info.get("description", "")
        example = cmd_info.get("example", "")

        sys.stdout.write(f"  {BOLD}{BRIGHT_YELLOW}{cmd}{RESET}")
        if usage:
            sys.stdout.write(f" {DIM}{usage}{RESET}")
        sys.stdout.write(f"\n      {description}\n")
        if example:
            sys.stdout.write(f"      {DIM}Example: {example}{RESET}\n")
        sys.stdout.write("\n")

    sys.stdout.write(f"{DIM}{'─' * min(60, width)}{RESET}\n")
    sys.stdout.flush()


def render_history(messages, room_name, count, show_timestamps):
    width = get_terminal_width()
    sys.stdout.write(f"\n{BOLD}{BRIGHT_CYAN}Message History - #{room_name} (last {count}):{RESET}\n")
    sys.stdout.write(f"{DIM}{'─' * min(70, width)}{RESET}\n")

    if not messages:
        sys.stdout.write(f"{DIM}  No messages in history{RESET}\n")
    else:
        for msg in messages:
            username = msg.get("username", "unknown")
            content = msg.get("content", "")
            timestamp = msg.get("timestamp")
            msg_type = msg.get("type", "normal")

            if msg_type == "system":
                sys.stdout.write(f"  {DIM}* {content}{RESET}\n")
                continue
            elif msg_type == "join":
                sys.stdout.write(f"  {GREEN}--> {username} joined{RESET}\n")
                continue
            elif msg_type == "leave":
                sys.stdout.write(f"  {YELLOW}<-- {username} left{RESET}\n")
                continue

            color = assign_user_color(username)

            if show_timestamps and timestamp:
                dt = datetime.fromtimestamp(timestamp)
                ts_str = f"{DIM}[{dt.strftime('%Y-%m-%d %H:%M:%S')}]{RESET} "
            else:
                ts_str = ""

            edited_marker = ""
            if msg.get("edited"):
                edited_marker = f" {DIM}(edited){RESET}"

            sys.stdout.write(f"  {ts_str}{BOLD}{color}{username}{RESET}: {content}{edited_marker}\n")

    sys.stdout.write(f"{DIM}{'─' * min(70, width)}{RESET}\n")
    sys.stdout.flush()


def render_search_results(results, query, room_name):
    width = get_terminal_width()
    sys.stdout.write(f"\n{BOLD}{BRIGHT_CYAN}Search results for '{query}'")
    if room_name:
        sys.stdout.write(f" in #{room_name}")
    sys.stdout.write(f":{RESET}\n")
    sys.stdout.write(f"{DIM}{'─' * min(70, width)}{RESET}\n")

    if not results:
        sys.stdout.write(f"{DIM}  No results found{RESET}\n")
    elif isinstance(results, dict):
        # Multi-room results
        total = sum(len(v) for v in results.values())
        sys.stdout.write(f"{DIM}  Found {total} results across {len(results)} rooms{RESET}\n\n")
        for rname, msgs in results.items():
            sys.stdout.write(f"  {BOLD}#{rname}{RESET}:\n")
            for msg in msgs:
                username = msg.get("username", "unknown")
                content = msg.get("content", "")
                timestamp = msg.get("timestamp")
                color = assign_user_color(username)
                dt_str = ""
                if timestamp:
                    dt = datetime.fromtimestamp(timestamp)
                    dt_str = f"{DIM}[{dt.strftime('%m/%d %H:%M')}]{RESET} "
                # Highlight query in content
                highlighted = _highlight_text(content, query)
                sys.stdout.write(f"    {dt_str}{BOLD}{color}{username}{RESET}: {highlighted}\n")
            sys.stdout.write("\n")
    else:
        # Single room results
        sys.stdout.write(f"{DIM}  Found {len(results)} results{RESET}\n\n")
        for msg in results:
            username = msg.get("username", "unknown")
            content = msg.get("content", "")
            timestamp = msg.get("timestamp")
            color = assign_user_color(username)
            dt_str = ""
            if timestamp:
                dt = datetime.fromtimestamp(timestamp)
                dt_str = f"{DIM}[{dt.strftime('%m/%d %H:%M')}]{RESET} "
            highlighted = _highlight_text(content, query)
            sys.stdout.write(f"  {dt_str}{BOLD}{color}{username}{RESET}: {highlighted}\n")

    sys.stdout.write(f"{DIM}{'─' * min(70, width)}{RESET}\n")
    sys.stdout.flush()


def render_private_messages(messages, other_user, current_user):
    width = get_terminal_width()
    sys.stdout.write(f"\n{BOLD}{BRIGHT_MAGENTA}Private Messages with {other_user}:{RESET}\n")
    sys.stdout.write(f"{DIM}{'─' * min(70, width)}{RESET}\n")

    if not messages:
        sys.stdout.write(f"{DIM}  No messages yet{RESET}\n")
    else:
        for msg in messages:
            from_user = msg.get("from", "unknown")
            content = msg.get("content", "")
            timestamp = msg.get("timestamp")
            read = msg.get("read", False)

            if timestamp:
                dt = datetime.fromtimestamp(timestamp)
                ts_str = f"{DIM}[{dt.strftime('%H:%M:%S')}]{RESET} "
            else:
                ts_str = ""

            if from_user == current_user:
                name_str = f"{BOLD}{BRIGHT_WHITE}You{RESET}"
                arrow = f"{DIM}->{RESET}"
            else:
                color = assign_user_color(from_user)
                name_str = f"{BOLD}{color}{from_user}{RESET}"
                arrow = ""

            read_str = ""
            if from_user == current_user and not read:
                read_str = f" {DIM}[unread]{RESET}"

            sys.stdout.write(f"  {ts_str}{name_str} {arrow}: {content}{read_str}\n")

    sys.stdout.write(f"{DIM}{'─' * min(70, width)}{RESET}\n")
    sys.stdout.flush()


def render_system_message(message, msg_level):
    if msg_level == "error":
        prefix = f"{BRIGHT_RED}[ERROR]{RESET}"
    elif msg_level == "warning":
        prefix = f"{BRIGHT_YELLOW}[WARN]{RESET}"
    elif msg_level == "success":
        prefix = f"{BRIGHT_GREEN}[OK]{RESET}"
    elif msg_level == "info":
        prefix = f"{BRIGHT_CYAN}[INFO]{RESET}"
    else:
        prefix = f"{DIM}[*]{RESET}"

    sys.stdout.write(f"  {prefix} {message}\n")
    sys.stdout.flush()


def render_prompt(username, current_room):
    color = assign_user_color(username)
    if current_room:
        prompt = f"{BOLD}{color}{username}{RESET}{DIM}@{RESET}{BRIGHT_YELLOW}#{current_room}{RESET}{BOLD}>{RESET} "
    else:
        prompt = f"{BOLD}{color}{username}{RESET}{BOLD}>{RESET} "
    sys.stdout.write(prompt)
    sys.stdout.flush()


def render_separator(width, char, color):
    if width is None:
        width = get_terminal_width()
    if char is None:
        char = "─"
    if color is None:
        color = DIM
    line = char * min(width, get_terminal_width())
    sys.stdout.write(f"{color}{line}{RESET}\n")
    sys.stdout.flush()


def render_welcome_banner(server_name, version, motd):
    width = get_terminal_width()
    banner_width = min(60, width)
    border = "═" * banner_width

    sys.stdout.write(f"\n{BOLD}{BRIGHT_CYAN}{border}{RESET}\n")
    # Center the server name
    padding = (banner_width - len(server_name) - 2) // 2
    sys.stdout.write(f"{BOLD}{BRIGHT_CYAN}{'═' * padding} {BRIGHT_WHITE}{server_name}{BRIGHT_CYAN} {'═' * padding}{RESET}\n")
    sys.stdout.write(f"{BOLD}{BRIGHT_CYAN}{border}{RESET}\n")

    if version:
        sys.stdout.write(f"  {DIM}Version: {version}{RESET}\n")

    if motd:
        sys.stdout.write(f"\n{BRIGHT_YELLOW}Message of the Day:{RESET}\n")
        for line in motd.split("\n"):
            sys.stdout.write(f"  {line}\n")

    sys.stdout.write(f"\n{DIM}Type {RESET}{BOLD}/help{RESET}{DIM} to see available commands{RESET}\n\n")
    sys.stdout.flush()


def render_notification(message, notification_type, sender, room):
    if notification_type == "pm":
        sys.stdout.write(f"\r{BOLD}{BRIGHT_MAGENTA}[PM from {sender}]{RESET} {message}\n")
    elif notification_type == "mention":
        sys.stdout.write(f"\r{BOLD}{BRIGHT_YELLOW}[Mention in #{room}]{RESET} {message}\n")
    elif notification_type == "join":
        sys.stdout.write(f"\r{DIM}[{sender} joined #{room}]{RESET}\n")
    elif notification_type == "leave":
        sys.stdout.write(f"\r{DIM}[{sender} left #{room}]{RESET}\n")
    else:
        sys.stdout.write(f"\r{DIM}[{message}]{RESET}\n")
    sys.stdout.flush()


def render_room_info(room_data):
    if not room_data:
        render_system_message("Room not found", "error")
        return

    width = get_terminal_width()
    name = room_data.get("name", "unknown")
    description = room_data.get("description", "No description")
    topic = room_data.get("topic", "")
    member_count = room_data.get("member_count", 0)
    created_at = room_data.get("created_at")
    creator = room_data.get("creator", "unknown")
    is_private = room_data.get("is_private", False)
    moderators = room_data.get("moderators", [])
    rules = room_data.get("rules", "")
    welcome_message = room_data.get("welcome_message", "")

    sys.stdout.write(f"\n{BOLD}{BRIGHT_CYAN}Room Info: #{name}{RESET}\n")
    sys.stdout.write(f"{DIM}{'─' * min(60, width)}{RESET}\n")
    sys.stdout.write(f"  {BOLD}Description:{RESET} {description}\n")

    if topic:
        sys.stdout.write(f"  {BOLD}Topic:{RESET} {topic}\n")

    sys.stdout.write(f"  {BOLD}Members:{RESET} {member_count}\n")
    sys.stdout.write(f"  {BOLD}Creator:{RESET} {creator}\n")
    sys.stdout.write(f"  {BOLD}Private:{RESET} {'Yes' if is_private else 'No'}\n")

    if created_at:
        dt = datetime.fromtimestamp(created_at)
        sys.stdout.write(f"  {BOLD}Created:{RESET} {dt.strftime('%Y-%m-%d %H:%M')}\n")

    if moderators:
        sys.stdout.write(f"  {BOLD}Moderators:{RESET} {', '.join(moderators)}\n")

    if welcome_message:
        sys.stdout.write(f"  {BOLD}Welcome:{RESET} {welcome_message}\n")

    if rules:
        sys.stdout.write(f"\n  {BOLD}Rules:{RESET}\n")
        for line in rules.split("\n"):
            sys.stdout.write(f"    {line}\n")

    sys.stdout.write(f"{DIM}{'─' * min(60, width)}{RESET}\n")
    sys.stdout.flush()


def render_user_profile(user_data):
    if not user_data:
        render_system_message("User not found", "error")
        return

    width = get_terminal_width()
    username = user_data.get("username", "unknown")
    display_name = user_data.get("display_name", username)
    bio = user_data.get("bio", "")
    online = user_data.get("online", False)
    status_message = user_data.get("status_message", "")
    rooms_joined = user_data.get("rooms_joined", 0)
    message_count = user_data.get("message_count", 0)
    is_admin = user_data.get("is_admin", False)
    created_at = user_data.get("created_at")
    last_seen = user_data.get("last_seen")

    color = assign_user_color(username)

    sys.stdout.write(f"\n{BOLD}{color}{display_name}{RESET}")
    if display_name != username:
        sys.stdout.write(f" {DIM}(@{username}){RESET}")
    sys.stdout.write("\n")
    sys.stdout.write(f"{DIM}{'─' * min(50, width)}{RESET}\n")

    if online:
        status_str = f"{GREEN}● Online{RESET}"
    else:
        status_str = f"{BRIGHT_BLACK}○ Offline{RESET}"

    if status_message:
        status_str += f" - {DIM}{status_message}{RESET}"

    sys.stdout.write(f"  Status: {status_str}\n")

    if bio:
        sys.stdout.write(f"  Bio: {bio}\n")

    if is_admin:
        sys.stdout.write(f"  {BOLD}{BRIGHT_RED}[Administrator]{RESET}\n")

    sys.stdout.write(f"  Rooms joined: {rooms_joined}\n")
    sys.stdout.write(f"  Messages sent: {message_count}\n")

    if created_at:
        dt = datetime.fromtimestamp(created_at)
        sys.stdout.write(f"  Joined: {dt.strftime('%Y-%m-%d')}\n")

    if last_seen and not online:
        dt = datetime.fromtimestamp(last_seen)
        sys.stdout.write(f"  Last seen: {dt.strftime('%Y-%m-%d %H:%M')}\n")

    sys.stdout.write(f"{DIM}{'─' * min(50, width)}{RESET}\n")
    sys.stdout.flush()


def _highlight_text(text, query):
    if not query or not text:
        return text
    lower_text = text.lower()
    lower_query = query.lower()
    result = ""
    i = 0
    while i < len(text):
        if lower_text[i:i+len(query)] == lower_query:
            result += f"{BOLD}{BRIGHT_YELLOW}{text[i:i+len(query)]}{RESET}"
            i += len(query)
        else:
            result += text[i]
            i += 1
    return result


def render_error(message):
    sys.stdout.write(f"  {BRIGHT_RED}Error: {message}{RESET}\n")
    sys.stdout.flush()


def render_success(message):
    sys.stdout.write(f"  {BRIGHT_GREEN}{message}{RESET}\n")
    sys.stdout.flush()


def render_info(message):
    sys.stdout.write(f"  {BRIGHT_CYAN}{message}{RESET}\n")
    sys.stdout.flush()
