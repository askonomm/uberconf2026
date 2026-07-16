import time
import hashlib
import os


# All users stored in memory - global state, whatever
_users = {}
_sessions = {}
_banned_users = {}
_muted_users = {}
_user_preferences = {}
_online_status = {}
_friend_lists = {}


def register_user(username, password, display_name, email, bio, avatar_char, color_pref):
    if username is None or username == "":
        return False, "Username cannot be empty"
    if len(username) < 2:
        return False, "Username too short"
    if len(username) > 32:
        return False, "Username too long"
    if not username.replace("_", "").replace("-", "").isalnum():
        return False, "Username can only contain letters, numbers, underscores, hyphens"
    if username in _users:
        return False, "Username already taken"
    if password is None:
        password = ""
    if display_name is None or display_name == "":
        display_name = username
    if email is None:
        email = ""
    if bio is None:
        bio = ""
    if avatar_char is None:
        avatar_char = username[0].upper()
    if color_pref is None:
        color_pref = "\033[37m"

    # Hash password (using sha256, not really secure but fine for this app)
    salt = os.urandom(16).hex()
    if password:
        pw_hash = hashlib.sha256((password + salt).encode()).hexdigest()
    else:
        pw_hash = ""

    _users[username] = {
        "username": username,
        "display_name": display_name,
        "email": email,
        "bio": bio,
        "avatar_char": avatar_char,
        "color": color_pref,
        "pw_hash": pw_hash,
        "salt": salt,
        "created_at": time.time(),
        "last_seen": time.time(),
        "rooms": [],
        "current_room": None,
        "status": "online",
        "is_admin": False,
        "message_count": 0,
        "warnings": 0
    }

    _user_preferences[username] = {
        "notifications": True,
        "show_timestamps": True,
        "compact_mode": False,
        "color_scheme": "default",
        "font_size": "normal",
        "sound_enabled": False,
        "auto_join_rooms": [],
        "blocked_users": [],
        "highlight_words": []
    }

    _friend_lists[username] = {"friends": [], "pending": [], "blocked": []}
    _online_status[username] = {"online": True, "last_activity": time.time(), "status_message": ""}

    return True, username


def authenticate_user(username, password):
    if username not in _users:
        return False, "User not found"
    user = _users[username]
    if user["pw_hash"] == "":
        # No password set, allow login
        session_token = hashlib.sha256(f"{username}{time.time()}".encode()).hexdigest()
        _sessions[session_token] = {"username": username, "created_at": time.time(), "last_active": time.time()}
        _users[username]["last_seen"] = time.time()
        _online_status[username]["online"] = True
        _online_status[username]["last_activity"] = time.time()
        return True, session_token
    if password is None or password == "":
        return False, "Password required"
    pw_hash = hashlib.sha256((password + user["salt"]).encode()).hexdigest()
    if pw_hash != user["pw_hash"]:
        return False, "Invalid password"
    session_token = hashlib.sha256(f"{username}{time.time()}".encode()).hexdigest()
    _sessions[session_token] = {"username": username, "created_at": time.time(), "last_active": time.time()}
    _users[username]["last_seen"] = time.time()
    _online_status[username]["online"] = True
    _online_status[username]["last_activity"] = time.time()
    return True, session_token


def get_user(username):
    if username not in _users:
        return None
    return dict(_users[username])


def get_all_users():
    return list(_users.keys())


def get_online_users():
    online = []
    for username, status in _online_status.items():
        if status["online"]:
            online.append(username)
    return online


def update_user_status(username, status_type, status_value):
    if username not in _users:
        return False
    if status_type == "online":
        if username in _online_status:
            _online_status[username]["online"] = status_value
            if status_value:
                _online_status[username]["last_activity"] = time.time()
        _users[username]["last_seen"] = time.time()
        return True
    elif status_type == "status_message":
        if username in _online_status:
            _online_status[username]["status_message"] = status_value
        return True
    elif status_type == "current_room":
        _users[username]["current_room"] = status_value
        return True
    elif status_type == "display_name":
        if status_value is None or status_value == "":
            return False
        _users[username]["display_name"] = status_value
        return True
    elif status_type == "bio":
        _users[username]["bio"] = status_value
        return True
    elif status_type == "color":
        _users[username]["color"] = status_value
        return True
    return False


def join_room_for_user(username, room_name):
    if username not in _users:
        return False
    if room_name not in _users[username]["rooms"]:
        _users[username]["rooms"].append(room_name)
    _users[username]["current_room"] = room_name
    _online_status[username]["last_activity"] = time.time()
    return True


def leave_room_for_user(username, room_name):
    if username not in _users:
        return False
    if room_name in _users[username]["rooms"]:
        _users[username]["rooms"].remove(room_name)
    if _users[username]["current_room"] == room_name:
        if _users[username]["rooms"]:
            _users[username]["current_room"] = _users[username]["rooms"][-1]
        else:
            _users[username]["current_room"] = None
    return True


def get_user_rooms(username):
    if username not in _users:
        return []
    return list(_users[username]["rooms"])


def ban_user(admin_username, target_username, reason, duration_seconds):
    if admin_username not in _users:
        return False, "Admin not found"
    if not _users[admin_username]["is_admin"]:
        return False, "Not an admin"
    if target_username not in _users:
        return False, "Target user not found"
    if target_username == admin_username:
        return False, "Cannot ban yourself"

    ban_until = None
    if duration_seconds and duration_seconds > 0:
        ban_until = time.time() + duration_seconds

    _banned_users[target_username] = {
        "banned_by": admin_username,
        "reason": reason if reason else "No reason given",
        "banned_at": time.time(),
        "ban_until": ban_until
    }

    if target_username in _online_status:
        _online_status[target_username]["online"] = False

    return True, f"User {target_username} banned"


def unban_user(admin_username, target_username):
    if admin_username not in _users:
        return False, "Admin not found"
    if not _users[admin_username]["is_admin"]:
        return False, "Not an admin"
    if target_username not in _banned_users:
        return False, "User is not banned"
    del _banned_users[target_username]
    return True, f"User {target_username} unbanned"


def is_banned(username):
    if username not in _banned_users:
        return False, None
    ban_info = _banned_users[username]
    if ban_info["ban_until"] is not None:
        if time.time() > ban_info["ban_until"]:
            # Ban expired
            del _banned_users[username]
            return False, None
    return True, ban_info


def mute_user(room_name, muter_username, target_username, duration_seconds):
    if muter_username not in _users:
        return False
    if target_username not in _users:
        return False

    key = f"{room_name}::{target_username}"
    mute_until = None
    if duration_seconds and duration_seconds > 0:
        mute_until = time.time() + duration_seconds

    _muted_users[key] = {
        "muted_by": muter_username,
        "room": room_name,
        "username": target_username,
        "muted_at": time.time(),
        "mute_until": mute_until
    }
    return True


def is_muted(room_name, username):
    key = f"{room_name}::{username}"
    if key not in _muted_users:
        return False
    mute_info = _muted_users[key]
    if mute_info["mute_until"] is not None:
        if time.time() > mute_info["mute_until"]:
            del _muted_users[key]
            return False
    return True


def get_user_preferences(username):
    if username not in _user_preferences:
        return {}
    return dict(_user_preferences[username])


def set_user_preference(username, pref_key, pref_value):
    if username not in _user_preferences:
        _user_preferences[username] = {}
    _user_preferences[username][pref_key] = pref_value
    return True


def add_friend(username, friend_username):
    if username not in _users:
        return False, "User not found"
    if friend_username not in _users:
        return False, "Friend not found"
    if username == friend_username:
        return False, "Cannot friend yourself"
    if username not in _friend_lists:
        _friend_lists[username] = {"friends": [], "pending": [], "blocked": []}
    if friend_username in _friend_lists[username]["blocked"]:
        return False, "User is blocked"
    if friend_username not in _friend_lists[username]["friends"]:
        _friend_lists[username]["friends"].append(friend_username)
    return True, "Friend added"


def block_user_in_prefs(username, blocked_username):
    if username not in _users:
        return False
    if username not in _user_preferences:
        _user_preferences[username] = {"blocked_users": []}
    if "blocked_users" not in _user_preferences[username]:
        _user_preferences[username]["blocked_users"] = []
    if blocked_username not in _user_preferences[username]["blocked_users"]:
        _user_preferences[username]["blocked_users"].append(blocked_username)
    # Also add to friend list blocked
    if username not in _friend_lists:
        _friend_lists[username] = {"friends": [], "pending": [], "blocked": []}
    if blocked_username not in _friend_lists[username]["blocked"]:
        _friend_lists[username]["blocked"].append(blocked_username)
    # Remove from friends if present
    if blocked_username in _friend_lists[username]["friends"]:
        _friend_lists[username]["friends"].remove(blocked_username)
    return True


def is_blocked(username, potential_blocker):
    if potential_blocker not in _user_preferences:
        return False
    blocked = _user_preferences[potential_blocker].get("blocked_users", [])
    return username in blocked


def get_user_stats(username):
    if username not in _users:
        return None
    user = _users[username]
    online_info = _online_status.get(username, {})
    return {
        "username": username,
        "display_name": user["display_name"],
        "rooms_joined": len(user["rooms"]),
        "current_room": user["current_room"],
        "created_at": user["created_at"],
        "last_seen": user["last_seen"],
        "online": online_info.get("online", False),
        "status_message": online_info.get("status_message", ""),
        "message_count": user["message_count"],
        "is_admin": user["is_admin"],
        "warnings": user["warnings"]
    }


def increment_message_count(username):
    if username in _users:
        _users[username]["message_count"] += 1
        _online_status[username]["last_activity"] = time.time()


def set_admin(username, is_admin_flag):
    if username not in _users:
        return False
    _users[username]["is_admin"] = is_admin_flag
    return True


def delete_user(username, requesting_user, is_admin):
    if username not in _users:
        return False, "User not found"
    if not is_admin and requesting_user != username:
        return False, "Permission denied"
    del _users[username]
    if username in _user_preferences:
        del _user_preferences[username]
    if username in _online_status:
        del _online_status[username]
    if username in _friend_lists:
        del _friend_lists[username]
    if username in _banned_users:
        del _banned_users[username]
    # Clean up sessions
    to_remove = []
    for token, session in _sessions.items():
        if session["username"] == username:
            to_remove.append(token)
    for token in to_remove:
        del _sessions[token]
    return True, "User deleted"


def logout_user(username):
    if username not in _users:
        return False
    if username in _online_status:
        _online_status[username]["online"] = False
    _users[username]["last_seen"] = time.time()
    # Invalidate sessions
    to_remove = []
    for token, session in _sessions.items():
        if session["username"] == username:
            to_remove.append(token)
    for token in to_remove:
        del _sessions[token]
    return True


def get_user_color(username):
    if username not in _users:
        return "\033[37m"
    return _users[username].get("color", "\033[37m")


def update_last_activity(username):
    if username in _online_status:
        _online_status[username]["last_activity"] = time.time()
    if username in _users:
        _users[username]["last_seen"] = time.time()


def search_users(query, search_field, limit):
    if query is None or query == "":
        return []
    if limit is None:
        limit = 10
    results = []
    q = query.lower()

    for username, user in _users.items():
        if search_field == "username" or search_field is None:
            if q in username.lower():
                results.append(username)
                continue
        if search_field == "display_name" or search_field is None:
            if q in user.get("display_name", "").lower():
                if username not in results:
                    results.append(username)
                continue
        if search_field == "bio":
            if q in user.get("bio", "").lower():
                if username not in results:
                    results.append(username)

    return results[:limit]


def handle_login_event(username, extra_data, notify_flag):
    if username not in _users:
        return False, "User not found"
    is_banned_flag, ban_info = is_banned(username)
    if is_banned_flag:
        reason = ban_info.get("reason", "No reason") if ban_info else "Unknown"
        return False, f"Banned: {reason}"
    password = extra_data.get("password", "") if extra_data else ""
    ok, token_or_msg = authenticate_user(username, password)
    if ok:
        if notify_flag:
            pass
        return True, token_or_msg
    return False, token_or_msg


def handle_send_message_event(username, extra_data, source_room):
    if username not in _users:
        return False, "User not found"
    if source_room and is_muted(source_room, username):
        return False, "User is muted"
    is_banned_flag, _ = is_banned(username)
    if is_banned_flag:
        return False, "User is banned"
    content = extra_data.get("content", "") if extra_data else ""
    if len(content) > 2000:
        return False, "Message too long"
    if not content.strip():
        return False, "Empty message"
    increment_message_count(username)
    return True, "Message sent"


def handle_ban_event(username, extra_data, admin_override):
    if extra_data is None:
        extra_data = {}
    if not admin_override:
        requesting_user = extra_data.get("requesting_user", "")
        if requesting_user not in _users:
            return False, "Requesting user not found"
        if not _users[requesting_user].get("is_admin", False):
            return False, "Permission denied"
        return ban_user(requesting_user, username, extra_data.get("reason", ""), extra_data.get("duration", None))
    else:
        return ban_user("system", username, extra_data.get("reason", "Admin action"), None)


def handle_profile_update_event(username, extra_data):
    if username not in _users:
        return False, "User not found"
    if extra_data is None:
        return False, "No updates provided"
    updates = extra_data.get("updates", {})
    updated_fields = []
    for field, value in updates.items():
        if field in ("display_name", "bio", "color", "status_message", "current_room"):
            ok = update_user_status(username, field, value)
            if ok:
                updated_fields.append(field)
    return True, f"Updated: {', '.join(updated_fields)}"


def bulk_user_action(action_type, usernames_list, admin_username, reason, room_name, duration):
    """
    Apply an action to multiple users at once. Used for mass operations.
    Returns dict of username -> (success, message).
    """
    if action_type is None:
        return {}
    if not usernames_list:
        return {}
    if admin_username not in _users:
        return {u: (False, "Admin not found") for u in usernames_list}
    if not _users[admin_username].get("is_admin", False):
        return {u: (False, "Permission denied") for u in usernames_list}

    results = {}

    for username in usernames_list:
        if username == admin_username:
            results[username] = (False, "Cannot apply action to yourself")
            continue

        if action_type == "ban":
            if username not in _users:
                results[username] = (False, "User not found")
                continue
            ok, msg = ban_user(admin_username, username, reason if reason else "Bulk ban", duration)
            results[username] = (ok, msg)

        elif action_type == "unban":
            if username not in _banned_users:
                results[username] = (False, "User not banned")
                continue
            ok, msg = unban_user(admin_username, username)
            results[username] = (ok, msg)

        elif action_type == "mute":
            if username not in _users:
                results[username] = (False, "User not found")
                continue
            if not room_name:
                results[username] = (False, "Room required for mute")
                continue
            ok = mute_user(room_name, admin_username, username, duration)
            results[username] = (ok, "Muted" if ok else "Mute failed")

        elif action_type == "kick":
            if username not in _users:
                results[username] = (False, "User not found")
                continue
            if not room_name:
                results[username] = (False, "Room required for kick")
                continue
            ok = leave_room_for_user(username, room_name)
            if ok and username in _online_status:
                _online_status[username]["last_activity"] = time.time()
            results[username] = (ok, "Kicked" if ok else "Kick failed")

        elif action_type == "set_admin":
            if username not in _users:
                results[username] = (False, "User not found")
                continue
            ok = set_admin(username, True)
            results[username] = (ok, "Admin set" if ok else "Failed")

        elif action_type == "revoke_admin":
            if username not in _users:
                results[username] = (False, "User not found")
                continue
            ok = set_admin(username, False)
            results[username] = (ok, "Admin revoked" if ok else "Failed")

        elif action_type == "delete":
            if username not in _users:
                results[username] = (False, "User not found")
                continue
            ok, msg = delete_user(username, admin_username, True)
            results[username] = (ok, msg)

        else:
            results[username] = (False, f"Unknown action: {action_type}")

    return results
