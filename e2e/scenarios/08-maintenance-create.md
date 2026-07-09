# Cenário 08 — Registro de Manutenção

## Objetivo
Verificar o fluxo completo de registro de uma nova manutenção: seleção do item,
preenchimento dos dados e confirmação na listagem.

## Pré-condição
- Usuário logado como `douglasmarquesdias@gmail.com` na org "Casa & Lajes"
- Pelo menos um item existente (ex: "DESENTUPIMENTO DE ESGOTO" ID: 145)

## Passos

### 1. Acessar o formulário de registro
- Clicar em "Registrar Manutenção" no menu lateral (Ações)
  **ou** acessar `/maintenances/new`
- **Verificar:** Página "Registrar Manutenção" carrega
- **Verificar:** Duas abas visíveis: "Dados" e "Anexos"
- **Verificar:** Seção "1 — Selecionar item" com campo de busca

### 2. Selecionar o item (Step 1)
- No campo de busca "Digite 3 caracteres para buscar…" digitar: `DES`
- **Verificar:** Dropdown de sugestões aparece com "DESENTUPIMENTO DE ESGOTO"
- Selecionar "DESENTUPIMENTO DE ESGOTO"
- **Verificar:** Item selecionado é exibido abaixo do campo

### 3. Preencher dados da manutenção (Step 2)
- **Data da manutenção \*:** Preencher com data atual (ex: `04/07/2026`)
- **Tipo de manutenção \*:** Selecionar "Inspeção"
  - Opções disponíveis: Preventiva, Corretiva, Inspeção, Teste, Emergencial
- **Responsável (opcional):** Digitar `Teste E2E`
- **Custo R$ (opcional):** Digitar `100,00`
- **Próxima manutenção (opcional):** Deixar em branco ou preencher

### 4. Avançar para anexos
- Clicar em "Próximo →"
- **Verificar:** Aba "Anexos" fica ativa
- **Verificar:** Área de upload de arquivos está visível

### 5. Submeter sem anexos
- Clicar em "Registrar" ou "Finalizar" (botão de submit na aba Anexos)
- **Verificar:** Mensagem de sucesso exibida (toast)
- **Verificar:** Redirecionamento para `/maintenances`

### 6. Confirmar na listagem
- **Verificar:** Listagem de manutenções exibe a nova entrada com:
  - Item: "DESENTUPIMENTO DE ESGOTO"
  - Data: data preenchida no passo 3
  - Tipo: "INSPECAO"
  - Responsável: "Teste E2E"
  - Custo: R$ 100,00
- **Verificar:** Botão "Ver detalhes" disponível para a nova entrada

### 7. Verificar via item (regressão)
- Navegar para `/items/145?origin=items`
- **Verificar:** Campo "Última manutenção" foi atualizado com a data registrada
- **Verificar:** Campo "Próximo vencimento" recalculado com base na periodicidade

## Resultado Esperado
- Manutenção registrada com sucesso
- Aparece na listagem com dados corretos
- Item associado tem "Última manutenção" atualizada
- Status do item permanece "Em dia" (ou muda se vencido)

## Ponto de atenção (regressão crítica)
- Após o registro, o item 145 passará para status `ITEM_ALREADY_USED_IN_MAINTENANCE`
  e a edição ficará bloqueada (igual ao cenário 07)
- **Verificar:** Botão "Editar" do item 145 fica bloqueado após o registro
