$ErrorActionPreference = 'Stop'
$BASE = 'http://127.0.0.1:18119'
$script:pass = 0
$script:fail = 0

function Req([string]$method, [string]$path, [string]$body, [string]$token) {
    $h = @{}
    if ($token) { $h['X-Token'] = $token }
    $p = @{ Uri = "$BASE$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 180; Headers = $h }
    if ($method -eq 'POST') {
        if ($null -eq $body) { $body = '' }
        $p['Body'] = $body
        $p['ContentType'] = 'application/x-www-form-urlencoded'
    }
    try {
        $r = Invoke-WebRequest @p
        return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content }
    } catch {
        $resp = $_.Exception.Response
        if ($resp -and $resp.GetResponseStream) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $sr.ReadToEnd() }
        }
        return [pscustomobject]@{ Status = -1; Body = $_.Exception.Message }
    }
}

function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { $script:pass++; Write-Output ("PASS  " + $name) }
    else { $script:fail++; Write-Output ("FAIL  " + $name + "  >> " + $detail) }
}

function Json([string]$s) { try { return $s | ConvertFrom-Json } catch { return $null } }

$good = @'
public class Solution implements SortStrategy {
    public void sort(int[] arr) { qs(arr, 0, arr.length - 1); }
    private void qs(int[] a, int l, int r) {
        while (l < r) {
            int i = l, j = r, p = a[(l + r) >>> 1];
            while (i <= j) {
                while (a[i] < p) i++;
                while (a[j] > p) j--;
                if (i <= j) { int t = a[i]; a[i] = a[j]; a[j] = t; i++; j--; }
            }
            if (j - l < r - i) { qs(a, l, j); l = i; } else { qs(a, i, r); r = j; }
        }
    }
}
'@
$bad = 'public class Solution implements SortStrategy { public void sort(int[] a) { } }'

Write-Output '===== 闂堟瑦鈧線銆夐棃?====='
foreach ($p in @('/', '/watch', '/case', '/admin', '/judge', '/super', '/session.js', '/app.js', '/avatar.js')) {
    $r = Req 'GET' $p $null $null
    Check "page $p" ($r.Status -eq 200) "$($r.Status)"
}
$trav = Req 'GET' '/../data/state.tsv' $null $null
Check 'path traversal blocked' ($trav.Status -eq 404) "$($trav.Status)"

Write-Output '===== 閻ц缍?====='
$r = Req 'POST' '/api/login' 'username=202530126001&password=123456&role=viewer' $null
$v1 = Json $r.Body
Check 'viewer login' ($r.Status -eq 200 -and $v1.role -eq 'viewer') "$($r.Status) $($r.Body)"
$vt = $v1.token
$r = Req 'POST' '/api/login' 'username=202530126002&password=123456&role=player' $null
$p2 = Json $r.Body
Check 'player2 login' ($r.Status -eq 200 -and $p2.role -eq 'player') "$($r.Status)"
$r = Req 'POST' '/api/login' 'username=202530126003&password=123456&role=player' $null
$p3 = Json $r.Body
Check 'player3 login' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'GET' '/api/me' $null $p2.token
Check 'me' ($r.Status -eq 200 -and (Json $r.Body).status -eq 'NOT_STARTED') "$($r.Status) $($r.Body)"
$r = Req 'GET' '/api/problem' $null $p2.token
Check 'problem' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'GET' '/api/contest' $null $null
Check 'contest idle' ($r.Status -eq 200 -and (Json $r.Body).status -eq 'idle') "$($r.Body)"

$qt0 = [string]([char]0x8D5B) + [char]0x51FA + [char]0x98CE + [char]0x683C
$r = Req 'POST' '/api/quick' ('text=' + [uri]::EscapeDataString($qt0)) $p2.token
Check 'quick before contest' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$r = Req 'POST' '/api/quick' ('text=' + [uri]::EscapeDataString($qt0)) $vt
Check 'viewer cannot send quick' ($r.Status -eq 403) "$($r.Status)"

Write-Output '===== 瀵偓鐠?====='
$r = Req 'POST' '/api/super/login' 'username=supervisor&password=testsuper' $null
$st = (Json $r.Body).token
Check 'super login' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/admin/user-op' 'username=202530126065&action=grantJudge' $st
Check 'grant judge' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$r = Req 'POST' '/api/judge/login' 'password=testjudge' $null
$jt = (Json $r.Body).token
Check 'judge login' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/judge/start-contest' '' $jt
Check 'start contest' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
Start-Sleep -Seconds 11
$r = Req 'GET' '/api/contest' $null $null
Check 'contest running' ((Json $r.Body).status -eq 'running') "$($r.Body)"

Write-Output '===== 娴ｆ粎鐡熼柧鎹愮熅 ====='
$r = Req 'POST' '/api/start' '' $p2.token
Check 'player2 start' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/test' ('code=' + [uri]::EscapeDataString($good)) $p2.token
Check 'test run compiles' ($r.Status -eq 200 -and $r.Body -match '"terminal"') "$($r.Status)"
$r = Req 'GET' '/api/watch/code?no=3' $null $p2.token
Check 'watch code blocked for unfinished player' ($r.Status -eq 403) "$($r.Status)"

Write-Output '===== 瀵懓绠烽弬鎷岊潐閸?====='
$r = Req 'POST' '/api/watch/danmaku' 'text=hello-viewer' $vt
Check 'viewer danmaku allowed while running' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$r = Req 'POST' '/api/watch/danmaku' 'text=spam-again' $vt
Check 'danmaku cooldown' ($r.Status -eq 429) "$($r.Status)"
Start-Sleep -Milliseconds 1600
$r = Req 'POST' '/api/watch/danmaku' 'text=hello-again' $vt
Check 'danmaku after cooldown' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/comments' 'text=player-dm&kind=danmaku' $p2.token
Check 'unfinished player danmaku blocked' ($r.Status -eq 403) "$($r.Status)"
$r = Req 'GET' '/api/comments?limit=20&kind=danmaku' $null $p2.token
Check 'player danmaku locked while running' ((Json $r.Body).locked -eq $true) "$($r.Body.Substring(0,[Math]::Min(80,$r.Body.Length)))"
$hint = [string]([char]0x5FEB) + [char]0x6392
$r = Req 'POST' '/api/watch/danmaku' ('text=' + [uri]::EscapeDataString($hint)) $vt
Check 'hint word blocked during contest' ($r.Status -eq 400) "$($r.Status)"
Start-Sleep -Milliseconds 600
# comment CD is 0.5s: use raw sockets so two POSTs go out back-to-back
function RawPost([string]$path, [string]$body, [string]$tk) {
    $u = [uri]$BASE
    $client = New-Object System.Net.Sockets.TcpClient($u.Host, $u.Port)
    $stream = $client.GetStream()
    $bytes = [Text.Encoding]::UTF8.GetBytes($body)
    $head = "POST $path HTTP/1.1`r`nHost: $($u.Host):$($u.Port)`r`nX-Token: $tk`r`n" +
        "Content-Type: application/x-www-form-urlencoded`r`nContent-Length: $($bytes.Length)`r`nConnection: close`r`n`r`n"
    $hb = [Text.Encoding]::ASCII.GetBytes($head)
    $stream.Write($hb, 0, $hb.Length)
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
    $ms = New-Object System.IO.MemoryStream
    $buf = New-Object byte[] 4096
    while (($n = $stream.Read($buf, 0, $buf.Length)) -gt 0) { $ms.Write($buf, 0, $n) }
    $client.Close()
    $text = [Text.Encoding]::UTF8.GetString($ms.ToArray())
    if ($text -match '^HTTP/1\.1 (\d{3})') { return [int]$matches[1] }
    return -1
}
$c1 = RawPost '/api/comments' 'text=cd-one&kind=comment' $vt
$c2 = RawPost '/api/comments' 'text=cd-two&kind=comment' $vt
Check 'comment cd first ok' ($c1 -eq 200) "$c1"
Check 'comment cooldown 0.5s' ($c2 -eq 429) "$c2"
Start-Sleep -Milliseconds 700
Start-Sleep -Milliseconds 1200
$r = Req 'POST' '/api/editor' ('code=' + [uri]::EscapeDataString($good)) $p2.token
Check 'editor push' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/finish' ('code=' + [uri]::EscapeDataString($bad)) $p2.token
$fr = Json $r.Body
Check 'finish wrong -> 0' ($r.Status -eq 200 -and $fr.correct -eq $false -and [double]$fr.score -eq 0) "$($r.Status) $($r.Body)"

$r = Req 'POST' '/api/start' '' $p3.token
Check 'player3 start' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/finish' ('code=' + [uri]::EscapeDataString($good)) $p3.token
$fr3 = Json $r.Body
Check 'finish correct' ($r.Status -eq 200 -and $fr3.correct -eq $true) "$($r.Status) $($r.Body)"
$r = Req 'POST' '/api/judge-efficiency' ('username=202530126003&code=' + [uri]::EscapeDataString($bad)) $p3.token
Check 'efficiency ignores client code' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$ready = $false; $score3 = 0
for ($i = 0; $i -lt 40; $i++) {
    Start-Sleep -Seconds 2
    $me = Json (Req 'GET' '/api/me' $null $p3.token).Body
    if ($me.result -and $me.result.efficiencyReady) { $ready = $true; $score3 = [double]$me.result.score; break }
}
Check 'efficiency auto done' ($ready -and $score3 -ge 80) "ready=$ready score=$score3"
$r = Req 'GET' '/api/leaderboard' $null $null
Check 'leaderboard' ($r.Status -eq 200 -and $r.Body -match '202530126003') "$($r.Status)"

Write-Output '===== 鐟欏倽绂?/ 鐠併劏顔?====='
$r = Req 'GET' '/api/watch' $null $vt
Check 'watch list' ($r.Status -eq 200 -and $r.Body -match '202530126003') "$($r.Status)"
$r = Req 'GET' '/api/watch/code?no=3' $null $vt
Check 'watch code (viewer)' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'GET' '/api/watch/code?no=3' $null $p3.token
Check 'watch code allowed after finish' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/comments' 'text=hello&kind=comment' $p3.token
Check 'comment post' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$r = Req 'GET' '/api/comments?limit=20' $null $p3.token
Check 'comment list' ($r.Status -eq 200 -and $r.Body -match 'hello') "$($r.Status)"
$qt = [string]([char]0x8D5B) + [char]0x51FA + [char]0x98CE + [char]0x683C
$r = Req 'POST' '/api/quick' ('text=' + [uri]::EscapeDataString($qt)) $p3.token
Check 'quick locked after start' ($r.Status -eq 409) "$($r.Status)"
Start-Sleep -Milliseconds 1600
$r = Req 'POST' '/api/comments' 'text=finished-player-dm&kind=danmaku' $p3.token
Check 'finished player danmaku allowed' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$r = Req 'GET' '/api/comments?limit=20&kind=danmaku' $null $p3.token
Check 'player danmaku unlocked after finish' (((Json $r.Body).locked -eq $false) -and $r.Body -match 'finished-player-dm') "$($r.Body.Substring(0,[Math]::Min(80,$r.Body.Length)))"

Write-Output '===== 缁狅紕鎮婇崨?====='
$r = Req 'POST' '/api/admin/login' 'username=&password=testadmin' $null
$at = (Json $r.Body).token
Check 'admin login' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'GET' '/api/admin/results' $null $at
Check 'admin results' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'GET' '/api/admin/users' $null $at
Check 'admin users' ($r.Status -eq 200 -and $r.Body -match '202530126002') "$($r.Status)"
$r = Req 'GET' '/api/comments?limit=20' $null $at
Check 'admin reads comments' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/admin/user-op' 'username=202530126001&action=mute&minutes=30' $at
Check 'admin mute' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/comments' 'text=test' $vt
Check 'muted viewer blocked' ($r.Status -eq 403) "$($r.Status)"
$r = Req 'POST' '/api/admin/user-op' 'username=202530126001&action=unmute' $at
Check 'admin unmute' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/admin/comment-post' 'text=announcement' $at
Check 'admin comment post' ($r.Status -eq 200) "$($r.Status)"

Write-Output '===== 閹崵顓?/ 缁狅紕鎮婇崨妯规姢閻?====='
$r = Req 'POST' '/api/admin/user-op' 'username=202530126004&action=grantAdmin' $st
Check 'grant admin' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/admin/login' 'username=202530126004&password=202530126004' $null
$a4 = (Json $r.Body).token
Check 'granted admin login' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'POST' '/api/admin/user-op' 'username=202530126004&action=revokeAdmin' $st
Check 'revoke admin' ($r.Status -eq 200) "$($r.Status)"
$r = Req 'GET' '/api/admin/results' $null $a4
Check 'revoked token rejected' ($r.Status -eq 401) "$($r.Status)"

Write-Output '===== 娑撶偓濮?/ 閻㈠疇鐦?====='
$r = Req 'GET' '/api/case/targets' $null $vt
$tg = Json $r.Body
Check 'case targets' ($r.Status -eq 200 -and $tg.items.Count -gt 50) "$($r.Status)"
$r = Req 'GET' '/api/case/targets' $null $at
Check 'case targets for staff' ($r.Status -eq 200) "$($r.Status)"
$reason = 'e2e-report-' + [DateTime]::Now.Ticks
$r = Req 'POST' '/api/case/report' ('username=202530126002&type=violation&reason=' + $reason) $vt
$caseId = (Json $r.Body).id
Check 'submit report' ($r.Status -eq 200 -and $caseId -gt 0) "$($r.Status) $($r.Body)"
$r = Req 'GET' '/api/case/list?status=pending&kind=all' $null $jt
Check 'judge sees violation case' ($r.Status -eq 200 -and $r.Body -match $reason) "$($r.Status)"
$r = Req 'GET' '/api/case/list?status=pending&kind=all' $null $at
Check 'admin cannot see violation case' ($r.Status -eq 200 -and $r.Body -notmatch $reason) "$($r.Status)"
$r = Req 'POST' '/api/case/op' ('id=' + $caseId + '&action=approve&note=e2e') $jt
Check 'judge approves violation' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$r = Req 'GET' '/api/me' $null $p2.token
Check 'violated player scored 0' ((Json $r.Body).result.correct -eq $false) "$($r.Body)"
$r = Req 'POST' '/api/case/appeal' 'type=violation&reason=appeal-e2e' $p2.token
Check 'submit appeal' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$r = Req 'GET' '/api/case/mine' $null $p2.token
Check 'my cases' ($r.Status -eq 200 -and $r.Body -match 'appeal-e2e') "$($r.Status)"

Write-Output '===== 閺€璺虹啲 ====='
$r = Req 'POST' '/api/judge/force-end' '' $jt
Check 'force end' ($r.Status -eq 200) "$($r.Status)"
Start-Sleep -Seconds 5
$r = Req 'GET' '/api/contest' $null $null
Check 'contest finished' ((Json $r.Body).status -eq 'finished') "$($r.Body)"
$r = Req 'POST' '/api/finish' ('code=' + [uri]::EscapeDataString($good)) $p2.token
Check 'late finish rejected' ($r.Status -eq 409) "$($r.Status)"

Write-Output ''
Write-Output ("RESULT pass=$script:pass fail=$script:fail")
if ($script:fail -gt 0) { exit 1 }



