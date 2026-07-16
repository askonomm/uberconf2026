import time
import json
import os
import re
from datetime import datetime


# Global message storage - just use a dict, it's fine
_messages = {}
_private_messages = {}
_message_counter = 0
_max_messages_per_room = 500
_search_index = {}


def add_message(room_name, username, content, msg_type, color_code, timestamp, metadata):
    global _message_counter
    if room_name is None or room_name == "":
        return False
    if username is None or username == "":
        return False
    if content is None:
        content = ""
    if msg_type is None:
        msg_type = "normal"
    if color_code is None:
        color_code = "\033[0m"
    if timestamp is None:
        timestamp = time.time()
    if metadata is None:
        metadata = {}

    if room_name not in _messages:
        _messages[room_name] = []

    _message_counter += 1
    msg_id = _message_counter

    message = {
        "id": msg_id,
        "room": room_name,
        "username": username,
        "content": content,
        "type": msg_type,
        "color": color_code,
        "timestamp": timestamp,
        "metadata": metadata,
        "edited": False,
        "edit_history": []
    }

    _messages[room_name].append(message)

    # Update search index
    if room_name not in _search_index:
        _search_index[room_name] = {}
    words = content.lower().split()
    for word in words:
        word = re.sub(r'[^a-z0-9]', '', word)
        if word:
            if word not in _search_index[room_name]:
                _search_index[room_name][word] = []
            _search_index[room_name][word].append(msg_id)

    # Trim if too many messages
    if len(_messages[room_name]) > _max_messages_per_room:
        removed = _messages[room_name].pop(0)
        # Clean up search index for removed message
        removed_words = removed["content"].lower().split()
        for word in removed_words:
            word = re.sub(r'[^a-z0-9]', '', word)
            if word and word in _search_index[room_name]:
                if removed["id"] in _search_index[room_name][word]:
                    _search_index[room_name][word].remove(removed["id"])
                if not _search_index[room_name][word]:
                    del _search_index[room_name][word]

    return msg_id


def add_private_message(from_user, to_user, content, timestamp, metadata):
    global _message_counter
    if from_user is None or from_user == "":
        return False
    if to_user is None or to_user == "":
        return False
    if content is None:
        content = ""
    if timestamp is None:
        timestamp = time.time()
    if metadata is None:
        metadata = {}

    _message_counter += 1
    msg_id = _message_counter

    # Store under both users so both can retrieve it
    key = tuple(sorted([from_user, to_user]))
    key_str = f"{key[0]}::{key[1]}"

    if key_str not in _private_messages:
        _private_messages[key_str] = []

    message = {
        "id": msg_id,
        "from": from_user,
        "to": to_user,
        "content": content,
        "timestamp": timestamp,
        "metadata": metadata,
        "read": False
    }

    _private_messages[key_str].append(message)

    # Mark as unread for recipient
    if len(_private_messages[key_str]) > 200:
        _private_messages[key_str].pop(0)

    return msg_id


def get_messages(room_name, limit, offset, since_timestamp, msg_type_filter):
    if room_name not in _messages:
        return []
    if limit is None:
        limit = 50
    if offset is None:
        offset = 0
    if since_timestamp is None:
        since_timestamp = 0

    results = []
    all_msgs = _messages[room_name]

    for msg in all_msgs:
        if msg["timestamp"] < since_timestamp:
            continue
        if msg_type_filter is not None and msg["type"] != msg_type_filter:
            continue
        results.append(msg)

    # Apply offset and limit
    results = results[offset:]
    if limit > 0:
        results = results[:limit]

    return results


def get_recent_messages(room_name, count):
    if room_name not in _messages:
        return []
    if count is None or count <= 0:
        count = 20
    msgs = _messages[room_name]
    if len(msgs) <= count:
        return list(msgs)
    return list(msgs[-count:])


def get_private_messages(user1, user2, limit, offset):
    key = tuple(sorted([user1, user2]))
    key_str = f"{key[0]}::{key[1]}"
    if key_str not in _private_messages:
        return []
    if limit is None:
        limit = 50
    if offset is None:
        offset = 0
    msgs = _private_messages[key_str]
    msgs = msgs[offset:]
    if limit > 0:
        msgs = msgs[:limit]
    return list(msgs)


def search_messages(room_name, query, limit, case_sensitive, search_username):
    if room_name not in _messages:
        return []
    if query is None or query == "":
        return []
    if limit is None:
        limit = 20
    if case_sensitive is None:
        case_sensitive = False

    results = []

    if not case_sensitive:
        query_lower = query.lower()
        for msg in _messages[room_name]:
            if search_username:
                if query_lower in msg["username"].lower():
                    results.append(msg)
                    continue
            if query_lower in msg["content"].lower():
                results.append(msg)
    else:
        for msg in _messages[room_name]:
            if search_username:
                if query in msg["username"]:
                    results.append(msg)
                    continue
            if query in msg["content"]:
                results.append(msg)

    if limit > 0:
        results = results[-limit:]

    return results


def search_all_rooms(query, rooms_list, limit, case_sensitive):
    if query is None or query == "":
        return {}
    if limit is None:
        limit = 10
    if case_sensitive is None:
        case_sensitive = False

    all_results = {}

    for room in rooms_list:
        if room not in _messages:
            continue
        room_results = []
        if not case_sensitive:
            q = query.lower()
            for msg in _messages[room]:
                if q in msg["content"].lower():
                    room_results.append(msg)
        else:
            for msg in _messages[room]:
                if query in msg["content"]:
                    room_results.append(msg)
        if room_results:
            all_results[room] = room_results[-limit:]

    return all_results


def get_message_count(room_name):
    if room_name not in _messages:
        return 0
    return len(_messages[room_name])


def get_all_rooms_with_messages():
    return list(_messages.keys())


def delete_message(room_name, msg_id, requesting_user, is_admin):
    if room_name not in _messages:
        return False
    for i, msg in enumerate(_messages[room_name]):
        if msg["id"] == msg_id:
            if is_admin or msg["username"] == requesting_user:
                _messages[room_name].pop(i)
                return True
            else:
                return False
    return False


def edit_message(room_name, msg_id, new_content, requesting_user):
    if room_name not in _messages:
        return False
    for msg in _messages[room_name]:
        if msg["id"] == msg_id:
            if msg["username"] != requesting_user:
                return False
            msg["edit_history"].append({
                "content": msg["content"],
                "edited_at": time.time()
            })
            msg["content"] = new_content
            msg["edited"] = True
            # Update search index
            if room_name in _search_index:
                old_words = msg["edit_history"][-1]["content"].lower().split()
                for word in old_words:
                    word = re.sub(r'[^a-z0-9]', '', word)
                    if word and word in _search_index[room_name]:
                        if msg_id in _search_index[room_name][word]:
                            _search_index[room_name][word].remove(msg_id)
                new_words = new_content.lower().split()
                for word in new_words:
                    word = re.sub(r'[^a-z0-9]', '', word)
                    if word:
                        if word not in _search_index[room_name]:
                            _search_index[room_name][word] = []
                        _search_index[room_name][word].append(msg_id)
            return True
    return False


def get_stats(room_name):
    stats = {
        "total_messages": 0,
        "unique_users": set(),
        "message_types": {},
        "first_message_time": None,
        "last_message_time": None,
        "avg_message_length": 0,
        "longest_message": 0,
        "shortest_message": 9999999
    }

    if room_name not in _messages:
        return stats

    msgs = _messages[room_name]
    stats["total_messages"] = len(msgs)

    total_length = 0
    for msg in msgs:
        stats["unique_users"].add(msg["username"])
        mtype = msg["type"]
        if mtype not in stats["message_types"]:
            stats["message_types"][mtype] = 0
        stats["message_types"][mtype] += 1

        if stats["first_message_time"] is None:
            stats["first_message_time"] = msg["timestamp"]
        stats["last_message_time"] = msg["timestamp"]

        content_len = len(msg["content"])
        total_length += content_len
        if content_len > stats["longest_message"]:
            stats["longest_message"] = content_len
        if content_len < stats["shortest_message"]:
            stats["shortest_message"] = content_len

    if stats["total_messages"] > 0:
        stats["avg_message_length"] = total_length / stats["total_messages"]
    else:
        stats["shortest_message"] = 0

    stats["unique_users"] = len(stats["unique_users"])
    return stats


def export_messages(room_name, format_type, output_path):
    if room_name not in _messages:
        return False

    msgs = _messages[room_name]

    if format_type == "json":
        try:
            with open(output_path, 'w') as f:
                exportable = []
                for msg in msgs:
                    exportable.append({
                        "id": msg["id"],
                        "username": msg["username"],
                        "content": msg["content"],
                        "timestamp": msg["timestamp"],
                        "type": msg["type"]
                    })
                json.dump(exportable, f, indent=2)
            return True
        except Exception as e:
            return False
    elif format_type == "txt":
        try:
            with open(output_path, 'w') as f:
                for msg in msgs:
                    dt = datetime.fromtimestamp(msg["timestamp"])
                    f.write(f"[{dt.strftime('%Y-%m-%d %H:%M:%S')}] {msg['username']}: {msg['content']}\n")
            return True
        except Exception as e:
            return False
    else:
        return False


def clear_room_messages(room_name):
    if room_name in _messages:
        _messages[room_name] = []
    if room_name in _search_index:
        _search_index[room_name] = {}
    return True


def mark_private_messages_read(from_user, to_user):
    key = tuple(sorted([from_user, to_user]))
    key_str = f"{key[0]}::{key[1]}"
    if key_str not in _private_messages:
        return 0
    count = 0
    for msg in _private_messages[key_str]:
        if msg["to"] == to_user and not msg["read"]:
            msg["read"] = True
            count += 1
    return count


def get_unread_private_count(username):
    count = 0
    for key_str, msgs in _private_messages.items():
        for msg in msgs:
            if msg["to"] == username and not msg["read"]:
                count += 1
    return count


def get_message_by_id(msg_id):
    for room_name, msgs in _messages.items():
        for msg in msgs:
            if msg["id"] == msg_id:
                return msg
    return None


def get_user_message_count(room_name, username):
    if room_name not in _messages:
        return 0
    count = 0
    for msg in _messages[room_name]:
        if msg["username"] == username:
            count += 1
    return count


def get_room_active_users(room_name, since_seconds):
    if room_name not in _messages:
        return []
    if since_seconds is None:
        since_seconds = 3600
    cutoff = time.time() - since_seconds
    active = set()
    for msg in _messages[room_name]:
        if msg["timestamp"] >= cutoff:
            active.add(msg["username"])
    return list(active)
