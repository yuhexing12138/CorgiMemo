$filePath = 'c:\Users\Lenovo\Desktop\CorgiMemo\【刻记+】APP\corgimemo-showcase.html'
$content = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)

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

$newCode = @"
            // 功能筛选器 - 四大分类 + 子筛选器
            const filterBtns = document.querySelectorAll('.proto-filter-btn');
            const subFilterBtns = document.querySelectorAll('.proto-sub-filter-btn');
            const subFilterContainer = document.getElementById('coreSubFilter');
            const phones = document.querySelectorAll('#tab7 .phone-showcase');

            // 动态计算并显示各分类数量
            function updateFilterCounts() {
                const allCount = phones.length;
                document.querySelector('.proto-filter-btn[data-filter="all"]').textContent = '全部 (' + allCount + ')';

                const categories = ['core', 'corgi', 'smart', 'personal'];
                categories.forEach(cat => {
                    const count = document.querySelectorAll('#tab7 .phone-showcase[data-category="' + cat + '"]').length;
                    const btn = document.querySelector('.proto-filter-btn[data-filter="' + cat + '"]');
                    if (btn) {
                        const catNames = { core: '核心功能', corgi: '柯基系统', smart: '智能特性', personal: '个人中心' };
                        btn.textContent = catNames[cat] + ' (' + count + ')';
                    }
                });

                // 子分类数量
                const subCategories = ['todo', 'inspire', 'date'];
                subCategories.forEach(sub => {
                    const count = document.querySelectorAll('#tab7 .phone-showcase[data-sub-category="' + sub + '"]').length;
                    const btn = document.querySelector('.proto-sub-filter-btn[data-sub-filter="' + sub + '"]');
                    if (btn) {
                        const subNames = { todo: '待办', inspire: '灵感', date: '日期' };
                        btn.textContent = subNames[sub] + ' (' + count + ')';
                    }
                });
            }

            // 筛选显示逻辑
            function filterPhones(category, subCategory) {
                phones.forEach(phone => {
                    if (category === 'all') {
                        phone.style.display = '';
                    } else if (subCategory) {
                        const phoneCat = phone.getAttribute('data-category');
                        const phoneSub = phone.getAttribute('data-sub-category');
                        phone.style.display = (phoneCat === category && phoneSub === subCategory) ? '' : 'none';
                    } else {
                        const phoneCat = phone.getAttribute('data-category');
                        phone.style.display = (phoneCat === category) ? '' : 'none';
                    }
                });
            }

            // 一级筛选器点击
            filterBtns.forEach(btn => {
                btn.addEventListener('click', () => {
                    filterBtns.forEach(b => b.classList.remove('active'));
                    subFilterBtns.forEach(b => b.classList.remove('active'));
                    btn.classList.add('active');

                    const filter = btn.getAttribute('data-filter');

                    // 显示/隐藏子筛选器
                    if (filter === 'core' && subFilterContainer) {
                        subFilterContainer.classList.add('visible');
                    } else {
                        if (subFilterContainer) subFilterContainer.classList.remove('visible');
                    }

                    filterPhones(filter, null);
                });
            });

            // 二级子筛选器点击
            subFilterBtns.forEach(btn => {
                btn.addEventListener('click', () => {
                    subFilterBtns.forEach(b => b.classList.remove('active'));
                    btn.classList.add('active');

                    const subFilter = btn.getAttribute('data-sub-filter');
                    filterPhones('core', subFilter);
                });
            });

            // 初始化数量
            updateFilterCounts();
"@

if ($content.Contains($oldCode)) {
    $content = $content.Replace($oldCode, $newCode)
    [System.IO.File]::WriteAllText($filePath, $content, [System.Text.Encoding]::UTF8)
    Write-Output "Replacement successful!"
} else {
    Write-Output "Old code not found! Trying to find the pattern..."
    $index = $content.IndexOf('// 功能筛选器')
    Write-Output "Found '// 功能筛选器' at index: $index"
    if ($index -ge 0) {
        $snippet = $content.Substring($index, 200)
        Write-Output "Snippet around match:"
        Write-Output $snippet
    }
}
