#!/usr/bin/env python3
"""Generate COMMANDS.md from the bot sources (@BotInfo and the key enums).

Run from the repository root: python3 tools/gen_commands.py
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BOT_DIR = os.path.join(ROOT, "main", "java", "net", "ildar", "wurm", "bot")


def split_top(s, sep=","):
    """Split on sep outside of strings and brackets."""
    parts, depth, cur, i, in_str = [], 0, "", 0, False
    while i < len(s):
        c = s[i]
        if in_str:
            cur += c
            if c == "\\":
                cur += s[i + 1]
                i += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
            cur += c
        elif c in "([{":
            depth += 1
            cur += c
        elif c in ")]}":
            depth -= 1
            cur += c
        elif c == sep and depth == 0:
            parts.append(cur)
            cur = ""
        else:
            cur += c
        i += 1
    if cur.strip():
        parts.append(cur)
    return parts


def evaluate(expr):
    """Evaluate a Java string expression made of literals and `X.name()` joined by +."""
    out = []
    for term in split_top(expr, "+"):
        term = term.strip()
        if term.startswith('"') and term.endswith('"'):
            out.append(bytes(term[1:-1], "utf-8").decode("unicode_escape"))
        else:
            m = re.fullmatch(r"(\w+)\.name\(\)", term)
            out.append(m.group(1) if m else term)
    return "".join(out)


def enum_body(src, start):
    i = src.index("{", start) + 1
    depth, j, in_str = 1, i, False
    while depth:
        c = src[j]
        if in_str:
            if c == "\\":
                j += 1
            elif c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        j += 1
    return src[i:j - 1]


def parse_keys(src):
    keys = []
    for m in re.finditer(r"enum\s+\w+\s+implements\s+(?:Bot\.)?InputKey\s*\{", src):
        body = enum_body(src, m.start())
        # the constant list ends at the first top-level ';'
        constants = split_top(body, ";")[0]
        for const in split_top(constants):
            const = re.sub(r"//[^\n]*", "", const).strip()
            cm = re.match(r"(\w+)\s*\((.*)\)\s*$", const, re.S)
            if not cm:
                continue
            args = [evaluate(a) for a in split_top(cm.group(2))]
            if len(args) >= 3:
                keys.append((cm.group(1), args[0], args[1], args[2]))
    return keys


def parse_bot_info(src):
    m = re.search(r"@BotInfo\s*\((.*?)\)\s*(?:public\s+)?(?:abstract\s+)?class", src, re.S)
    if not m:
        return None
    info = {}
    for part in split_top(m.group(1)):
        k, _, v = part.partition("=")
        info[k.strip()] = evaluate(v.strip())
    return info


def read(name):
    with open(os.path.join(BOT_DIR, name), encoding="utf-8") as f:
        return f.read()


def key_rows(keys):
    rows = []
    for name, full, desc, usage in sorted(keys, key=lambda k: k[0]):
        usage = usage.replace("|", "\\|")
        cmd = "`%s%s`" % (name, (" " + usage) if usage else "")
        rows.append("| %s | %s | %s |" % (cmd, full, desc.replace("|", "\\|").replace("\n", " ")))
    return rows


def main():
    base_keys = parse_keys(read("Bot.java"))
    area_keys = parse_keys(read("AreaAssistant.java"))
    bots = []
    for name in sorted(os.listdir(BOT_DIR)):
        if not name.endswith(".java"):
            continue
        src = read(name)
        info = parse_bot_info(src)
        if not info:
            continue
        keys = parse_keys(src)
        if "new AreaAssistant(" in src:
            keys += area_keys
        bots.append((info, keys))
    bots.sort(key=lambda b: b[0].get("name", "").lower())

    lines = [
        "# Commands",
        "",
        "Generated from the sources by `tools/gen_commands.py` - don't edit by hand.",
        "",
        "Use `bot <bot> <key> [arguments]`. A bot is named by its abbreviation or full name, and a key by its",
        "short name or full name (`bot ch s 0.9` = `bot chopper stamina 0.9`). Keys can be set before the bot",
        "is turned on. See the README for the general usage.",
        "",
        "## Keys every bot has",
        "",
        "| Key | Name | Description |",
        "|---|---|---|",
    ] + key_rows(base_keys) + [
        "| `on` | On | Start the bot (`on <key> [arguments]` sets a key first) |",
        "",
        "## Bots",
        "",
        "| Bot | Abbreviation | Description |",
        "|---|---|---|",
    ]
    for info, _ in bots:
        anchor = re.sub(r"[^a-z0-9 -]", "", info["name"].lower()).replace(" ", "-")
        lines.append("| [%s](#%s) | `%s` | %s |" % (info["name"], anchor, info["abbreviation"], info["description"]))
    for info, keys in bots:
        lines += ["", "## %s" % info["name"], "", "`bot %s` - %s" % (info["abbreviation"], info["description"]), ""]
        lines += ["| Key | Name | Description |", "|---|---|---|"] + key_rows(keys)
    with open(os.path.join(ROOT, "COMMANDS.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("COMMANDS.md: %d bots" % len(bots))


if __name__ == "__main__":
    main()
