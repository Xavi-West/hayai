#!/usr/bin/env python3
"""Synchronize Hayai translations from Mihon/Yokai and fill Hayai-only strings.

The script deliberately writes checked-in XML files; builds stay offline.  It uses
the public Google Translate endpoint only for strings that neither upstream has
translated yet.  Run it before changing the base English catalogue:

    python tools/i18n/sync_translations.py --refresh-upstreams

Use --check in CI or locally to verify that no locale can silently fall back to
English because a resource key is missing.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import shutil
import subprocess
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
import urllib.parse
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "i18n" / "src" / "commonMain" / "moko-resources"
UPSTREAMS = {
    "mihon": "https://github.com/mihonapp/mihon.git",
    "yokai": "https://github.com/null2264/yokai.git",
}
ALIASES = {"iw": "he", "in": "id", "tl": "fil"}
TOKEN = re.compile(r"%(?:\d+\$)?[-+# 0,(]*\d*(?:\.\d+)?[a-zA-Z]|\\\\n|<[^>]+>")
MARKER = re.compile(r"#{2,3}\d{5}#{2,3}")
MAX_BATCH_CHARS = 1_600
REQUEST_INTERVAL_SECONDS = 0.25
_last_request_at = 0.0


def read_catalog(path: Path) -> tuple[list[ET.Element], dict[str, ET.Element]]:
    if not path.is_file():
        return [], {}
    root = ET.parse(path).getroot()
    entries = list(root)
    return entries, {entry.attrib["name"]: entry for entry in entries if "name" in entry.attrib}


def element_text(element: ET.Element) -> str:
    return element.text or ""


def plural_values(element: ET.Element) -> dict[str, str]:
    return {item.attrib["quantity"]: element_text(item) for item in element.findall("item")}


def upstream_locale(root: Path, locale: str) -> Path | None:
    candidates = (locale, ALIASES.get(locale, locale))
    for candidate in candidates:
        path = root / "i18n" / "src" / "commonMain" / "moko-resources" / candidate
        if path.is_dir():
            return path
    return None


def fetch_upstreams(target: Path) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for name, url in UPSTREAMS.items():
        destination = target / name
        subprocess.run(["git", "clone", "--depth", "1", url, str(destination)], check=True)
        result[name] = destination
    return result


def protected_text(value: str) -> tuple[str, list[str]]:
    tokens: list[str] = []

    def replace(match: re.Match[str]) -> str:
        tokens.append(match.group(0))
        return f'<x id="{len(tokens) - 1}"/>'

    return TOKEN.sub(replace, value), tokens


def restore_tokens(value: str, tokens: list[str]) -> str:
    for index, token in enumerate(tokens):
        value = value.replace(f'<x id="{index}"/>', token)
    return value


def translate_single_without_marker(language: str, value: str, tokens: list[str]) -> list[str]:
    return [restore_tokens(request_translation(language, value), tokens)]


def request_translation(language: str, payload: str) -> str:
    global _last_request_at
    for attempt in range(8):
        delay = REQUEST_INTERVAL_SECONDS - (time.monotonic() - _last_request_at)
        if delay > 0:
            time.sleep(delay)
        try:
            url = "https://translate.google.com/m?" + urllib.parse.urlencode({"sl": "en", "tl": language, "q": payload})
            request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(request, timeout=20) as response:
                _last_request_at = time.monotonic()
                content = response.read().decode("utf-8")
                match = re.search(r'<div class="result-container">(.*?)</div>', content, re.DOTALL)
                if not match:
                    raise ValueError("mobile translation response did not contain a result")
                return html.unescape(match.group(1))
        except urllib.error.HTTPError as error:
            _last_request_at = time.monotonic()
            if error.code != 429 or attempt == 7:
                raise
            time.sleep(30 * (attempt + 1))
    raise AssertionError("unreachable")


def translate_batch(language: str, values: list[str]) -> list[str]:
    protected = [protected_text(value) for value in values]
    payload = "\n".join(
        f"{text}\n###{index:05d}###" for index, (text, _) in enumerate(protected)
    )
    for attempt in range(4):
        try:
            translated = request_translation(language, payload)
            pieces = MARKER.split(translated)
            if len(pieces) != len(values) + 1:
                # Some language pairs normalize an unusually long marker batch.
                # Smaller batches keep the request deterministic without losing a
                # string or its format placeholders.
                if len(values) == 1:
                    return translate_single_without_marker(language, *protected[0])
                midpoint = len(values) // 2
                return translate_batch(language, values[:midpoint]) + translate_batch(language, values[midpoint:])
            translated_values = [pieces[index].strip() for index in range(len(values))]
            return [restore_tokens(value, tokens) for value, (_, tokens) in zip(translated_values, protected)]
        except (OSError, ValueError, json.JSONDecodeError) as error:
            if attempt == 3:
                raise RuntimeError(f"Could not translate {language}: {error}") from error
            time.sleep(2**attempt)
    raise AssertionError("unreachable")


def translate_missing(language: str, pending: list[tuple[str, str]]) -> dict[str, str]:
    batch: list[tuple[str, str]] = []
    batches: list[list[tuple[str, str]]] = []
    chars = 0
    for key, value in pending:
        if batch and chars + len(value) > MAX_BATCH_CHARS:
            batches.append(batch)
            batch, chars = [], 0
        batch.append((key, value))
        chars += len(value)
    if batch:
        batches.append(batch)

    translated: dict[str, str] = {}
    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = [
            executor.submit(translate_batch, translation_language(language), [value for _, value in current])
            for current in batches
        ]
        for current, future in zip(batches, futures):
            translated.update(zip((key for key, _ in current), future.result()))
    return translated


def translation_language(locale: str) -> str:
    return ALIASES.get(locale.replace("-r", "-"), locale.replace("-r", "-"))


def upstream_value(
    key: str,
    base: ET.Element,
    upstreams: list[tuple[dict[str, ET.Element], dict[str, ET.Element]]],
) -> str | None:
    for upstream_base, upstream_locale_catalog in upstreams:
        source = upstream_base.get(key)
        translation = upstream_locale_catalog.get(key)
        if source is not None and translation is not None and element_text(source) == element_text(base):
            return element_text(translation)
    return None


def write_catalog(path: Path, base_entries: list[ET.Element], values: dict[str, str], plurals: dict[str, dict[str, str]]) -> None:
    root = ET.Element("resources")
    for base in base_entries:
        name = base.attrib.get("name")
        if not name:
            continue
        copy = ET.SubElement(root, base.tag, base.attrib)
        if base.tag == "plurals":
            for base_item in base.findall("item"):
                item = ET.SubElement(copy, "item", base_item.attrib)
                item.text = plurals[name][base_item.attrib["quantity"]]
        else:
            copy.text = values[name]
    ET.indent(root, space="    ")
    path.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(root, encoding="unicode") + "\n", encoding="utf-8")


def sync(upstreams: dict[str, Path], check_only: bool, locales: set[str] | None = None) -> None:
    base_entries, base_catalog = read_catalog(RESOURCES / "base" / "strings.xml")
    base_plural_entries, base_plurals = read_catalog(RESOURCES / "base" / "plurals.xml")
    all_base_entries = base_entries + base_plural_entries
    upstream_catalogs = {
        name: (
            read_catalog(path / "i18n" / "src" / "commonMain" / "moko-resources" / "base" / "strings.xml")[1],
            read_catalog(path / "i18n" / "src" / "commonMain" / "moko-resources" / "base" / "plurals.xml")[1],
        )
        for name, path in upstreams.items()
    }
    missing_report: list[str] = []

    for locale_dir in sorted(path for path in RESOURCES.iterdir() if path.is_dir() and path.name != "base"):
        if locales and locale_dir.name not in locales:
            continue
        strings_entries, strings = read_catalog(locale_dir / "strings.xml")
        plural_entries, plurals = read_catalog(locale_dir / "plurals.xml")
        del strings_entries, plural_entries
        source_catalogs = []
        for name in ("mihon", "yokai"):
            upstream_dir = upstream_locale(upstreams[name], locale_dir.name)
            if upstream_dir:
                source_catalogs.append((upstream_catalogs[name][0], read_catalog(upstream_dir / "strings.xml")[1]))

        values: dict[str, str] = {}
        pending: list[tuple[str, str]] = []
        for name, base in base_catalog.items():
            if base.attrib.get("translatable") == "false":
                values[name] = element_text(base)
                continue
            value = upstream_value(name, base, source_catalogs) or (element_text(strings[name]) if name in strings else None)
            if value is None:
                pending.append((name, element_text(base)))
            else:
                values[name] = value

        plural_values_by_name: dict[str, dict[str, str]] = {}
        for name, base in base_plurals.items():
            current = plural_values(plurals[name]) if name in plurals else {}
            result: dict[str, str] = {}
            for item in base.findall("item"):
                quantity, source = item.attrib["quantity"], element_text(item)
                result[quantity] = current.get(quantity, source)
                if quantity not in current:
                    pending.append((f"{name}:{quantity}", source))
            plural_values_by_name[name] = result

        if check_only:
            if pending:
                missing_report.append(f"{locale_dir.name}: {len(pending)} missing translations")
            continue

        translated = translate_missing(locale_dir.name, pending)
        for name, _ in pending:
            if ":" in name:
                plural, quantity = name.split(":", 1)
                plural_values_by_name[plural][quantity] = translated[name]
            else:
                values[name] = translated[name]
        write_catalog(locale_dir / "strings.xml", base_entries, values, plural_values_by_name)
        write_catalog(locale_dir / "plurals.xml", base_plural_entries, {}, plural_values_by_name)
        print(f"Synced {locale_dir.name}: {len(pending)} machine-translated strings")

    if missing_report:
        raise SystemExit("\n".join(missing_report))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mihon-dir", type=Path)
    parser.add_argument("--yokai-dir", type=Path)
    parser.add_argument("--refresh-upstreams", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--locale", action="append", dest="locales", help="Sync only this locale; may be repeated.")
    args = parser.parse_args()

    if args.refresh_upstreams:
        with tempfile.TemporaryDirectory(prefix="hayai-i18n-") as temp:
            sync(fetch_upstreams(Path(temp)), args.check, set(args.locales or []))
        return
    if not args.mihon_dir or not args.yokai_dir:
        parser.error("pass --refresh-upstreams or both --mihon-dir and --yokai-dir")
    sync({"mihon": args.mihon_dir, "yokai": args.yokai_dir}, args.check, set(args.locales or []))


if __name__ == "__main__":
    main()
