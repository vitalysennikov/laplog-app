#!/usr/bin/env python3
"""
Исправляет бэкап:
1. Заменяет "PLEASE SELECT TWO DISTINCT LANGUAGES" на None
2. Заполняет name_en/name_ru/name_zh из словаря (для известных имён)
3. Переводит notes через mymemory API (ru→en, ru→zh)
"""
import json
import time
import urllib.request
import urllib.parse

INPUT = "laplog_backup_2026-05-21_141736.json"
OUTPUT = "laplog_backup_fixed.json"
ERROR_STRING = "PLEASE SELECT TWO DISTINCT LANGUAGES"

# Словарь имён: ru → {en, zh}
NAME_DICT = {
    "дыхание сокола":     {"en": "falcon breath",       "ru": "дыхание сокола",      "zh": "猎鹰呼吸"},
    "задержки на выдохе": {"en": "expiratory retention", "ru": "задержки на выдохе",  "zh": "呼气潴留"},
    "лад живота":         {"en": "abdominal fret",       "ru": "лад живота",           "zh": "腹部疼痛"},
    "дерево жизни":       {"en": "The Tree of Life",     "ru": "дерево жизни",         "zh": "生命之树"},
    "дыхание медведя":    {"en": "bear's breath",        "ru": "дыхание медведя",      "zh": "熊的呼吸"},
    "дыхание полоза":     {"en": "snake breathing",      "ru": "дыхание полоза",       "zh": "蛇呼吸"},
}

api_call_count = 0

def mymemory_translate(text, from_lang, to_lang):
    global api_call_count
    if not text or not text.strip():
        return None
    lang_map = {"ru": "ru-RU", "en": "en-US", "zh": "zh-CN"}
    lang_pair = f"{lang_map[from_lang]}|{lang_map[to_lang]}"
    encoded = urllib.parse.quote(text)
    url = f"https://api.mymemory.translated.net/get?q={encoded}&langpair={lang_pair}"
    try:
        api_call_count += 1
        req = urllib.request.Request(url, headers={"User-Agent": "laplog-fix/1.0"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read())
        result = data.get("responseData", {}).get("translatedText", "")
        if result == ERROR_STRING:
            print(f"  [API ERROR] {from_lang}→{to_lang}: API returned error string")
            return None
        if result and result != text:
            return result
        print(f"  [API] {from_lang}→{to_lang}: no translation (same text or empty)")
        return None
    except Exception as e:
        print(f"  [API FAIL] {from_lang}→{to_lang}: {e}")
        return None

def clean(val):
    if val == ERROR_STRING:
        return None
    return val

def fix_notes(session):
    notes = session.get("notes")
    if not notes:
        return

    sid = session["id"]
    notes_en = clean(session.get("notes_en"))
    notes_ru = clean(session.get("notes_ru"))
    notes_zh = clean(session.get("notes_zh"))

    changed = False

    if notes_en is None:
        print(f"  Session {sid}: translating notes ru→en")
        notes_en = mymemory_translate(notes, "ru", "en")
        time.sleep(0.3)
        changed = True

    if notes_ru is None:
        # оригинал и так русский
        notes_ru = notes
        changed = True

    if notes_zh is None:
        print(f"  Session {sid}: translating notes ru→zh")
        notes_zh = mymemory_translate(notes, "ru", "zh")
        time.sleep(0.3)
        changed = True

    if changed:
        session["notes_en"] = notes_en
        session["notes_ru"] = notes_ru
        session["notes_zh"] = notes_zh

def fix_session(session):
    sid = session["id"]
    name = session.get("name")

    # Чистим ошибки
    for field in ["name_en", "name_ru", "name_zh", "notes_en", "notes_ru", "notes_zh"]:
        session[field] = clean(session.get(field))

    # Имя из словаря
    if name and name in NAME_DICT:
        d = NAME_DICT[name]
        if session.get("name_en") is None:
            session["name_en"] = d["en"]
        if session.get("name_ru") is None:
            session["name_ru"] = d["ru"]
        if session.get("name_zh") is None:
            session["name_zh"] = d["zh"]
    elif name and any(session.get(f) is None for f in ["name_en", "name_ru", "name_zh"]):
        print(f"  Session {sid}: unknown name '{name}', skipping name translation")

    # Заметки
    fix_notes(session)

with open(INPUT, encoding="utf-8") as f:
    backup = json.load(f)

sessions = backup["sessions"]
print(f"Обрабатываю {len(sessions)} сессий...")

for i, s in enumerate(sessions):
    has_issues = (
        any(s.get(f) == ERROR_STRING for f in ["name_en","name_ru","name_zh","notes_en","notes_ru","notes_zh"]) or
        (s.get("name") and any(s.get(f) is None for f in ["name_en","name_ru","name_zh"])) or
        (s.get("notes") and any(s.get(f) is None for f in ["notes_en","notes_ru","notes_zh"]))
    )
    if has_issues:
        print(f"[{i+1}/{len(sessions)}] Session {s['id']} (name='{s.get('name')}', notes={'yes' if s.get('notes') else 'no'})")
        fix_session(s)
    else:
        print(f"[{i+1}/{len(sessions)}] Session {s['id']} — OK")

with open(OUTPUT, "w", encoding="utf-8") as f:
    json.dump(backup, f, ensure_ascii=False, indent=2)

print(f"\nГотово. API-запросов: {api_call_count}")
print(f"Файл сохранён: {OUTPUT}")
