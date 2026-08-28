# Fixtures de teste

As fixtures `rj-*.html` deste diretorio sao HTML **sinteticos**, escritos a mao para exercitar os seletores usados por `src/extractor/*`. Nao sao capturas reais da SEFAZ-RJ — nenhuma esteve disponivel no momento em que a Tarefa 1.0 foi implementada (sem acesso de rede ao ambiente da SEFAZ nem a uma chave de acesso valida para gerar um scan real).

Antes do rollout (Tarefa 8.0, smoke test manual), recomenda-se capturar ao menos uma pagina real de sucesso e uma de bloqueio da consulta SEFAZ-RJ (via "Salvar como" no navegador ou `view-source`), anonimizar CNPJ/nome do estabelecimento/endereco do consumidor, e complementar estas fixtures com essas capturas — validando que os seletores sobrevivem ao "ruido" estrutural de uma pagina real (wrappers extras, elementos duplicados com a mesma classe, formatacao de espacos variavel).
