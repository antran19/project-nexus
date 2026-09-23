#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://localhost:8080"

echo "Registering user..."
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/users/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@example.com","password":"longenough","fullName":"Smoke Test"}')
echo "$REGISTER_RESPONSE"

echo "Logging in..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke@example.com","password":"longenough"}')
echo "$LOGIN_RESPONSE"

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo "Changing password using the token returned by login..."
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X PUT "$BASE_URL/api/v1/users/me/password" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"oldPassword":"longenough","newPassword":"evenlongerpassword"}'

echo "Smoke test completed: register -> login -> change-password all succeeded through api-gateway."
