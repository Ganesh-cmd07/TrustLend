$loginBody = @{
    email = "borrower1@trustlend.local"
    password = "password123"
} | ConvertTo-Json

$loginRes = Invoke-RestMethod -Uri "http://localhost:4040/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
$token = $loginRes.token

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type"  = "application/json"
}

$questions = @("hello", "thanks", "what can you do?", "what is trustlend?")

foreach ($q in $questions) {
    Write-Host "========================================"
    Write-Host "QUESTION: $q"
    Write-Host "----------------------------------------"
    $body = @{ prompt = $q } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "http://localhost:4040/api/v1/ai/chat" -Method Post -Headers $headers -Body $body
    Write-Host $res.reply
}
