#!/bin/bash
# Setup do planejamento/orçamento mensal via API
# Pré-requisito: API rodando em localhost:8080 e categorias já cadastradas
#
# Categorias utilizadas:
#   253 = Comida
#   254 = Gastos Gerais
#   255 = Telefonia
#   256 = Pets
#   257 = Moradia: condomínio + água
#   258 = Moradia: gás
#   259 = Remédio
#   260 = Transporte
#   261 = Viagens
#   262 = Assinaturas
#   263 = Médico: psicóloga
#   264 = Carro: combustível
#   265 = Moradia: luz
#   266 = Carro: seguro
#   269 = Academia

BASE_URL="http://localhost:8080"
YEAR_MONTH="${1:-2026-05}"

echo "=== Criando orçamento para $YEAR_MONTH ==="
BUDGET_RESPONSE=$(curl -s -X POST "$BASE_URL/budgets" \
  -H 'Content-Type: application/json' \
  -d "{\"yearMonth\": \"$YEAR_MONTH\"}")

echo "$BUDGET_RESPONSE" | python3 -m json.tool

BUDGET_ID=$(echo "$BUDGET_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
echo "Budget ID: $BUDGET_ID"

if [ -z "$BUDGET_ID" ]; then
  echo "Erro ao criar orçamento. Verifique se já existe um para $YEAR_MONTH (409 Conflict)."
  exit 1
fi

echo ""
echo "=== Adicionando itens do orçamento ==="

add_item() {
  local category_id=$1
  local expected=$2
  local label=$3
  echo "--- $label (categoryId: $category_id, expected: $expected) ---"
  curl -s -X POST "$BASE_URL/budgets/$BUDGET_ID/items" \
    -H 'Content-Type: application/json' \
    -d "{\"categoryId\": $category_id, \"type\": \"EXPENSE\", \"expected\": $expected}" | python3 -m json.tool
  echo ""
}

add_item 253 1000.00 "Comida"
add_item 254 3000.00 "Gastos Gerais"
add_item 264 500.00  "Carro: combustível"
add_item 266 389.37  "Carro: seguro"
add_item 256 523.08  "Pets (PetLove + Banho)"
add_item 265 200.00  "Moradia: luz"
add_item 269 129.43  "Academia (Bodytech)"
add_item 259 0.00    "Remédio (Venvanse + Daforin)"
add_item 262 330.00  "Assinaturas"
add_item 255 240.85  "Telefonia"
add_item 257 750.00  "Moradia: condomínio + água"
add_item 258 80.00   "Moradia: gás"
add_item 263 600.00  "Médico: psicóloga"
add_item 261 2000.00 "Viagens"
add_item 260 100.00  "Transporte"

echo "=== Planejamento para $YEAR_MONTH concluído ==="
echo "Total esperado: R$ 9.842,73"
