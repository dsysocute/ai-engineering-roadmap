$ErrorActionPreference = 'Stop'
$BASE = 'http://127.0.0.1:18118'
function Req([string]$method, [string]$path, [string]$body, [string]$token) {
    $h = @{}
    if ($token) { $h['X-Token'] = $token }
    $p = @{ Uri = "$BASE$path"; Method = $method; UseBasicParsing = $true; TimeoutSec = 60; Headers = $h }
    if ($method -eq 'POST') {
        if ($null -eq $body) { $body = '' }
        $p['Body'] = $body
        $p['ContentType'] = 'application/x-www-form-urlencoded'
    }
    try { $r = Invoke-WebRequest @p; return [pscustomobject]@{ Status = [int]$r.StatusCode; Body = $r.Content } }
    catch {
        $resp = $_.Exception.Response
        if ($resp -and $resp.GetResponseStream) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $sr.ReadToEnd() }
        }
        return [pscustomobject]@{ Status = -1; Body = $_.Exception.Message }
    }
}
function Json([string]$s) { try { return $s | ConvertFrom-Json } catch { return $null } }
function Check([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) { Write-Output "PASS  $name" } else { Write-Output "FAIL  $name  >> $detail" }
}

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

$super = (Json (Req 'POST' '/api/super/login' 'username=supervisor&password=testsuper' $null).Body).token
Req 'POST' '/api/admin/user-op' 'username=202530126065&action=grantJudge' $super | Out-Null
$jt = (Json (Req 'POST' '/api/judge/login' 'password=testjudge' $null).Body).token
$p2 = (Json (Req 'POST' '/api/login' 'username=202530126002&password=123456&role=player' $null).Body).token
$p3 = (Json (Req 'POST' '/api/login' 'username=202530126003&password=123456&role=player' $null).Body).token

Req 'POST' '/api/judge/start-contest' 'minutes=2' $jt | Out-Null
Start-Sleep -Seconds 11
Req 'POST' '/api/start' '' $p2 | Out-Null
Req 'POST' '/api/start' '' $p3 | Out-Null
Req 'POST' '/api/editor' ('code=' + [uri]::EscapeDataString($good)) $p2 | Out-Null
"contest: " + (Json (Req 'GET' '/api/contest' $null $null).Body).status

Write-Output '===== 判罚 -> 申诉 -> 复赛（整场还在进行）====='
$r = Req 'POST' '/api/judge/violate' 'username=202530126002&note=test-penalty' $jt
Check 'judge penalize' ($r.Status -eq 200) "$($r.Status)"
$me = Json (Req 'GET' '/api/me' $null $p2).Body
Check 'penalized is finished' ($me.status -eq 'FINISHED' -and [double]$me.result.score -eq 0) "$($me.status) $($me.result.score)"

$r = Req 'POST' '/api/case/appeal' 'type=violation&reason=reinst-test' $p2
Check 'appeal submitted' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
$st = Json (Req 'GET' '/api/case/list?status=pending&kind=all' $null $jt).Body
$case = $st.items | Where-Object { $_.kind -eq 'appeal' -and $_.target -eq '202530126002' } | Select-Object -First 1
Check 'judge sees appeal' ($null -ne $case) "$($st.items.Count) items"
$r = Req 'POST' '/api/case/op' ('id=' + $case.id + '&action=approve&note=ok') $jt
Check 'appeal approved' ($r.Status -eq 200) "$($r.Status) $($r.Body)"

$me = Json (Req 'GET' '/api/me' $null $p2).Body
Check 'back to coding' ($me.status -eq 'CODING' -and $me.reinstated -eq $true) "$($me.status) reinstated=$($me.reinstated)"
Check 'personal countdown' ([long]$me.personalRemainMillis -gt 0) "remain=$($me.personalRemainMillis)"
Check 'violation record cleared' ($null -eq $me.result) "$($me.result)"

$r = Req 'POST' '/api/finish' ('code=' + [uri]::EscapeDataString($good)) $p2
$fr = Json $r.Body
Check 'reinstated can submit' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
Check 'reinstated score counted' ([double]$fr.score -gt 0 -and $fr.correct -eq $true) "score=$($fr.score) correct=$($fr.correct)"

Write-Output '===== 判罚 -> 整场强制结束 -> 申诉 -> 复赛交卷 ====='
$r = Req 'POST' '/api/judge/violate' 'username=202530126003&note=test-penalty-2' $jt
Check 'p3 penalized' ($r.Status -eq 200) "$($r.Status)"
Req 'POST' '/api/judge/force-end' '' $jt | Out-Null
"contest after force-end: " + (Json (Req 'GET' '/api/contest' $null $null).Body).status
$r = Req 'POST' '/api/case/appeal' 'type=violation&reason=reinst-after-end' $p3
Check 'p3 appeal submitted' ($r.Status -eq 200) "$($r.Status)"
$st = Json (Req 'GET' '/api/case/list?status=pending&kind=all' $null $jt).Body
$case3 = $st.items | Where-Object { $_.kind -eq 'appeal' -and $_.target -eq '202530126003' } | Select-Object -First 1
$r = Req 'POST' '/api/case/op' ('id=' + $case3.id + '&action=approve&note=ok') $jt
Check 'p3 appeal approved' ($r.Status -eq 200) "$($r.Status)"
$me3 = Json (Req 'GET' '/api/me' $null $p3).Body
Check 'p3 back to coding after contest end' ($me3.status -eq 'CODING' -and $me3.reinstated -eq $true) "$($me3.status)"
$r = Req 'POST' '/api/finish' ('code=' + [uri]::EscapeDataString($good)) $p3
$fr3 = Json $r.Body
Check 'p3 can submit after contest end' ($r.Status -eq 200) "$($r.Status) $($r.Body)"
Check 'p3 score counted' ([double]$fr3.score -gt 0) "score=$($fr3.score)"
