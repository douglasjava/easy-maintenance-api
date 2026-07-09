# Cenário 02 — Dialog de Troca de Plano (Rota Correta)

## Objetivo
Verificar que o `PlanChangeDialog` chama `/me/billing/plans` (rota autenticada do usuário)
e **não** uma rota admin. Bug corrigido em sessão anterior.

## Pré-condição
- Usuário logado como `douglasmarquesdias@gmail.com` na org "Casa & Lajes"

## Passos

### 1. Navegar para a página de Faturamento
- Clicar em "Faturamento" no menu do perfil (dropdown superior direito) **ou** acessar `/billing`
- **Verificar:** Página "Faturamento" carrega com o plano atual exibido

### 2. Abrir o dialog de troca de plano
- Localizar o botão "Alterar plano" ou "Upgrade" na página
- Clicar no botão
- **Verificar:** Modal/dialog abre exibindo as opções de plano disponíveis

### 3. Verificar chamada de API correta
- Abrir DevTools → Network (ou usar browser automation para inspecionar requests)
- **Verificar:** Request feito para `/easy-maintenance/api/v1/me/billing/plans` com status 200
- **Verificar:** NÃO existe request para `/easy-maintenance/api/v1/admin/billing/plans` ou similar

### 4. Verificar conteúdo do dialog
- **Verificar:** Planos exibidos refletem os planos disponíveis para upgrade
- **Verificar:** Plano atual está marcado como "atual" ou equivalente
- Fechar o dialog (botão Cancelar ou X)

## Resultado Esperado
- Dialog abre sem erro
- API `/me/billing/plans` retorna 200 com lista de planos
- Nenhuma chamada 403/401 para rota admin

## Contexto técnico
- Antes do fix, o frontend chamava a rota admin ao abrir o dialog
- O fix consistiu em usar o endpoint `/me/billing/plans` que é autenticado pelo token do usuário
