# Run All — Suíte Completa de Regressão

Diga ao Claude: **"Execute `run-all.md`"** para rodar todos os cenários em sequência.

---

## Comportamento do runner

1. Limpa sessão (localStorage + sessionStorage)
2. Faz login uma vez — token reutilizado por todos os cenários
3. Executa os cenários na ordem abaixo
4. Para cada cenário, registra ✅ / ❌ por verificação
5. Ao final, imprime o relatório consolidado

**Em caso de falha:** Claude reporta qual verificação falhou e continua para o próximo cenário
(não aborta a suíte inteira).

---

## Ordem de execução

```
FASE 1 — Auth & Billing (sem dependências)
  01-login.md
  02-billing-plan-change.md

FASE 2 — Limites de plano (sem dependências)
  03-organizations-limit.md
  04-team-members-limit.md

FASE 3 — Itens (05 → 06 em sequência; 07 independente)
  05-items-create.md       ← cria TESTE_E2E_ITEM
  06-items-edit.md         ← depende de 05
  07-items-blocked-by-maintenance.md

FASE 4 — Manutenção
  08-maintenance-create.md
```

---

## Instruções para o Claude executar

### PRÉ-VÔLO

```
1. Limpar sessão:
   - Executar JS: localStorage.clear(); sessionStorage.clear();

2. Executar cenário 01-login.md completo
   - Se falhar → PARAR e reportar: "Login falhou — demais cenários abortados"
   - Se passar → continuar com sessão ativa
```

### EXECUÇÃO DOS CENÁRIOS

Para cada cenário (02 a 08):
```
1. Ler o arquivo do cenário
2. Executar cada passo no browser
3. Avaliar cada verificação como ✅ ou ❌
4. Registrar resultado na tabela de resumo abaixo
5. Em caso de ❌: anotar o que falhou, continuar para o próximo cenário
```

### PÓS-SUÍTE — LIMPEZA

```
- Remover item TESTE_E2E_ITEM (criado em 05, editado em 06)
  → Navegar para /items, localizar TESTE_E2E_ITEM, clicar Remover
```

---

## Template de relatório (preencher durante execução)

```
========================================================
RELATÓRIO DE REGRESSÃO — Easy Maintenance
Data: [DATA]
Ambiente: staging
Usuário: douglasmarquesdias@gmail.com
Org: Casa & Lajes
========================================================

CENÁRIO 01 — Login e Seleção de Organização
  Formulário de login visível ........................ [ ]
  Credenciais aceitas sem erro ....................... [ ]
  Redirecionamento para /select-organization ......... [ ]
  Seleção de org → URL / (Dashboard) ................ [ ]
  Header "Casa & Lajes" ativo ........................ [ ]
  Saudação "Douglas Marques Dias" ................... [ ]
  Menu lateral completo .............................. [ ]
  STATUS: PASSOU / FALHOU

CENÁRIO 02 — Billing Plan Change Dialog
  Página /billing carrega ............................ [ ]
  Dialog abre ao clicar "Alterar plano" .............. [ ]
  API /me/billing/plans retorna 200 .................. [ ]
  Nenhuma chamada para rota admin .................... [ ]
  STATUS: PASSOU / FALHOU

CENÁRIO 03 — Limite de Organizações
  UsageMeter "Empresas cadastradas X/Y" visível ...... [ ]
  Botão habilitado quando abaixo do limite ........... [ ]
  Botão bloqueado/mensagem quando no limite .......... [ ]
  STATUS: PASSOU / FALHOU

CENÁRIO 04 — Limite de Membros
  Tela de usuários carrega ........................... [ ]
  UsageMeter "Usuários X/Y" visível .................. [ ]
  Bloqueio ao atingir maxUsers ....................... [ ]
  STATUS: PASSOU / FALHOU

CENÁRIO 05 — Criar Item
  Contador inicial "5/500" visível ................... [ ]
  Formulário /items/new abre ......................... [ ]
  Item TESTE_E2E_ITEM criado com sucesso ............. [ ]
  Contador incrementado para "6/500" ................. [ ]
  STATUS: PASSOU / FALHOU

CENÁRIO 06 — Editar Item (DEPENDE DE 05)
  Item TESTE_E2E_ITEM na lista ....................... [ ]
  SEM mensagem "Edição indisponível" ................. [ ]
  Formulário de edição abre .......................... [ ]
  Alteração salva com sucesso ........................ [ ]
  Valor novo confirmado no detalhe ................... [ ]
  STATUS: PASSOU / FALHOU

CENÁRIO 07 — Item Bloqueado por Manutenção
  Item 43 (LIMPEZA CAIXA DE GORDURA) na lista ....... [ ]
  Botão Editar bloqueado na lista .................... [ ]
  Detalhe /items/43 carrega .......................... [ ]
  Mensagem "Edição indisponível..." visível ........... [ ]
  Botão "Registrar Manutenção" ainda disponível ...... [ ]
  STATUS: PASSOU / FALHOU

CENÁRIO 08 — Registrar Manutenção
  Formulário /maintenances/new carrega ............... [ ]
  Busca de item funciona (mín 3 chars) ............... [ ]
  Item DESENTUPIMENTO selecionado .................... [ ]
  Dados preenchidos sem erro ......................... [ ]
  Manutenção registrada com sucesso .................. [ ]
  Nova entrada aparece na listagem ................... [ ]
  STATUS: PASSOU / FALHOU

========================================================
RESUMO FINAL
  Cenários: X/8 PASSARAM
  Falhas:   [lista de cenários com falha]
  Itens críticos: [qualquer regressão nova]
========================================================
```
