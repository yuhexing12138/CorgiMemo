"""
批量图片压缩脚本 - PNG转WebP
将 corgi 文件夹中的PNG图片转换为WebP格式，目标大小约100KB
"""
import os
from PIL import Image
import pathlib

# 配置参数
SOURCE_DIR = pathlib.Path(r"【刻记+】APP\assets\corgi")
OUTPUT_DIR = pathlib.Path(r"【刻记+】APP\assets\corgi_webp")  # 输出到新目录
QUALITY = 80  # WebP质量参数 (0-100)，80为推荐值
TARGET_SIZE_KB = 100  # 目标大小（KB）

def convert_png_to_webp():
    """
    批量转换PNG图片为WebP格式
    使用二分查找法自动调整质量参数以达到目标文件大小
    """
    # 创建输出目录
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # 获取所有PNG文件
    png_files = list(SOURCE_DIR.glob("*.png"))
    total = len(png_files)
    print(f"找到 {total} 张PNG图片，开始转换...\n")

    success_count = 0
    results = []

    for idx, png_path in enumerate(png_files, 1):
        filename = png_path.name
        webp_path = OUTPUT_DIR / (png_path.stem + ".webp")

        try:
            # 打开原始图片
            with Image.open(png_path) as img:
                # 转换为RGBA模式（保留透明通道）
                if img.mode != 'RGBA':
                    img = img.convert('RGBA')

                # 使用自适应质量调整以达到目标大小
                quality = find_optimal_quality(img, webp_path, TARGET_SIZE_KB)

                # 保存为WebP
                img.save(webp_path, 'WEBP', quality=quality, method=6)

            # 获取文件大小
            size_kb = webp_path.stat().st_size / 1024
            original_size_kb = png_path.stat().st_size / 1024
            ratio = (1 - size_kb / original_size_kb) * 100

            results.append({
                'file': filename,
                'original': f"{original_size_kb:.1f}KB",
                'compressed': f"{size_kb:.1f}KB",
                'ratio': f"{ratio:.1f}%"
            })

            status = "✓" if size_kb <= TARGET_SIZE_KB * 1.2 else "△"
            print(f"[{idx}/{total}] {status} {filename}")
            print(f"    原始: {original_size_kb:.1f}KB → 压缩: {size_kb:.1f}KB (缩减{ratio:.1f}%, 质量={quality})")

            success_count += 1

        except Exception as e:
            print(f"[{idx}/{total}] ✗ {filename} - 错误: {e}")
            results.append({
                'file': filename,
                'original': 'ERROR',
                'compressed': 'ERROR',
                'ratio': str(e)
            })

    # 打印汇总
    print("\n" + "="*60)
    print("转换完成！")
    print(f"成功: {success_count}/{total} 张")
    print(f"输出目录: {OUTPUT_DIR.absolute()}")
    print("="*60)

    # 显示详细结果表格
    print("\n详细结果:")
    print(f"{'文件名':<30} {'原始大小':>10} {'压缩后':>10} {'缩减率':>8}")
    print("-"*60)
    for r in results:
        print(f"{r['file']:<30} {r['original']:>10} {r['compressed']:>10} {r['ratio']:>8}")


def find_optimal_quality(img, target_path, target_size_kb, max_iterations=10):
    """
    使用二分查找法找到最优的质量参数
    目标：使输出文件大小接近目标值（target_size_kb）
    """
    low, high = 60, 95  # 质量参数范围
    best_quality = 80

    for _ in range(max_iterations):
        mid = (low + high) // 2

        # 临时保存以测试大小
        import tempfile
        with tempfile.NamedTemporaryFile(suffix='.webp', delete=False) as tmp:
            tmp_path = tmp.name

        img.save(tmp_path, 'WEBP', quality=mid, method=6)
        size_kb = os.path.getsize(tmp_path) / 1024
        os.unlink(tmp_path)

        # 根据文件大小调整质量范围
        if size_kb > target_size_kb:
            # 文件太大，降低质量
            high = mid - 1
        else:
            # 文件足够小，尝试提高质量
            best_quality = mid
            low = mid + 1

        # 收敛检查
        if abs(size_kb - target_size_kb) < target_size_kb * 0.1:  # 误差10%以内
            break

    return best_quality


if __name__ == "__main__":
    convert_png_to_webp()
