#!/usr/bin/env python3
"""从 Gradle 缓存的 *-sources.jar 中按子串匹配并抽取 .kt 源文件。

用法:
    python extract_source.py --jar <path-to-sources.jar> --match <ClassName> [--out <dir>]

示例:
    python extract_source.py \
        --jar "~/.gradle/caches/modules-2/files-2.1/androidx.compose.ui/ui-text-android/1.11.2/<hash>/ui-text-android-1.11.2-sources.jar" \
        --match AndroidFont --out /tmp/lookup
"""
import argparse
import os
import sys
import zipfile


def main() -> int:
    parser = argparse.ArgumentParser(description="Extract .kt entries from a Gradle sources jar by substring match.")
    parser.add_argument("--jar", required=True, help="Path to the *-sources.jar file.")
    parser.add_argument("--match", required=True, help="Substring to match against entry names (e.g. a class name).")
    parser.add_argument("--out", default="extracted_src", help="Output directory for extracted files.")
    args = parser.parse_args()

    jar_path = os.path.expanduser(args.jar)
    if not os.path.isfile(jar_path):
        print(f"ERROR: jar not found: {jar_path}", file=sys.stderr)
        return 2

    with zipfile.ZipFile(jar_path) as z:
        entries = [n for n in z.namelist() if n.endswith((".kt", ".java"))]
        matches = [n for n in entries if args.match in n]

    if not matches:
        print(f"No entries matching '{args.match}'. {len(entries)} source files total. Sample:")
        for n in entries[:20]:
            print("  ", n)
        return 1

    os.makedirs(args.out, exist_ok=True)
    print(f"Found {len(matches)} match(es) for '{args.match}':")
    with zipfile.ZipFile(jar_path) as z:
        for name in matches:
            dest = os.path.join(args.out, os.path.basename(name))
            with open(dest, "wb") as f:
                f.write(z.read(name))
            print(f"  extracted -> {dest}  ({name})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
