# nfce-extraction-service

Microservico interno de extracao server-side de dados de NFC-e (SEFAZ-RJ), via Playwright + stealth. Chamado internamente pelo backend Kotlin (`POST /purchases/invoice/extraction`) — ver PRD/Tech Spec de `extracao-nfce-servidor`.

## Endpoints

- `POST /extract` — interno, protegido por header `X-Internal-Key`. Recebe `{ invoiceUrl: string }`, retorna `{ status, data?, message? }` (`status` em `READY | BLOCKED | TIMEOUT | NAVIGATION_ERROR`).
- `GET /health` — publico (sem autenticacao), usado pelo healthcheck do Docker.

## Variaveis de ambiente

| Variavel | Obrigatoria | Padrao | Descricao |
|---|---|---|---|
| `X_INTERNAL_KEY` | Sim | — | Segredo compartilhado exigido no header `X-Internal-Key` de `POST /extract`. Sem essa variavel configurada, **todas** as requisicoes a `/extract` sao rejeitadas com `401` (fail-closed). |
| `PORT` | Nao | `3000` | Porta em que o servidor escuta. |
| `HOST` | Nao | `0.0.0.0` | Interface de bind. Ver "Orientacao de rede" abaixo. |
| `EXTRACTION_TIMEOUT_MS` | Nao | `60000` | Tempo maximo de espera pela nota antes de retornar `status: "TIMEOUT"`. |
| `LOG_LEVEL` | Nao | `info` | Nivel de log do `pino` (`debug`, `info`, `warn`, `error`, ...). |

## Requisitos de infraestrutura

- **RAM**: ~1-2GB, para manter um processo Chromium de longa duracao (o servico reutiliza um unico browser entre requisicoes, abrindo um `BrowserContext` isolado por extracao). Some esse valor ao consumo ja existente do backend Kotlin na mesma VPS.
- **Rede**: `POST /extract` **nunca** deve ser exposto publicamente. O servico deve escutar apenas na rede interna da VPS (Docker network interna/porta nao publicada no host), acessivel somente pelo backend Kotlin rodando na mesma maquina. O header `X-Internal-Key` e defesa em profundidade, nao a barreira primaria — o isolamento de rede e o controle real.
- **Concorrencia**: dimensionado para poucas extracoes simultaneas (2-3), coerente com o volume esperado e para minimizar o risco de padrao de trafego suspeito no IP da VPS perante o anti-bot da SEFAZ.

## Logging

Log estruturado via `pino`. Cada extracao emite **uma unica linha** de log com o status final (`READY/BLOCKED/TIMEOUT/NAVIGATION_ERROR`) e a duracao em ms — nunca o HTML da pagina nem dados pessoais da nota (CPF/CNPJ do consumidor, endereco). Exemplo:

```json
{"level":30,"time":1787875054204,"status":"READY","durationMs":1415,"msg":"nfce_extraction_completed"}
```

## Rodando localmente

```bash
npm install
npx playwright install --with-deps chromium
npm run typecheck
npm test
```

## Build e execucao (producao)

```bash
npm run build   # compila TypeScript para dist/
npm start       # node dist/index.js
```

## Docker

```bash
docker build -t nfce-extraction-service .
docker run --rm -p 3000:3000 \
  -e X_INTERNAL_KEY=troque-por-um-segredo-forte \
  nfce-extraction-service

curl http://localhost:3000/health
# {"status":"ok"}
```

Na VPS, a porta do container **nao** deve ser publicada para a rede externa (`-p 3000:3000` acima e apenas para validacao local) — apenas acessivel pela rede interna do Docker/VPS ao backend Kotlin.
