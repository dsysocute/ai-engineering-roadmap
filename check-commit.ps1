param([switch]$NoPopup)

# 收工检查：查 git 未提交/未推送 + PROGRESS.md 当天记录，结果弹窗
$repo = "D:\ai-engineering-roadmap"
$progress = Join-Path $repo "daily progress\PROGRESS.md"
$today = (Get-Date).ToString("yyyy-MM-dd")

if (-not (Test-Path -LiteralPath $repo)) {
    $msg = "仓库路径不存在：$repo"
    if ($NoPopup) { Write-Output $msg } else { [System.Windows.Forms.MessageBox]::Show($msg, "收工检查") | Out-Null }
    exit 1
}

$issues = @()

# 1. 工作区改动
$status = (git -C $repo -c core.quotepath=false status --short) -join "`n"
if ($status.Trim()) {
    $count = ($status -split "`n").Count
    $issues += "有 $count 处改动没提交："
    $issues += $status
}

# 2. 是否已推送
$branch = (git -C $repo rev-parse --abbrev-ref HEAD).Trim()
$ahead = (git -C $repo rev-list --count "origin/$branch..HEAD" 2>$null)
if ($ahead -and [int]$ahead -gt 0) {
    $issues += "有 $ahead 条提交没推送（当前分支 $branch）"
}

# 3. PROGRESS 当天记录
if (Test-Path -LiteralPath $progress) {
    $tail = Get-Content -LiteralPath $progress -Encoding UTF8 -Tail 40
    $hasToday = $tail -match ("日期：" + [regex]::Escape($today))
    if (-not $hasToday) {
        $issues += "PROGRESS.md 没有 $today 的记录，收工三行还没写：做了什么 / 卡在哪 / 明天第一件事"
    }
} else {
    $issues += "找不到 daily progress\PROGRESS.md"
}

# 4. 关键文件别丢
$critical = @("CURRENT-STATUS.md", "ROADMAP.md")
foreach ($f in $critical) {
    if (-not (Test-Path -LiteralPath (Join-Path $repo $f))) { $issues += "关键文件不见了：$f" }
}

$lastCommit = (git -C $repo log --oneline -1 2>$null)

if ($issues.Count -eq 0) {
    $msg = "今天收工了，进度已提交并推送。`n`n最新提交：$lastCommit"
} else {
    $msg = "还没收工，缺这几样：`n`n" + ($issues -join "`n") + "`n`n提交命令（按里程碑命名）：`n  git add <文件>`n  git commit -m `"M1: 完成 xxx`"`n  git push`n`n不要提交：.venv、.env、临时文件；Word 文档不明体积变动先别勾。"
}

if ($NoPopup) {
    Write-Output $msg
} else {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show($msg, "收工检查 - $today", "OK", "Information") | Out-Null
}
