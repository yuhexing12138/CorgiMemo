import re, io, sys, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
base = r"C:\Users\EDY\Desktop\CorgiMemo\app\src\main\java\com\corgimemo\app\ui\screens"
files = [
 "inspiration/InspirationScreen.kt","inspiration/InspirationEditScreen.kt","inspiration/InspirationViewScreen.kt",
 "inspiration/stats/InspirationStatsScreen.kt","inspiration/stats/ChartFullscreenScreen.kt",
 "date/SpecialDateScreen.kt","date/SpecialDateQuickCreateScreen.kt","date/SpecialDateDetailScreen.kt",
 "date/SpecialDateCardStyleScreen.kt","date/stats/DateStatsScreen.kt","todo/TodoEditScreen.kt",
 "common/ImagePreviewScreen.kt","home/HomeScreen.kt",
 "onboarding/WelcomePage.kt","onboarding/UserTypePage.kt","onboarding/FunctionOverviewPage.kt",
 "onboarding/TodoFeaturePage.kt","onboarding/InspirationFeaturePage.kt","onboarding/DateFeaturePage.kt",
 "onboarding/CorgiSystemPage.kt","onboarding/CorgiNamingPage.kt","onboarding/PermissionPage.kt",
 "onboarding/CompletionPage.kt","onboarding/OnboardingScreen.kt",
]
def strings(fp):
    try:
        with open(fp, encoding='utf-8') as f: t=f.read()
    except Exception as e:
        return ["<ERR %s>"%e]
    out=[]
    for m in re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', t):
        if re.search(r'[一-龥]', m) and len(m)<42 and not m.startswith(('com.','import')):
            out.append(m)
    seen=[]
    for s in out:
        if s not in seen: seen.append(s)
    return seen
for f in files:
    p=os.path.join(base,f)
    ss=strings(p)
    print("=== "+f+" ===")
    print(" · ".join(ss[:24]))
    print()
