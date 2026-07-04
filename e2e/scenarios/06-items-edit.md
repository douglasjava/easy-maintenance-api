# Cenário 06 — Edição de Item (Sem Manutenções)

## Objetivo
Verificar que um item sem manutenções registradas pode ser editado com sucesso.

## Pré-condição
- Cenário `05-items-create.md` executado — item `TESTE_E2E_ITEM` criado
- Usuário logado como `douglasmarquesdias@gmail.com` na org "Casa & Lajes"
- O item `TESTE_E2E_ITEM` não tem manutenções registradas (recém-criado)

## Passos

### 1. Localizar o item criado
- Acessar `/items`
- **Verificar:** Item `TESTE_E2E_ITEM` aparece na lista
- Clicar em "Abrir" no item `TESTE_E2E_ITEM` para acessar o detalhe
  **ou** clicar diretamente em "Editar" na linha da tabela

### 2. Verificar que edição está disponível
- Na página de detalhe do item: **Verificar** que NÃO aparece a mensagem
  `"Edição indisponível: este item possui manutenções registradas."`
- **Verificar:** Botão "Editar" está habilitado (sem tooltip de bloqueio)

### 3. Abrir o formulário de edição
- Clicar em "Editar"
- **Verificar:** Formulário de edição abre com os dados atuais preenchidos:
  - TIPO DO ITEM: `TESTE_E2E_ITEM`
  - CATEGORIA: Operacional
  - ÚLTIMA MANUTENÇÃO: vazio ou data preenchida
  - Campos de periodicidade preenchidos

### 4. Modificar um campo
- Alterar a **quantidade de periodicidade** de `6` para `12`
  (ou outro campo que seja facilmente verificável)

### 5. Salvar as alterações
- Clicar em "Salvar" (ou equivalente)
- **Verificar:** Mensagem de sucesso (toast) exibida
- **Verificar:** Redirecionamento de volta ao detalhe ou lista do item

### 6. Confirmar alteração
- Abrir o detalhe do item `TESTE_E2E_ITEM`
- **Verificar:** Campo "Quantidade" exibe `12` (valor novo)
- **Verificar:** Categoria ainda é "Operacional" (campo não alterado manteve valor)

## Resultado Esperado
- Item editado com sucesso
- Novo valor persistido corretamente
- Outros campos não alterados permanecem iguais

## Contexto técnico
- A API chama `GET /items/{id}/can-update` antes de permitir edição
- Retorna `canUpdate: true` quando não há manutenção ativa/recente
- Itens com manutenções registradas têm `canUpdate: false` (ver cenário 07)
