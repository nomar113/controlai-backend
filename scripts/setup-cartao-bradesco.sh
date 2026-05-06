#!/bin/bash
# Setup do cartão Bradesco Visa Infinite via API
# Pré-requisito: API rodando em localhost:8080 e holder já cadastrado (holderId: 169)

BASE_URL="http://localhost:8080"

echo "=== Criando cartão Bradesco Visa Infinite ==="
BRADESCO_RESPONSE=$(curl -s -X POST "$BASE_URL/payment-methods" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Bradesco Visa Infinite",
    "type": "CREDIT_CARD",
    "holderId": 169,
    "closingDay": 26
  }')

echo "$BRADESCO_RESPONSE" | python3 -m json.tool

BRADESCO_ID=$(echo "$BRADESCO_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
echo "Bradesco ID: $BRADESCO_ID"

if [ -z "$BRADESCO_ID" ]; then
  echo "Erro ao criar cartão Bradesco. Usando ID padrão 137."
  BRADESCO_ID=137
fi

echo ""
echo "=== Criando sub-cartões do Bradesco ==="

echo "--- Titular Ramon (6668) ---"
curl -s -X POST "$BASE_URL/payment-methods/$BRADESCO_ID/sub-cards" \
  -H 'Content-Type: application/json' \
  -d '{
    "lastFourDigits": "6668",
    "type": "PHYSICAL_HOLDER",
    "nickname": "Titular Ramon"
  }' | python3 -m json.tool

echo ""
echo "--- Adicional (9687) ---"
curl -s -X POST "$BASE_URL/payment-methods/$BRADESCO_ID/sub-cards" \
  -H 'Content-Type: application/json' \
  -d '{
    "lastFourDigits": "9687",
    "type": "PHYSICAL_DEPENDENT",
    "nickname": "Adicional"
  }' | python3 -m json.tool

echo ""
echo "=== Setup Bradesco concluído ==="
