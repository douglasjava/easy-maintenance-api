# Cenário 05 — Criação de Item + Validação do Contador

## Objetivo
Verificar que um novo item pode ser criado com sucesso e que o contador de itens
é incrementado corretamente após a criação.

## Pré-condição
- Usuário logado como `douglasmarquesdias@gmail.com` na org "Casa & Lajes"
- Conta com menos de `maxItems` itens cadastrados (atualmente: 5/500)

## Passos

### 1. Navegar para a lista de itens
- Acessar `/items`
- **Verificar:** Contador "Itens X/500" visível (ex: `5/500`)
- **Anotar:** o número atual de itens para comparar depois

### 2. Abrir formulário de criação
- Clicar em "+ Novo Item"
- **Verificar:** Navega para `/items/new`
- **Verificar:** Formulário "Novo Item" está visível com campos:
  - TIPO DO ITEM (combobox com busca/criação livre)
  - CATEGORIA (Regulatória / Operacional)
  - ÚLTIMA MANUTENÇÃO (opcional, date)

### 3. Preencher o formulário — item Operacional
- **TIPO DO ITEM:** Digitar `TESTE_E2E_ITEM` e selecionar/criar a opção
  - Hint: "Escreva de forma padronizada. Ex: EXTINTOR_CO2, CAIXA_DAGUA."
- **CATEGORIA:** Selecionar "Operacional"
  - Ao selecionar Operacional, a seção de periodicidade muda (sem campo Norma)
  - **Verificar:** Aparecem campos de periodicidade: Unidade (MESES/DIAS/ANOS) e Quantidade
- **ÚLTIMA MANUTENÇÃO:** Preencher com data atual ou deixar em branco
- **Periodicidade:** Preencher quantidade (ex: `6`) e unidade (`MESES`)

### 4. Submeter o formulário
- Clicar em "Criar item"
- **Verificar:** Nenhuma mensagem de erro aparece
- **Verificar:** Redirecionamento para a lista de itens ou para o detalhe do item criado

### 5. Confirmar item na lista
- Navegar para `/items` (se não redirecionou automaticamente)
- **Verificar:** Item `TESTE_E2E_ITEM` aparece na lista
- **Verificar:** Contador incrementou: `6/500` (ou N+1/500)
- **Verificar:** Status inicial exibido como "Em dia" ou sem status

## Resultado Esperado
- Item criado com sucesso
- Contador de itens incrementado em 1
- Item aparece na lista com categoria "Operacional" e status correto

## Limpeza (opcional)
- Após o cenário 06 (edição), o item `TESTE_E2E_ITEM` pode ser removido via botão "Remover"

## Dados de referência
- Nome sugerido para criação: `TESTE_E2E_ITEM`
- O item criado aqui é usado pelo cenário `06-items-edit.md`
