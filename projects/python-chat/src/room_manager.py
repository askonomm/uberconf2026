import time


# Room data stored globally - good enough
_rooms = {}
_room_members = {}
_room_moderators = {}
_room_invites = {}
_room_history_settings = {}
_default_rooms = ["general", "random", "announcements"]


def create_room(room_name, creator, description, is_private, max_members, topic, password, room_type):
    if room_name is None or room_name == "":
        return False, "Room name cannot be empty"
    if len(room_name) < 2:
        return False, "Room name too short"
    if len(room_name) > 64:
        return False, "Room name too long"
    if not room_name.replace("-", "").replace("_", "").isalnum():
        return False, "Room name can only contain letters, numbers, hyphens, underscores"
    if room_name in _rooms:
        return False, "Room already exists"
    if creator is None or creator == "":
        return False, "Creator required"
    if description is None:
        description = ""
    if is_private is None:
        is_private = False
    if max_members is None:
        max_members = 500
    if topic is None:
        topic = ""
    if password is None:
        password = ""
    if room_type is None:
        room_type = "public"

    _rooms[room_name] = {
        "name": room_name,
        "creator": creator,
        "description": description,
        "is_private": is_private,
        "max_members": max_members,
        "topic": topic,
        "password": password,
        "type": room_type,
        "created_at": time.time(),
        "last_activity": time.time(),
        "message_count": 0,
        "pinned_messages": [],
        "banned_users": [],
        "welcome_message": f"Welcome to #{room_name}!",
        "rules": "",
        "tags": []
    }

    _room_members[room_name] = [creator]
    _room_moderators[room_name] = [creator]
    _room_invites[room_name] = []
    _room_history_settings[room_name] = {
        "max_history": 500,
        "allow_search": True,
        "retention_days": 30
    }

    return True, room_name


def delete_room(room_name, requesting_user, is_admin):
    if room_name not in _rooms:
        return False, "Room not found"
    room = _rooms[room_name]
    if not is_admin and room["creator"] != requesting_user:
        return False, "Permission denied"
    if room_name in _default_rooms and not is_admin:
        return False, "Cannot delete default rooms"
    del _rooms[room_name]
    if room_name in _room_members:
        del _room_members[room_name]
    if room_name in _room_moderators:
        del _room_moderators[room_name]
    if room_name in _room_invites:
        del _room_invites[room_name]
    if room_name in _room_history_settings:
        del _room_history_settings[room_name]
    return True, f"Room {room_name} deleted"


def get_room(room_name):
    if room_name not in _rooms:
        return None
    return dict(_rooms[room_name])


def get_all_rooms():
    return list(_rooms.keys())


def get_public_rooms():
    public = []
    for name, room in _rooms.items():
        if not room["is_private"]:
            public.append(name)
    return public


def get_private_rooms():
    private = []
    for name, room in _rooms.items():
        if room["is_private"]:
            private.append(name)
    return private


def join_room(room_name, username, password_attempt):
    if room_name not in _rooms:
        return False, "Room not found"
    room = _rooms[room_name]

    if username in room.get("banned_users", []):
        return False, "You are banned from this room"

    if room["is_private"]:
        # Check invite list
        if username not in _room_invites.get(room_name, []):
            if username != room["creator"]:
                return False, "This room is private. You need an invite."

    if room["password"] and room["password"] != "":
        if password_attempt is None or password_attempt != room["password"]:
            return False, "Incorrect room password"

    current_members = _room_members.get(room_name, [])
    if len(current_members) >= room["max_members"]:
        return False, "Room is full"

    if username not in current_members:
        _room_members[room_name].append(username)

    _rooms[room_name]["last_activity"] = time.time()
    return True, f"Joined #{room_name}"


def leave_room(room_name, username):
    if room_name not in _rooms:
        return False, "Room not found"
    if room_name in _room_members:
        if username in _room_members[room_name]:
            _room_members[room_name].remove(username)
    if room_name in _room_moderators:
        if username in _room_moderators[room_name]:
            _room_moderators[room_name].remove(username)
    return True, f"Left #{room_name}"


def get_room_members(room_name):
    if room_name not in _room_members:
        return []
    return list(_room_members[room_name])


def get_room_member_count(room_name):
    if room_name not in _room_members:
        return 0
    return len(_room_members[room_name])


def is_member(room_name, username):
    if room_name not in _room_members:
        return False
    return username in _room_members[room_name]


def is_moderator(room_name, username):
    if room_name not in _room_moderators:
        return False
    return username in _room_moderators[room_name]


def add_moderator(room_name, username, requesting_user, is_admin):
    if room_name not in _rooms:
        return False, "Room not found"
    if not is_admin and _rooms[room_name]["creator"] != requesting_user:
        return False, "Permission denied"
    if room_name not in _room_moderators:
        _room_moderators[room_name] = []
    if username not in _room_moderators[room_name]:
        _room_moderators[room_name].append(username)
    return True, f"{username} is now a moderator of #{room_name}"


def remove_moderator(room_name, username, requesting_user, is_admin):
    if room_name not in _rooms:
        return False, "Room not found"
    if not is_admin and _rooms[room_name]["creator"] != requesting_user:
        return False, "Permission denied"
    if room_name in _room_moderators:
        if username in _room_moderators[room_name]:
            _room_moderators[room_name].remove(username)
    return True, f"{username} is no longer a moderator of #{room_name}"


def invite_user(room_name, inviter_username, target_username):
    if room_name not in _rooms:
        return False, "Room not found"
    if not is_member(room_name, inviter_username):
        return False, "You are not in this room"
    if room_name not in _room_invites:
        _room_invites[room_name] = []
    if target_username not in _room_invites[room_name]:
        _room_invites[room_name].append(target_username)
    return True, f"Invited {target_username} to #{room_name}"


def ban_from_room(room_name, target_username, requesting_user, is_admin, reason):
    if room_name not in _rooms:
        return False, "Room not found"
    if not is_admin and not is_moderator(room_name, requesting_user):
        return False, "Permission denied"
    room = _rooms[room_name]
    if target_username not in room["banned_users"]:
        room["banned_users"].append(target_username)
    # Remove from members
    if room_name in _room_members:
        if target_username in _room_members[room_name]:
            _room_members[room_name].remove(target_username)
    return True, f"{target_username} banned from #{room_name}"


def update_room_setting(room_name, setting_key, setting_value, requesting_user, is_admin):
    if room_name not in _rooms:
        return False, "Room not found"
    if not is_admin and _rooms[room_name]["creator"] != requesting_user:
        if not is_moderator(room_name, requesting_user):
            return False, "Permission denied"
    valid_settings = ["description", "topic", "welcome_message", "rules", "max_members", "password"]
    if setting_key not in valid_settings:
        return False, f"Invalid setting: {setting_key}"
    _rooms[room_name][setting_key] = setting_value
    return True, f"Setting '{setting_key}' updated"


def get_room_info(room_name):
    if room_name not in _rooms:
        return None
    room = dict(_rooms[room_name])
    room["member_count"] = get_room_member_count(room_name)
    room["members"] = get_room_members(room_name)
    room["moderators"] = list(_room_moderators.get(room_name, []))
    return room


def update_room_activity(room_name):
    if room_name in _rooms:
        _rooms[room_name]["last_activity"] = time.time()
        _rooms[room_name]["message_count"] += 1


def pin_message(room_name, msg_id, requesting_user, is_admin):
    if room_name not in _rooms:
        return False, "Room not found"
    if not is_admin and not is_moderator(room_name, requesting_user):
        return False, "Permission denied"
    if msg_id not in _rooms[room_name]["pinned_messages"]:
        _rooms[room_name]["pinned_messages"].append(msg_id)
        if len(_rooms[room_name]["pinned_messages"]) > 10:
            _rooms[room_name]["pinned_messages"].pop(0)
    return True, "Message pinned"


def unpin_message(room_name, msg_id, requesting_user, is_admin):
    if room_name not in _rooms:
        return False, "Room not found"
    if not is_admin and not is_moderator(room_name, requesting_user):
        return False, "Permission denied"
    if msg_id in _rooms[room_name]["pinned_messages"]:
        _rooms[room_name]["pinned_messages"].remove(msg_id)
    return True, "Message unpinned"


def get_pinned_messages(room_name):
    if room_name not in _rooms:
        return []
    return list(_rooms[room_name].get("pinned_messages", []))


def initialize_default_rooms():
    for room_name in _default_rooms:
        if room_name not in _rooms:
            create_room(
                room_name, "system", f"The {room_name} room",
                False, 1000, "", "", "public"
            )
            # system is not really in members
            if room_name in _room_members and "system" in _room_members[room_name]:
                _room_members[room_name].remove("system")


def get_rooms_for_user(username):
    rooms = []
    for room_name, members in _room_members.items():
        if username in members:
            rooms.append(room_name)
    return rooms


def search_rooms(query, include_private, limit):
    if query is None:
        query = ""
    if limit is None:
        limit = 20
    results = []
    q = query.lower()
    for name, room in _rooms.items():
        if not include_private and room["is_private"]:
            continue
        if q in name.lower():
            results.append(name)
            continue
        if q in room.get("description", "").lower():
            if name not in results:
                results.append(name)
            continue
        if q in room.get("topic", "").lower():
            if name not in results:
                results.append(name)
    return results[:limit]


def get_room_stats(room_name):
    if room_name not in _rooms:
        return None
    room = _rooms[room_name]
    return {
        "name": room_name,
        "member_count": get_room_member_count(room_name),
        "message_count": room["message_count"],
        "created_at": room["created_at"],
        "last_activity": room["last_activity"],
        "moderator_count": len(_room_moderators.get(room_name, [])),
        "is_private": room["is_private"],
        "pinned_count": len(room.get("pinned_messages", []))
    }


def handle_room_membership_event(event_type, room_name, username, is_admin, password, target_user, extra_data):
    """Handle join/leave/kick/ban/invite events - these all deal with membership"""
    if extra_data is None:
        extra_data = {}
    result = {"event": event_type, "room": room_name, "user": username}

    if event_type == "join":
        if room_name not in _rooms:
            return False, "Room not found", result
        if is_admin:
            if username not in _room_members.get(room_name, []):
                if room_name not in _room_members:
                    _room_members[room_name] = []
                _room_members[room_name].append(username)
            _rooms[room_name]["last_activity"] = time.time()
            result["joined"] = True
            return True, f"Admin joined #{room_name}", result
        else:
            ok, msg = join_room(room_name, username, password)
            result["joined"] = ok
            return ok, msg, result

    elif event_type == "leave":
        ok, msg = leave_room(room_name, username)
        result["left"] = ok
        return ok, msg, result

    elif event_type == "kick":
        if target_user is None:
            return False, "Target user required", result
        if not is_admin and not is_moderator(room_name, username):
            return False, "Permission denied", result
        if room_name not in _room_members or target_user not in _room_members[room_name]:
            return False, f"{target_user} not in room", result
        _room_members[room_name].remove(target_user)
        if room_name in _room_moderators and target_user in _room_moderators[room_name]:
            _room_moderators[room_name].remove(target_user)
        result["kicked"] = target_user
        return True, f"Kicked {target_user} from #{room_name}", result

    elif event_type == "ban":
        if target_user is None:
            return False, "Target user required", result
        reason = extra_data.get("reason", "No reason")
        ok, msg = ban_from_room(room_name, target_user, username, is_admin, reason)
        result["banned"] = target_user if ok else None
        return ok, msg, result

    elif event_type == "invite":
        if target_user is None:
            return False, "Target user required", result
        ok, msg = invite_user(room_name, username, target_user)
        result["invited"] = target_user if ok else None
        return ok, msg, result

    else:
        return False, f"Unknown membership event: {event_type}", result


def handle_room_settings_event(event_type, room_name, username, is_admin, extra_data, setting_key, setting_value, target_user):
    """Handle settings, moderation, create/delete events"""
    if extra_data is None:
        extra_data = {}
    result = {"event": event_type, "room": room_name}

    if event_type == "create":
        description = extra_data.get("description", "")
        is_private = extra_data.get("is_private", False)
        max_members = extra_data.get("max_members", 500)
        topic = extra_data.get("topic", "")
        room_type = extra_data.get("type", "public")
        password = extra_data.get("password", "")
        ok, msg = create_room(room_name, username, description, is_private, max_members, topic, password, room_type)
        result["created"] = ok
        return ok, msg, result

    elif event_type == "delete":
        ok, msg = delete_room(room_name, username, is_admin)
        result["deleted"] = ok
        return ok, msg, result

    elif event_type == "set_topic":
        if not is_admin and not is_moderator(room_name, username):
            return False, "Permission denied", result
        if room_name not in _rooms:
            return False, "Room not found", result
        topic_text = extra_data.get("topic", "")
        _rooms[room_name]["topic"] = topic_text
        result["topic"] = topic_text
        return True, f"Topic set to: {topic_text}", result

    elif event_type == "set_password":
        if not is_admin and not is_moderator(room_name, username):
            return False, "Permission denied", result
        if room_name not in _rooms:
            return False, "Room not found", result
        new_pw = extra_data.get("password", "")
        _rooms[room_name]["password"] = new_pw
        result["password_set"] = bool(new_pw)
        return True, "Password updated", result

    elif event_type == "add_moderator":
        if target_user is None:
            return False, "Target user required", result
        ok, msg = add_moderator(room_name, target_user, username, is_admin)
        result["moderator_added"] = target_user if ok else None
        return ok, msg, result

    elif event_type == "remove_moderator":
        if target_user is None:
            return False, "Target user required", result
        ok, msg = remove_moderator(room_name, target_user, username, is_admin)
        result["moderator_removed"] = target_user if ok else None
        return ok, msg, result

    elif event_type == "pin_message":
        msg_id = extra_data.get("msg_id")
        if msg_id is None:
            return False, "Message ID required", result
        ok, msg = pin_message(room_name, msg_id, username, is_admin)
        result["pinned"] = msg_id if ok else None
        return ok, msg, result

    elif event_type == "unpin_message":
        msg_id = extra_data.get("msg_id")
        if msg_id is None:
            return False, "Message ID required", result
        ok, msg = unpin_message(room_name, msg_id, username, is_admin)
        result["unpinned"] = msg_id if ok else None
        return ok, msg, result

    elif event_type == "update_setting":
        if setting_key is None:
            return False, "Setting key required", result
        ok, msg = update_room_setting(room_name, setting_key, setting_value, username, is_admin)
        result["setting"] = setting_key
        return ok, msg, result

    elif event_type == "get_info":
        info = get_room_info(room_name)
        if info is None:
            return False, "Room not found", result
        result.update(info)
        return True, "Room info retrieved", result

    else:
        return False, f"Unknown settings event: {event_type}", result


def bulk_room_operation(operation, room_names, admin_username, extra_data):
    """
    Apply an operation to multiple rooms. Used for batch admin operations.
    Returns dict of room_name -> (success, message).
    """
    if not operation or not room_names:
        return {}
    if extra_data is None:
        extra_data = {}

    results = {}

    for rname in room_names:
        if operation == "delete":
            ok, msg = delete_room(rname, admin_username, True)
            results[rname] = (ok, msg)

        elif operation == "set_max_members":
            new_max = extra_data.get("max_members", 500)
            if rname not in _rooms:
                results[rname] = (False, "Room not found")
                continue
            _rooms[rname]["max_members"] = new_max
            results[rname] = (True, f"Max members set to {new_max}")

        elif operation == "make_private":
            if rname not in _rooms:
                results[rname] = (False, "Room not found")
                continue
            _rooms[rname]["is_private"] = True
            results[rname] = (True, "Room made private")

        elif operation == "make_public":
            if rname not in _rooms:
                results[rname] = (False, "Room not found")
                continue
            _rooms[rname]["is_private"] = False
            results[rname] = (True, "Room made public")

        elif operation == "clear_banned":
            if rname not in _rooms:
                results[rname] = (False, "Room not found")
                continue
            _rooms[rname]["banned_users"] = []
            results[rname] = (True, "Banned users cleared")

        elif operation == "reset_topic":
            if rname not in _rooms:
                results[rname] = (False, "Room not found")
                continue
            _rooms[rname]["topic"] = ""
            results[rname] = (True, "Topic cleared")

        elif operation == "kick_all":
            if rname not in _room_members:
                results[rname] = (False, "No members data")
                continue
            kicked_count = len(_room_members[rname])
            _room_members[rname] = []
            results[rname] = (True, f"Kicked {kicked_count} users")

        else:
            results[rname] = (False, f"Unknown operation: {operation}")

    return results
