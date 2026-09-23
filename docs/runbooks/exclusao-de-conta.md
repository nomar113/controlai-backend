# Runbook — Exclusão de conta, backups e registros de acesso

Operação da exclusão de conta (PRD `prd-pos-rejeicao-app-store`, Funcionalidade 1) no servidor de
produção. Complementa o código em `application/account_deletion` e `domain/account_deletion`.

## 1. Job de purga

- `AccountPurgeScheduler` roda todo dia às **04:00 de Brasília** e apaga fisicamente as contas cuja
  exclusão venceu (`users.deletion_scheduled_for <= agora`), uma transação por conta.
  - **Único membro do grupo:** apaga o grupo inteiro (cartões, titulares, compras, NFC-e, parcelas,
    orçamentos, categorias, chaves de API, convites, assinatura), o usuário e o grupo.
  - **Grupo com parceiro(a):** apaga só o usuário, o vínculo `group_members`, tokens e convites dele.
    O grupo, os titulares e os dados financeiros continuam com quem fica.
  - Em `kiwify_webhook_events`, o `raw_payload` com o e-mail do usuário vira `{"redacted": true}`. O
    `kiwify_event_id` fica, porque é a chave de idempotência.
- Configuração (variáveis de ambiente do `controlai.env`, opcionais):
  - `ACCOUNT_DELETION_PURGE_CRON`: cron de 6 campos do Spring (padrão `0 0 4 * * *`); `-` desliga.
  - `ACCOUNT_DELETION_PURGE_ENABLED`: `false` desliga o job (padrão `true`).
- Observabilidade:
  - métricas `account.deletion.purged{outcome=sole_member|member_removed}` e
    `account.deletion.purge.failure`;
  - log INFO `Account purged for user <id> (<outcome>)` por conta e `Account purge finished: ...`
    com o resumo;
  - log ERROR `Account purge failed for user <id>` por conta com falha. A conta continua vencida e
    é tentada de novo no dia seguinte; investigar a causa (normalmente uma FK nova fora da purga).
- Consultar as contas vencidas e as que vão vencer:

  ```sql
  SELECT id, deletion_requested_at, deletion_scheduled_for
  FROM users WHERE deletion_scheduled_for IS NOT NULL ORDER BY deletion_scheduled_for;
  ```

## 2. Backups

- O backup diário do banco (`/root/bin/controlai-db-backup.sh`, cron às 03:00, arquivos em
  `/root/backups`) deve guardar **no máximo 30 dias** (as notas do servidor indicam 14 dias; registre
  aqui o valor conferido e use o mesmo na política de privacidade). Os dados de uma conta purgada somem de vez
  quando o último backup anterior à purga expira. Esse prazo deve constar na política de privacidade.
- Conferir a retenção configurada no script (o `find ... -mtime +N -delete`, ou equivalente) e a
  quantidade de arquivos em `/root/backups`.

### Regra: nunca restaurar contas excluídas

**Pré-requisito:** os logs da API (saída do `script.sh`) devem ficar gravados em arquivo, fora do
banco, por **pelo menos o mesmo prazo dos backups**, e sobreviver a deploys. Sem eles não há como
saber quais contas de um backup já foram purgadas; o banco não serve, porque o restore o sobrescreve.

Um backup pode conter contas que já foram purgadas. Depois de **qualquer** restore (total ou parcial):

1. Antes de liberar o tráfego, liste as contas purgadas depois da data do backup, pelos logs da API:
   `grep "Account purged for user" <logs da API desde a data do backup>`.
2. Para cada `userId` listado que voltou com o restore, rode a purga de novo antes de abrir o acesso:
   marque a conta como vencida e deixe o job rodar (ou suba a API com
   `ACCOUNT_DELETION_PURGE_CRON` apontando para daqui a poucos minutos):

   ```sql
   UPDATE users SET deletion_scheduled_for = NOW() - INTERVAL 1 MINUTE
   WHERE id IN (<ids purgados depois do backup>);
   ```
3. Contas que já estavam com a exclusão vencida no backup são purgadas de novo pelo próprio job,
   porque o estado fica em `users.deletion_scheduled_for`.
4. Confirme pelo log (`Account purged for user ...`) e por `SELECT id FROM users WHERE id IN (...)`.

Nunca restaure seletivamente dados de um usuário ou grupo excluído, nem para "ajudar" quem pediu a
exclusão: depois dos 30 dias não há recuperação (PRD, Fora de Escopo).

## 3. Registros de acesso (Marco Civil, art. 15)

- Os registros de acesso à aplicação (IP, data e hora) são os logs de acesso do nginx em
  `/var/log/nginx/access.log*` (proxy de `api.opencod3.com.br`).
- O Marco Civil exige guardá-los por **6 meses**. Eles ficam **fora da purga** (a API não os toca) e
  devem ser apagados depois desse prazo.
- Configuração esperada em `/etc/logrotate.d/nginx` (o padrão do Ubuntu guarda só 14 dias):

  ```
  /var/log/nginx/*.log {
      daily
      rotate 183
      maxage 183
      missingok
      compress
      delaycompress
      notifempty
      create 0640 www-data adm
      sharedscripts
      prerotate
          if [ -d /etc/logrotate.d/httpd-prerotate ]; then \
              run-parts /etc/logrotate.d/httpd-prerotate; \
          fi \
      endscript
      postrotate
          invoke-rc.d nginx rotate >/dev/null 2>&1
      endscript
  }
  ```

  Validar com `logrotate -d /etc/logrotate.d/nginx`.
- Os logs da própria API (stdout do `script.sh`) não devem guardar e-mail nem nome: o código só
  registra `userId` ou e-mail mascarado.

## 4. Conta do revisor das lojas

A conta demo (`revisor.loja@nomar.com.br`) é criada pela V40 e enriquecida pela V42. Ela pode pedir
a exclusão como qualquer conta (RF1.14).

- **Exclusão agendada durante a revisão:** a conta fica bloqueada (423) até alguém cancelar. Cancele
  entrando com ela no app ("Cancelar exclusão") ou por SQL:

  ```sql
  UPDATE users SET deletion_requested_at = NULL, deletion_scheduled_for = NULL
  WHERE email = 'revisor.loja@nomar.com.br';
  ```
- **Conta purgada:** as migrações V40 e V42 já rodaram e o Flyway não as repete. Para recriar:
  1. Confirme que a conta foi mesmo purgada (`SELECT id FROM users WHERE email =
     'revisor.loja@nomar.com.br'` sem linhas).
  2. Crie uma migração nova `V<próximo>__recreate_store_reviewer_account.sql` com o conteúdo da V40
     seguido do da V42. A V42 já se protege (variáveis nulas e `NOT EXISTS`), mas a **V40 não**:
     guarde cada `INSERT` dela com `... SELECT ... FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users
     WHERE email = 'revisor.loja@nomar.com.br')` (e o do grupo pelo nome `ControlAI Revisor`), para
     que a migração nunca falhe e bloqueie a subida da API se a conta existir. Troque o hash da
     senha se a senha antiga tiver sido compartilhada fora da equipe.
  3. Rode a suíte (`./gradlew test`): o `TestDatabaseCleaner` apaga os dados seedados, e os testes
     de migração conferem o schema.
  4. Publique a API; o Flyway aplica a migração na subida.
  5. Entre com a conta e confira NFC-e com itens, dois titulares, compra parcelada e orçamento.
