# Easy Maintenance API - Documentação

## 1. Visão Geral
A **Easy Maintenance API** é uma plataforma de gerenciamento de manutenção preditiva e corretiva, projetada para ajudar organizações a monitorar seus ativos, cumprir normas técnicas e otimizar processos de manutenção através de Inteligência Artificial.

O sistema é multi-empresa (multi-tenant), permitindo que diferentes organizações gerenciem seus usuários e ativos de forma isolada e segura.

## 2. Tecnologias Utilizadas
- **Linguagem:** Java 21
- **Framework:** Spring Boot 3.x
- **Persistência:** Spring Data JPA / Hibernate
- **Banco de Dados:** MySQL
- **Migrações:** Flyway
- **Segurança:** Spring Security + JWT (JSON Web Token)
- **Documentação:** SpringDoc / OpenAPI (Swagger)
- **Utilitários:** Lombok, MapStruct
- **Integração IA:** Assistente de manutenção e sugestões automáticas.

## 3. Arquitetura e Estrutura
O projeto segue uma arquitetura modular baseada em domínios:

- `admin`: Operações de bootstrap e gerenciamento de nível de sistema.
- `org_users`: Gestão de organizações (tenants) e usuários.
- `assets`: Gerenciamento de itens de manutenção (ativos) e registros de manutenção.
- `supplier`: Busca e integração com fornecedores de serviços.
- `catalog_norms`: Catálogo técnico de normas e recomendações.
- `ai`: Módulo de Inteligência Artificial para resumos e assistência.
- `dashboard`: Agregação de dados para visão gerencial e KPIs.
- `shared`: Componentes compartilhados (segurança, filtros, exceções, configuração).

## 4. Segurança e Multi-tenancy
### Autenticação
A autenticação é baseada em JWT. Ao realizar o login, o usuário recebe um `accessToken` que deve ser enviado no cabeçalho `Authorization: Bearer <token>` em todas as requisições protegidas.

### Multi-tenancy (X-Org-Id)
Para garantir o isolamento dos dados, a maioria dos endpoints requer o cabeçalho `X-Org-Id`. Este cabeçalho identifica qual organização o usuário está acessando no momento. O sistema valida se o usuário autenticado tem permissão para acessar a organização informada.

### Primeiro Acesso
O sistema possui uma lógica de troca de senha obrigatória no primeiro acesso. O endpoint de login retorna um campo `firstAccess: true` se o usuário precisar alterar a senha antes de prosseguir.

## 5. Módulos da API

### Admin
Reservado para configuração inicial do sistema. Permite criar as primeiras organizações e usuários sem a necessidade de um tenant prévio. Protegido por um token de bootstrap configurado no servidor.

### Organizações e Usuários
Gerencia o cadastro de empresas e seus colaboradores. Suporta diferentes perfis de acesso (Admin, Manager, User, etc.).

### Ativos (Items) e Manutenções
Permite o cadastro de equipamentos ou áreas que necessitam de manutenção. Cada item pode ter um histórico de manutenções realizadas, certificados e comprovantes.

### Inteligência Artificial
- **Resumo Executivo:** Gera uma análise do status atual das manutenções da organização.
- **Assistente:** Chatbot para tirar dúvidas sobre procedimentos e normas.
- **Sugestão de Itens:** Ajuda a configurar novos ativos com base no tipo de equipamento.

### Fornecedores
Busca de prestadores de serviço próximos com base na geolocalização e especialidade técnica.

### Dashboard
Visão consolidada com:
- KPIs de manutenção.
- Itens que requerem atenção imediata.
- Calendário de eventos futuros.

## 6. Configuração e Execução

### Variáveis de Ambiente Necessárias
As seguintes variáveis devem ser configuradas (ou passadas via profile):
- `JWT_SECRET`: Chave para assinatura dos tokens.
- `MYSQLHOST`, `MYSQLPORT`, `MYSQLUSER`, `MYSQLPASSWORD`, `MYSQL_DATABASE`: Dados de conexão com o banco.
- `BOOTSTRAP_ADMIN_TOKEN`: Token para acesso aos endpoints administrativos.

### Execução via Docker
O projeto inclui um `docker-compose.yml` para facilitar o setup local:
```bash
docker-compose up -d
```

### Documentação Online (Swagger)
Após iniciar a aplicação, a documentação interativa está disponível em:
`http://localhost:8080/swagger-ui.html`

## 7. Fluxo Principal de Uso
1. **Criação da Org:** Via AdminController, cria-se a organização.
2. **Criação de Usuário:** Cria-se o usuário administrador da org.
3. **Primeiro Acesso:** O usuário loga, recebe o status de `firstAccess` e usa o token para definir sua senha definitiva.
4. **Cadastro de Ativos:** O usuário cadastra os itens que precisam de manutenção.
5. **Gestão:** O sistema monitora as datas e alerta via Dashboard e IA.
