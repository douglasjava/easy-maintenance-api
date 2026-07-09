# Cenário 07 — Edição de Item Bloqueada por Manutenção

## Objetivo
Verificar que um item com manutenções registradas exibe mensagem de bloqueio
ao tentar editar e que o botão Editar está desabilitado.

## Pré-condição
- Usuário logado como `douglasmarquesdias@gmail.com` na org "Casa & Lajes"
- Item "LIMPEZA CAIXA DE GORDURA" (ID: 43) já tem manutenções registradas

## Passos

### 1. Navegar para a lista de itens
- Acessar `/items`
- **Verificar:** Item "LIMPEZA CAIXA DE GORDURA" aparece na lista
- **Verificar:** Na linha do item, o botão "Editar" está visualmente diferente
  (desabilitado, opaco, ou com ícone de bloqueio)

### 2. Verificar bloqueio na lista
- Passar o mouse sobre o botão "Editar" da linha "LIMPEZA CAIXA DE GORDURA"
- **Verificar:** Tooltip ou mensagem de bloqueio é exibida

### 3. Navegar para o detalhe do item
- Clicar em "Abrir" na linha "LIMPEZA CAIXA DE GORDURA"
- **Verificar:** URL muda para `/items/43?origin=items`
- **Verificar:** Página de detalhe carrega com:
  - Título: "Detalhe do Item"
  - Nome: "LIMPEZA CAIXA DE GORDURA"
  - Categoria: "Operacional"
  - Status: "Em dia"

### 4. Verificar mensagem de bloqueio no detalhe
- **Verificar:** Texto `"Edição indisponível: este item possui manutenções registradas."`
  está visível próximo ao botão Editar
- **Verificar:** Botão "Editar" está presente mas desabilitado (não abre formulário ao clicar)

### 5. Verificar chamada de API
- **Verificar:** A requisição `GET /easy-maintenance/api/v1/items/43/can-update` retorna 200
  com `canUpdate: false` (ou campo equivalente indicando bloqueio)

### 6. Verificar que outras ações ainda funcionam
- **Verificar:** Botão "Registrar Manutenção" ainda está disponível e funcional
- **Verificar:** Botão "Remover" ainda está disponível

## Resultado Esperado
- Mensagem `"Edição indisponível: este item possui manutenções registradas."` exibida
- Botão "Editar" desabilitado (clicá-lo não abre formulário)
- `GET /items/{id}/can-update` confirma bloqueio
- Outras ações (Registrar Manutenção, Remover) permanecem acessíveis

## Contexto técnico
- A tela chama `GET /items/{id}/can-update` ao carregar o detalhe
- O estado `ITEM_ALREADY_USED_IN_MAINTENANCE` é derivado da existência de manutenções
- Todos os 5 itens existentes no staging já têm manutenções registradas
- IDs afetados: 43, 89, 90, 143, 145
