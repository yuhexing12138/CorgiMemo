#!/usr/bin/env python3
"""在 Gradle 缓存中搜索「包含指定类名/文件名」的 *-sources.jar，输出可直接喂给 extract_source.py 的绝对路径。

解决的问题：Gradle 缓存路径中的 <hash> 目录名不可预测，
`~/.gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<hash>/<artifact>-<version>-sources.jar`
（<group> 是点分隔的扁平目录名，如 androidx.compose.ui，**不是** androidx/compose/ui）
在只知道「我要找 PointerInputChange 这个类」时无法拼出来。本脚本遍历缓存、打开每个 sources jar
的 namelist 做子串匹配，把命中结果连同内部条目路径一起打印。

用法:
    python find_sources_jar.py --class PointerInputChange
    python find_sources_jar.py --class AndroidFont --group androidx.compose.ui --version 1.11.2
    python find_sources_jar.py --class Color --group androidx.compose.ui --artifact ui-graphics

参数:
    --class      必需。类名或文件名子串（如 AndroidFont、PointerEvent.kt）。
    --group      可选。group 目录名过滤（如 androidx.compose.ui），显著加速。
                 注意：files-2.1 下是**点分隔的扁平目录名**，不是 androidx/compose/ui 这种嵌套路径。
    --artifact   可选。artifact 名过滤（如 ui-graphics；注意 -android 产物同样含 commonMain）。
    --version    可选。版本号过滤（如 1.11.2）。
    --cache      可选。Gradle 缓存根目录，默认 ~/.gradle/caches/modules-2/files-2.1。
    --limit      可选。最多打印多少条命中，默认 20。

退出码: 0 = 有命中；1 = 无命中；2 = 缓存目录不存在。
"""
import argparse
import os
import sys
import zipfile


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Find Gradle-cached *-sources.jar files containing a given class/file."
    )
    parser.add_argument("--class", dest="cls", required=True,
                        help="Class name or file-name substring to match (e.g. AndroidFont).")
    parser.add_argument("--group", default=None, help="Filter by Maven group (e.g. androidx.compose.ui).")
    parser.add_argument("--artifact", default=None, help="Filter by artifact name (e.g. ui-graphics).")
    parser.add_argument("--version", default=None, help="Filter by version (e.g. 1.11.2).")
    parser.add_argument("--cache", default="~/.gradle/caches/modules-2/files-2.1",
                        help="Gradle modules cache root.")
    parser.add_argument("--limit", type=int, default=20, help="Max hits to print.")
    args = parser.parse_args()

    # files-2.1 的 group 目录是点分隔的扁平名（androidx.compose.ui），不做 dot→slash 转换
    cache_root = os.path.normpath(os.path.expanduser(args.cache))
    if not os.path.isdir(cache_root):
        print(f"ERROR: gradle cache not found: {cache_root}", file=sys.stderr)
        return 2

    # 先在目录层面按 group / artifact / version 剪枝，避免无谓地打开大量 jar
    group_dir = os.path.join(cache_root, args.group) if args.group else cache_root
    if not os.path.isdir(group_dir):
        print(f"ERROR: group dir not found: {group_dir}", file=sys.stderr)
        print("提示: group 目录是点分隔的扁平名，例如 androidx.compose.ui", file=sys.stderr)
        return 2

    hits = []
    scanned = 0
    for root, _dirs, files in os.walk(group_dir):
        for name in files:
            if not name.endswith("-sources.jar"):
                continue
            parts = root.replace("\\", "/").split("/")
            if args.artifact and args.artifact not in parts:
                continue
            if args.version and args.version not in parts:
                continue
            jar_path = os.path.join(root, name)
            scanned += 1
            try:
                with zipfile.ZipFile(jar_path) as z:
                    matched = [n for n in z.namelist()
                               if args.cls in n and n.endswith((".kt", ".java"))]
            except Exception as exc:  # 损坏/非 zip，跳过即可
                print(f"  [skip] {jar_path} ({exc})", file=sys.stderr)
                continue
            if matched:
                hits.append((jar_path, matched))

    print(f"Scanned {scanned} sources jar(s) under {group_dir}")
    if not hits:
        print(f"No sources jar contains '{args.cls}'. 尝试去掉 --artifact/--version 过滤，"
              f"或改用 extract_source.py 直接指定 jar。")
        return 1

    print(f"Found {len(hits)} jar(s) containing '{args.cls}':\n")
    for jar_path, matched in hits[: args.limit]:
        print(f"  {jar_path}")
        for entry in matched[:5]:
            print(f"      - {entry}")
        if len(matched) > 5:
            print(f"      ... ({len(matched) - 5} more)")
    if len(hits) > args.limit:
        print(f"\n  ... ({len(hits) - args.limit} more jars, raise --limit to see all)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
