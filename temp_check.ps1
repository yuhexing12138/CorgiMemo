$filePath = "c:\Users\Lenovo\Desktop\CorgiMemo\【刻记+】APP\corgimemo-showcase.html"
$content = Get-Content -LiteralPath $filePath -Raw -Encoding UTF8

$oldCode = @"
            // 功能筛选器
            const filterBtns = document.querySelectorAll('.proto-filter-btn');
            filterBtns.forEach(btn => {
                btn.addEventListener('click', () => {
                    // 更新按钮状态
                    filterBtns.forEach(b => b.classList.remove('active'));
                    btn.classList.add('active');

                    const filter = btn.getAttribute('data-filter');
                    const phones = document.querySelectorAll('#tab7 .phone-showcase');

                    phones.forEach(phone => {
                        if (filter === 'all') {
                            phone.style.display = '';
                        } else {
                            const category = phone.getAttribute('data-category');
                            phone.style.display = (category === filter) ? '' : 'none';
                        }
                    });
                });
            });
"@

if ($content.Contains($oldCode)) {
    Write-Output "Old code found! Performing replacement..."
} else {
    Write-Output "Old code NOT found with exact match."
    $idx = $content.IndexOf("// 功能筛选器")
    Write-Output "Index of '// 功能筛选器': $idx"
    if ($idx -ge 0) {
        $snippet = $content.Substring($idx, [Math]::Min(500, $content.Length - $idx))
        Write-Output "=== SNIPPET START ==="
        Write-Output $snippet
        Write-Output "=== SNIPPET END ==="
    }
}
