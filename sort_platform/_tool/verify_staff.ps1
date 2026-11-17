$ErrorActionPreference = 'Stop'
$BASE = 'http://127.0.0.1:18120'
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
function Check([string]$n, [bool]$ok, [string]$d) { if ($ok) { "PASS  $n" } else { "FAIL  $n  >> $d" } }

$super = (Json (Req 'POST' '/api/super/login' 'username=supervisor&password=testsuper' $null).Body).token

# 064 先以选手身份报名
$r = Req 'POST' '/api/login' 'username=202530126064&password=123456&role=player' $null
Check '064 registers as player' ((Json $r.Body).role -eq 'player') "$($r.Body)"
$list = Json (Req 'GET' '/api/contest' $null $null).Body
Check '064 in player list' ($list.playerNames -contains '202530126064') "$($list.playerNames -join ',')"

# 设为裁判 -> 应移出选手名单
Req 'POST' '/api/admin/user-op' 'username=202530126064&action=grantJudge' $super | Out-Null
$list = Json (Req 'GET' '/api/contest' $null $null).Body
Check '064 removed from player list after judge' (-not ($list.playerNames -contains '202530126064')) "$($list.playerNames -join ',')"
$jt = (Json (Req 'POST' '/api/judge/login' 'password=testjudge' $null).Body).token
$sm = Json (Req 'GET' '/api/staff/me' $null $jt).Body
Check 'judge avatar is 64' ($sm.avatar -eq '64' -and $sm.role -eq 'judge') "$($sm.role) $($sm.avatar)"

# 064 再以选手登录 -> 自动降为观众并带说明
$r = Req 'POST' '/api/login' 'username=202530126064&password=123456&role=player' $null
$d = Json $r.Body
Check 'judge student forced to viewer' ($d.role -eq 'viewer') "$($d.role)"
Check 'judge login has notice' ($d.notice -like '*裁判*') "$($d.notice)"
$list = Json (Req 'GET' '/api/contest' $null $null).Body
Check 'judge still not in player list' (-not ($list.playerNames -contains '202530126064')) "$($list.playerNames -join ',')"

# 065 先报名再设为管理员
$r = Req 'POST' '/api/login' 'username=202530126065&password=123456&role=player' $null
Check '065 registers as player' ((Json $r.Body).role -eq 'player') "$($r.Body)"
Req 'POST' '/api/admin/user-op' 'username=202530126065&action=grantAdmin' $super | Out-Null
$list = Json (Req 'GET' '/api/contest' $null $null).Body
Check '065 removed from player list after admin' (-not ($list.playerNames -contains '202530126065')) "$($list.playerNames -join ',')"
$at = (Json (Req 'POST' '/api/admin/login' 'username=202530126065&password=202530126065' $null).Body).token
$sm = Json (Req 'GET' '/api/staff/me' $null $at).Body
Check 'admin avatar is 65' ($sm.avatar -eq '65' -and $sm.role -eq 'admin') "$($sm.role) $($sm.avatar)"
$r = Req 'POST' '/api/login' 'username=202530126065&password=123456&role=player' $null
Check 'admin student forced to viewer' ((Json $r.Body).role -eq 'viewer') "$($r.Body)"

# 总管头像
$sm = Json (Req 'GET' '/api/staff/me' $null $super).Body
Check 'super avatar is 总管' ($sm.avatar -eq ([string]([char]0x603B) + [char]0x7BA1)) "$($sm.avatar)"

# 学生令牌不能调 staff/me
$st = (Json (Req 'POST' '/api/login' 'username=202530126001&password=123456&role=viewer' $null).Body).token
$r = Req 'GET' '/api/staff/me' $null $st
Check 'student token rejected' ($r.Status -eq 401) "$($r.Status)"
