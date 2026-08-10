param(
  [ValidateSet("local", "github-actions", "other-ci")]
  [string]$Producer = "local",
  [string]$CiRunUrl = "",
  [switch]$KeepServices
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$resultPath = Join-Path $root "benchmarks/results/outbox-benchmark-v2.json"
$previousLocation = Get-Location
$previousEnvironment = @{}
$environmentNames = @(
  "SOURCE_COMMIT",
  "SOURCE_TREE_CLEAN",
  "IMAGE_DIGEST",
  "BENCHMARK_PRODUCER",
  "CI_RUN_URL"
)

foreach ($name in $environmentNames) {
  $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

Set-Location -LiteralPath $root
try {
  $status = @(git status --porcelain)
  if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect Git worktree"
  }
  if ($status.Count -ne 0) {
    throw "Benchmark requires a clean Git worktree so V2 provenance remains truthful"
  }

  $env:SOURCE_COMMIT = (git rev-parse HEAD).Trim()
  $env:SOURCE_TREE_CLEAN = "true"
  $env:BENCHMARK_PRODUCER = $Producer
  $env:CI_RUN_URL = $CiRunUrl

  if (Test-Path -LiteralPath $resultPath) {
    Remove-Item -LiteralPath $resultPath -Force
  }

  docker compose down --volumes --remove-orphans 2>$null | Out-Null
  docker compose build benchmark
  if ($LASTEXITCODE -ne 0) { throw "Docker image build failed" }

  $env:IMAGE_DIGEST = (docker image inspect --format "{{.Id}}" outbox-pattern:local).Trim()
  if ($env:IMAGE_DIGEST -notmatch '^sha256:[0-9a-f]{64}$') {
    throw "Could not obtain the built image digest"
  }

  docker compose up --detach --wait postgres redpanda
  if ($LASTEXITCODE -ne 0) { throw "PostgreSQL or Redpanda did not become healthy" }

  docker compose run --rm benchmark
  if ($LASTEXITCODE -ne 0) { throw "Benchmark container failed" }
  if (-not (Test-Path -LiteralPath $resultPath -PathType Leaf)) {
    throw "Benchmark did not regenerate $resultPath on the host"
  }

  $result = Get-Content -Raw -LiteralPath $resultPath | ConvertFrom-Json
  if ($result.schema_version -ne 2) { throw "Benchmark result is not V2" }
  $lost = $result.metrics | Where-Object { $_.name -eq "lost_messages" }
  if ($null -eq $lost -or [double]$lost.value -ne 0) {
    throw "Benchmark acceptance failed: lost_messages must equal zero"
  }

  Write-Host "Benchmark regenerated: $resultPath"
  $result.metrics | ForEach-Object {
    Write-Host ("  {0}: {1} {2}" -f $_.name, $_.value, $_.unit)
  }
} finally {
  if (-not $KeepServices) {
    $cleanupErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    docker compose down --volumes --remove-orphans *> $null
    $ErrorActionPreference = $cleanupErrorAction
  }
  foreach ($name in $environmentNames) {
    [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
  }
  Set-Location -LiteralPath $previousLocation
}
