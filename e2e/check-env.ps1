# check-env.ps1
# Verifica se o ambiente de staging está acessível antes de rodar a suíte E2E.
# Uso: .\e2e\check-env.ps1

$BASE_URL = "https://easy-maintenance-web-production.up.railway.app"
$API_URL  = "https://easy-maintenance-api-production.up.railway.app/easy-maintenance/api/v1"

Write-Host "`n=== Easy Maintenance — Pre-flight Check ===" -ForegroundColor Cyan
Write-Host "Data: $(Get-Date -Format 'yyyy-MM-dd HH:mm')`n"

$results = @()

function Check($label, $url, $expectedStatus) {
    try {
        $response = Invoke-WebRequest -Uri $url -Method GET -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
        $ok = $response.StatusCode -eq $expectedStatus
        $results += [PSCustomObject]@{ Label = $label; Status = $response.StatusCode; OK = $ok }
        $icon = if ($ok) { "OK" } else { "WARN" }
        Write-Host "  [$icon] $label — HTTP $($response.StatusCode)"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if (-not $code) { $code = "TIMEOUT/ERR" }
        $results += [PSCustomObject]@{ Label = $label; Status = $code; OK = $false }
        Write-Host "  [FAIL] $label — $code"
    }
}

Write-Host "Frontend:" -ForegroundColor Yellow
Check "Login page"     "$BASE_URL/login" 200
Check "Items page"     "$BASE_URL/items" 200

Write-Host "`nBackend (health / public endpoints):" -ForegroundColor Yellow
Check "API health"     "$API_URL/health" 200

$failed = $results | Where-Object { -not $_.OK }
Write-Host ""
if ($failed.Count -eq 0) {
    Write-Host "Ambiente OK — pode rodar a suite." -ForegroundColor Green
} else {
    Write-Host "$($failed.Count) verificacao(oes) falharam. Corrija antes de rodar a suite." -ForegroundColor Red
    $failed | ForEach-Object { Write-Host "  - $($_.Label): $($_.Status)" -ForegroundColor Red }
}
Write-Host ""
