$lines = [System.IO.File]::ReadAllLines('c:\Users\Lenovo\Desktop\CorgiMemo\【刻记+】APP\corgimemo-showcase.html', [System.Text.Encoding]::UTF8)
for ($i = 6041; $i -le 6062; $i++) {
    $lineNum = $i + 1
    Write-Output ("${lineNum}: " + $lines[$i])
}
