# E2E Scenarios — Easy Maintenance

Cenários manuais de regressão para execução via browser automation (Claude).

## Como executar

Diga ao Claude: **"Execute o cenário `0X-nome.md`"** — ele abrirá o browser, navegará pelos passos
e reportará cada verificação (✅ passou / ❌ falhou).

## Pré-requisitos

| Item | Valor |
|------|-------|
| URL base | `https://easy-maintenance-web-production.up.railway.app` |
| Usuário (conta principal) | `douglasmarquesdias@gmail.com` |
| Senha | `654321` |
| Org padrão | `Casa & Lajes` |
| Ambiente | staging |

> **Atenção:** Os cenários assumem dados existentes no ambiente de staging.
> IDs de itens referenciados: 43 (LIMPEZA CAIXA DE GORDURA), 89 (IMPERMEABILIZACAO DE CAIXA DAGUA),
> 90 (PINTURA DE FACHADA), 143 (REPARO DE FACHADA), 145 (DESENTUPIMENTO DE ESGOTO).

## Cenários disponíveis

| Arquivo | Descrição | Tipo |
|---------|-----------|------|
| `01-login.md` | Login e seleção de organização | Auth |
| `02-billing-plan-change.md` | Dialog de troca de plano — rota correta | Billing |
| `03-organizations-limit.md` | UsageMeter de empresas + bloqueio no limite | Limit |
| `04-team-members-limit.md` | UsageMeter de membros + bloqueio no limite | Limit |
| `05-items-create.md` | Criação de item + validação do contador | Items |
| `06-items-edit.md` | Edição de item recém-criado | Items |
| `07-items-blocked-by-maintenance.md` | Edição bloqueada por manutenção registrada | Items |
| `08-maintenance-create.md` | Registro de manutenção completo | Maintenance |

## Dependências entre cenários

```
01-login            (independente — pré-requisito de todos)
02-billing          (independente)
03-organizations    (independente)
04-team-members     (independente)
05-items-create  →  06-items-edit  (06 depende do item criado em 05)
07-items-blocked    (independente — usa item 43 já existente)
08-maintenance      (independente — usa itens existentes)
```
