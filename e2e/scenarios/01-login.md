# Cenário 01 — Login e Seleção de Organização

## Objetivo
Validar que o fluxo de login funciona e que o usuário chega ao dashboard na organização correta.

## Pré-condição
- Usuário não está logado (ou sessão foi limpa)

## Passos

### 1. Acessar a página de login
- Navegar para `/login`
- **Verificar:** Formulário com campos "E-mail" e "Senha" está visível

### 2. Preencher credenciais
- E-mail: `douglasmarquesdias@gmail.com`
- Senha: `654321`
- Clicar em "Entrar" (ou equivalente)

### 3. Verificar redirecionamento
- **Verificar:** URL muda para `/` (Dashboard) ou `/organizations`
- **Verificar:** Nenhuma mensagem de erro aparece

### 4. Confirmar organização ativa
- **Verificar:** Header mostra "Casa & Lajes" como organização ativa (dropdown no topo)
- **Verificar:** Saudação "Bem-vindo, Douglas Marques Dias" está visível

### 5. Navegar pelo menu lateral
- **Verificar:** Links "Dashboard", "Itens", "Manutenções" estão presentes
- **Verificar:** Seção "Ações" com "Novo Item", "Registrar Manutenção", "IA Onboarding"

## Resultado Esperado
- Usuário autenticado e na organização "Casa & Lajes"
- Dashboard carregado sem erros

## Dados de referência
- URL base: `https://easy-maintenance-web-production.up.railway.app`
- Após login, token JWT é armazenado no localStorage/sessionStorage
