# Cenário 04 — Limite de Membros de Equipe (UsageMeter + Bloqueio)

## Objetivo
Validar que o UsageMeter de usuários por organização reflete a contagem correta e que
convidar um novo membro é bloqueado ao atingir o `maxUsers` do plano do dono.

## Pré-condição
- Usuário logado como `douglasmarquesdias@gmail.com` (role ADMIN)
- Plano com `maxUsers > 0` (ex: Starter = 10)

## Passos

### 1. Navegar para a tela de Usuários
- Clicar em "Usuários" no menu do perfil (dropdown superior direito)
  **ou** acessar `/users` / `/team`
- **Verificar:** Página de gestão de equipe carrega com a lista de membros

### 2. Verificar UsageMeter de membros
- **Verificar:** UsageMeter exibe formato `X / Y` (ex: `3 / 10`)
- **Verificar:** Barra de progresso proporcional ao uso

### 3. Verificar botão de convite quando ABAIXO do limite
- Se `atual < max`: botão "Convidar membro" ou "+ Adicionar" deve estar habilitado
- **Verificar:** Clicar abre o formulário/modal de convite

### 4. Verificar botão de convite quando NO/ACIMA do limite
- Se `atual >= max`: botão deve exibir mensagem de bloqueio
- **Verificar:** Mensagem similar a `"Limite de X usuários atingido. Faça upgrade do plano."`

### 5. Validação backend no convite
- Ao tentar convidar via API com org no limite, backend deve retornar:
  `"Limite de usuários atingido (X/Y). Faça upgrade do seu plano para adicionar mais membros."`
- **Verificar:** Frontend exibe esse erro como toast/alerta

## Resultado Esperado
- UsageMeter exibe contagem real de membros da org
- Bloqueio ocorre tanto no frontend quanto no backend
- Mensagem clara com contexto de upgrade

## Contexto técnico
- `maxUsers` vem do USER subscription item do **dono da conta** (não da org)
- Backend: `UsersService.validateUserLimit(ownerId, orgCode)` — chamado por `TeamMemberService.inviteMember`
- Membros de equipe (sem assinatura própria) passam por `addOrganizationByInvite` (sem validação de assinatura própria)
