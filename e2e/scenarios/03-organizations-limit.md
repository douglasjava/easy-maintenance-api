# Cenário 03 — Limite de Organizações (UsageMeter + Bloqueio)

## Objetivo
Validar que o UsageMeter de empresas reflete corretamente a contagem atual e que o botão
"Nova Empresa" fica bloqueado ao atingir o limite do plano.

## Pré-condição
- Usuário logado como `douglasmarquesdias@gmail.com`
- Conta com plano que tem `maxOrganizations > 0` (ex: Starter = 3)

## Passos

### 1. Navegar para Minhas Empresas
- Acessar `/organizations`
- **Verificar:** Página "Minhas Empresas" carrega a lista de empresas

### 2. Verificar UsageMeter
- **Verificar:** Card com "Empresas cadastradas" exibe o formato `X / Y`
  - Ex: `2 / 3` (2 empresas de um limite de 3)
- **Verificar:** Barra de progresso proporcional ao uso

### 3. Verificar botão "Nova Empresa" quando ABAIXO do limite
- Se `atual < max`: botão "+ Nova Empresa" deve estar habilitado (clicável)
- **Verificar:** Clicar no botão navega para `/organizations/new` sem mensagem de bloqueio

### 4. Verificar botão "Nova Empresa" quando NO/ACIMA do limite
- Se `atual >= max`: botão "+ Nova Empresa" deve exibir mensagem de bloqueio ao passar o mouse
  ou ao clicar
- **Verificar:** Mensagem contém texto similar a: `"Limite de X empresa(s) atingido. Faça upgrade do plano."`
- **Verificar:** Usuário NÃO é redirecionado para criação de empresa

### 5. Verificar validação no backend (se possível)
- Se tentar criar empresa via API com conta no limite, deve retornar erro com mensagem
  `"Limite de organizações atingido (X/Y). Faça upgrade..."`

## Resultado Esperado
- UsageMeter exibe contagem correta
- Bloqueio ocorre no frontend (GuardedButton) e no backend (validateOrgLimit)
- Mensagem de limite é clara e direciona ao upgrade

## Contexto técnico
- `maxOrganizations` vem de `accountAccess.features.maxOrganizations` (USER subscription item)
- Backend: `UsersService.validateOrgLimit(userId)` — lança `RuleException` ao atingir o limite
- Frontend: `GuardedButton` com `allowed={canCreate}` e `blockedMessage`
