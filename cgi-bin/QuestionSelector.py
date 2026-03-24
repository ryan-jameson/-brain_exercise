#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Simple CGI-like script for selecting cognitive training tasks.
Reads a topic from command-line args or POST body (stdin) and prints JSON.
"""
import json
import random
import re
import sys
from datetime import datetime
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parents[1]
NEUROFLEX_CONFIG = BASE_DIR / "neuroflex" / "src" / "config"

TOPIC_LIBRARY = {
    "schulte": {
        "prompt": "舒尔特方格 (Schulte Grid) - 寻找并按顺序点击出现的数字",
    },
    "stroop": {
        "prompt": "Stroop 色词干扰 (Stroop Test) - 选出字体的颜色，而不要管文字内容",
    },
    "sequence": {
        "prompt": "序列记忆连线 (Sequence Memory) - 记住顺序并将其按原序列重新排列",
        "easy": 4,      # 4个物品
        "hard": 8       # 8个物品
    },
    "mirror": {
        "prompt": "镜像手眼协调绘制 (Mirror Coordination) - 在右侧画出左侧图案的轴对称镜像",
        "easy": 9,      # 3x3
        "hard": 25      # 5x5
    },
    "categorize": {
        "prompt": "规则分类 (Rule-based Categorization) - 将物品根据规则进行分类",
    },
    "memory_story": {
        "prompt": "情景记忆 (Episodic Memory) - 记住出现的情景物品及对应位置",
    },
}


def read_stdin() -> str:
    if sys.stdin.isatty():
        return ""
    try:
        data = sys.stdin.read()
    except Exception:
        data = ""
    return data.strip()


def extract_topic(argv, body: str) -> str:
    if len(argv) > 1 and argv[1].strip():
        return argv[1].strip().lower()
    if body:
        # Try JSON first
        try:
            payload = json.loads(body)
            if isinstance(payload, dict) and "topic" in payload:
                return str(payload["topic"]).lower()
        except Exception:
            pass
        # Try simple query format: topic=xxx
        for part in body.split("&"):
            if part.startswith("topic="):
                return part.split("=", 1)[1].lower()
    return "memory"


def extract_difficulty(argv, body: str) -> str:
    if len(argv) > 2 and argv[2].strip():
        return argv[2].strip().lower()
    if body:
        try:
            payload = json.loads(body)
            if isinstance(payload, dict) and "difficulty" in payload:
                return str(payload["difficulty"]).lower()
        except Exception:
            pass
        for part in body.split("&"):
            if part.startswith("difficulty="):
                return part.split("=", 1)[1].lower()
    return "random"


def build_task(topic: str, difficulty: str) -> dict:
    if topic not in TOPIC_LIBRARY:
        topic = "schulte"
    entry = TOPIC_LIBRARY[topic]
    difficulty_level = normalize_difficulty(difficulty)
    task = {
        "type": topic,
        "prompt": entry["prompt"],
        "createdAt": datetime.now().isoformat(),
        "difficulty": difficulty_level,
    }

    if topic == "schulte":
        size = pick_count_by_difficulty(difficulty_level, [3, 5, 7])
        task["size"] = size
        numbers = list(range(1, size * size + 1))
        random.shuffle(numbers)
        task["grid"] = numbers
    elif topic == "stroop":
        colors_pool = load_neuroflex_pool("stroop.js", "colors")
        if not colors_pool:
            colors_pool = [{"name":"red", "label":"红", "value":"#ff3366"}, {"name":"blue", "label":"蓝", "value":"#00d4ff"}, {"name":"green", "label":"绿", "value":"#00ff88"}]
        count = pick_count_by_difficulty(difficulty_level, [5, 10, 15])
        trials = []
        for _ in range(count):
            text_color = random.choice(colors_pool)
            ink_color = random.choice(colors_pool) if random.random() > 0.3 else text_color
            trials.append({"text": text_color["label"], "color": ink_color["value"], "colorName": ink_color["name"], "colorLabel": ink_color["label"]})
        task["trials"] = trials
        task["colorsPool"] = colors_pool
    elif topic == "sequence":
        pool = load_neuroflex_pool("sequence.js", "itemPool")
        count = pick_count_by_difficulty(difficulty_level, [4, 6, 9])
        task["sequenceItems"] = random.sample(pool, k=min(count, len(pool))) if pool else []
    elif topic == "mirror":
        task["points"] = build_mirror_shape(difficulty_level)
    elif topic == "categorize":
        pool = load_neuroflex_pool("categorize.js", "itemPool")
        count = pick_count_by_difficulty(difficulty_level, [6, 12, 18])
        task["items"] = random.sample(pool, k=min(count, len(pool))) if pool else []
        task["rule"] = "请根据类别将物品分类"
    elif topic == "memory_story":
        pool = load_neuroflex_pool("memoryStory.js", "itemPool")
        count = pick_count_by_difficulty(difficulty_level, [5, 7, 9])
        task["items"] = random.sample(pool, k=min(count, len(pool))) if pool else []
        
    return task

def build_mirror_shape(diff: int):
    # generate a simple grid path for mirror drawing
    points = []
    length = [3, 5, 8][diff - 1]
    for i in range(length):
        points.append({"x": random.randint(1, 4), "y": random.randint(1, 4)})
    return points


def load_neuroflex_pool(file_name: str, export_name: str):
    path = NEUROFLEX_CONFIG / file_name
    if not path.exists():
        return []
    content = path.read_text(encoding="utf-8")
    array_text = extract_export_array(content, export_name)
    if not array_text:
        return []
    try:
        json_text = to_json_array(array_text)
        return json.loads(json_text)
    except Exception:
        return parse_simple_objects(array_text)


def extract_export_array(content: str, export_name: str) -> str:
    pattern = re.compile(rf"export\s+const\s+{re.escape(export_name)}\s*=\s*\[(.*?)\]", re.S)
    match = pattern.search(content)
    if not match:
        return ""
    return match.group(1)


def to_json_array(raw: str) -> str:
    # Remove line comments
    cleaned = re.sub(r"//.*", "", raw)
    # Quote keys
    cleaned = re.sub(r"([,{])\\s*(\w+)\\s*:", r"\1 \"\2\":", cleaned)
    # Replace single quotes with double quotes
    cleaned = cleaned.replace("'", '"')
    # Remove trailing commas before closing braces/brackets
    cleaned = re.sub(r",\s*([}\]])", r"\1", cleaned)
    return "[" + cleaned.strip() + "]"


def parse_simple_objects(raw: str):
    cleaned = re.sub(r"//.*", "", raw)
    items = []
    for match in re.finditer(r"\{([^}]*)\}", cleaned, re.S):
        block = match.group(1)
        item = {}
        for key, value in re.findall(r"(\w+)\s*:\s*([^,]+)", block):
            value = value.strip(" \r\n\t}")
            if value.startswith("'") and value.endswith("'"):
                item[key] = value.strip("'")
            elif value.startswith('"') and value.endswith('"'):
                item[key] = value.strip('"')
            else:
                try:
                    item[key] = int(value)
                except ValueError:
                    item[key] = value
        if item:
            items.append(item)
    return items


def normalize_difficulty(value: str) -> int:
    if not value:
        return random.randint(1, 3)
    value = value.lower()
    if value in {"1", "easy", "low"}:
        return 1
    if value in {"2", "medium", "mid", "normal"}:
        return 2
    if value in {"3", "hard", "high"}:
        return 3
    return random.randint(1, 3)


def pick_count_by_difficulty(level: int, options):
    if not options:
        return 0
    index = max(0, min(level - 1, len(options) - 1))
    return options[index]


def main() -> None:
    try:
        body = read_stdin()
        topic = extract_topic(sys.argv, body)
        difficulty = extract_difficulty(sys.argv, body)
        task = build_task(topic, difficulty)
        print(json.dumps(task, ensure_ascii=False))
    except Exception as exc:
        print(json.dumps({"error": f"QuestionSelector error: {exc}"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
