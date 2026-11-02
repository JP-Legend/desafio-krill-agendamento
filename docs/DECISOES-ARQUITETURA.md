# Decisões de banco de dados e arquitetura

## Banco de dados: PostgreSQL 16

O agendamento é um problema relacional: agendamento, faixa horária, tipo de caminhão, tipo de paletização, CD, loja, fornecedor, vale-palete e auditoria se ligam por chaves. O PostgreSQL foi escolhido por:

- **Integridade no próprio banco.** As 16 migrações Flyway (`V1` a `V16`) criam chaves estrangeiras, `UNIQUE` e `CHECK` (por exemplo, status do agendamento), cerca de cem restrições no total. Um dado inconsistente é recusado mesmo que passe por fora da API.
- **Transação e trava para agendamentos simultâneos.** Antes de gravar, o caso de uso abre uma transação (porta `Transacao`) e chama `bloquearDia`, que executa `pg_advisory_xact_lock` com uma chave por data. Dois usuários agendando o mesmo dia ao mesmo tempo são atendidos em fila, e a soma de paletes usada no alerta de capacidade não fica errada. A trava some sozinha no fim da transação.
- **Ocupação.** A ocupação (% do dia e de cada faixa) é calculada na `CalculadoraOcupacao`, no domínio, sobre os agendamentos ativos da data lidos numa única consulta indexada. O agendamento grava os "paletes equivalentes" (não paletizado conta em dobro), então a soma não depende de reprocessar o fator. Consultas agregadas com `GROUP BY` ficam no SQL quando o volume pede (quadro do pátio e capacidade, em `LogisticaRepositorioJdbc`).
- **JSONB** para o "antes/depois" da auditoria, sem uma tabela de histórico por entidade.
- **Índices parciais** (por exemplo, token do link do fornecedor só quando existe).
- **Flyway** versiona o esquema: a primeira subida cria tudo e as seguintes aplicam só o que falta.
- **Livre e open source**, imagem oficial `postgres:16-alpine` no compose e o mesmo Postgres 16 nos testes de integração com Testcontainers, sem H2.

## Clean Architecture no backend

Pacote `br.com.redekrill.agendamento`:

| Camada | Conteúdo | Depende de |
|---|---|---|
| `domain` | Entidades e regras puras: `Agendamento`, `Escopo`, `Permissao`, `CalculadoraOcupacao`, exceções de negócio | nada (não importa Spring) |
| `application` | Casos de uso (`AgendamentoUseCase`, `CapacidadeRecebimentoUseCase`, `AcessoUseCase`, `AuditoriaUseCase`…) e portas (`AgendamentoRepositorio`, `Transacao`, `EscopoAtual`, `PedidoCompraGateway`…) | `domain` |
| `infrastructure` | Adaptadores: JPA e `JdbcClient`, SMTP, Web Push, ERP Bluesoft, WhatsApp, segurança (JWT, BCrypt, filtros), configuração dos beans | `application`, `domain` |
| `web` | Controllers REST em `/api/v1`, DTOs com exemplos do Swagger, tratador de erros | `application` |

Os casos de uso são montados em `UseCaseConfig`, sem anotações do Spring dentro deles. Por isso os testes de regra rodam com Mockito, sem subir o contexto. A capacidade só alerta (não bloqueia): a calculadora devolve a lista de alertas, o caso de uso salva e repassa os alertas para a tela, o e-mail e o push.

Trocas de integração ficam atrás de portas: o ERP tem um adaptador local (padrão) e um Bluesoft (desligado), e o WhatsApp tem link `wa.me` e um adaptador da API oficial desligado.

## Frontend e backend separados por REST

- O frontend (Vue 3 + Vite + PrimeVue) só conversa com a API por JSON em `/api/v1`, com o JWT no cabeçalho `Authorization`.
- A API é documentada no Swagger (`/swagger-ui/index.html`).
- O nginx do container `web` serve os arquivos estáticos e repassa `/api` para o container `api`, então em produção tudo fica na mesma origem.
- As permissões são decididas no backend. A tela só esconde o que o perfil não pode usar.

## E-mail em desenvolvimento: Mailpit

O envio é SMTP comum, configurado por variável de ambiente. No compose o SMTP aponta para o Mailpit, que guarda as mensagens e mostra em http://localhost:8025. Nada sai para fora e dá para conferir o e-mail de confirmação ao fornecedor. Em produção basta trocar `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER` e `SMTP_PASSWORD`. O envio é assíncrono (`@Async`): falha no SMTP não desfaz o agendamento.

## Docker

- **API:** build multi-stage. O estágio Maven baixa as dependências numa camada própria (cache) e gera o jar; o estágio final usa só `eclipse-temurin:21-jre-alpine`, copia as camadas extraídas do Spring Boot (dependências mudam menos que o código), roda como usuário `app` e tem healthcheck.
- **Web:** estágio `node:22-alpine` gera o build do Vite; o estágio final é `nginx-unprivileged:stable-alpine` (uid 101) só com os arquivos estáticos.
- **Compose:** `db`, `api`, `web` e `mail`, com healthchecks e volume para os dados do Postgres. Um `docker compose up -d --build` sobe tudo.
