#!/bin/bash
# Setup do cartão Itau Visa Platinum LATAM Pass via API
# Pré-requisito: API rodando em localhost:8080 e holder já cadastrado (holderId: 169)

BASE_URL="http://localhost:8080"

echo "=== Criando cartão Itau Visa Platinum LATAM Pass ==="
ITAU_RESPONSE=$(curl -s -X POST "$BASE_URL/payment-methods" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Itau Visa Platinum LATAM Pass",
    "type": "CREDIT_CARD",
    "holderId": 169,
    "closingDay": 1
  }')

echo "$ITAU_RESPONSE" | python3 -m json.tool

ITAU_ID=$(echo "$ITAU_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
echo "Itau ID: $ITAU_ID"

if [ -z "$ITAU_ID" ]; then
  echo "Erro ao criar cartão Itau. Usando ID padrão 138."
  ITAU_ID=138
fi

echo ""
echo "=== Criando sub-cartões do Itau ==="

echo "--- Titular Ramon (2108) ---"
curl -s -X POST "$BASE_URL/payment-methods/$ITAU_ID/sub-cards" \
  -H 'Content-Type: application/json' \
  -d '{
    "lastFourDigits": "2108",
    "type": "PHYSICAL_HOLDER",
    "nickname": "Titular Ramon"
  }' | python3 -m json.tool

echo ""
echo "--- Cartao 2 Ramon (8415) ---"
curl -s -X POST "$BASE_URL/payment-methods/$ITAU_ID/sub-cards" \
  -H 'Content-Type: application/json' \
  -d '{
    "lastFourDigits": "8415",
    "type": "PHYSICAL_HOLDER",
    "nickname": "Cartao 2 Ramon"
  }' | python3 -m json.tool

echo ""
echo "--- Aline (2831) ---"
curl -s -X POST "$BASE_URL/payment-methods/$ITAU_ID/sub-cards" \
  -H 'Content-Type: application/json' \
  -d '{
    "lastFourDigits": "2831",
    "type": "PHYSICAL_DEPENDENT",
    "nickname": "Aline",
    "dependentName": "Aline C da S Diogo"
  }' | python3 -m json.tool

echo ""
echo "=== Setup Itau concluído ==="
