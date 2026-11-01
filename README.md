# Agendamento Rede Krill

Sistema web para agendar a entrega de caminhões no recebimento do CD, controlando a capacidade de paletes por dia e por faixa horária. Regras, telas e visual vêm do **Gestão CD**, o sistema de referência da Rede Krill (veja [docs/PORTE-GESTAO-CD.md](docs/PORTE-GESTAO-CD.md)).

- **Backend:** Java 17+, Spring Boot 3.5 (Web, Validation, Data JPA, Security com JWT, Flyway), PostgreSQL 16, Swagger (springdoc), e-mail SMTP, Web Push (VAPID).
- **Frontend:** Vue 3 + Vite + PrimeVue, PWA instalável (manifest, service worker, tela offline, notificações push).
- **Infra:** Docker multi-stage (usuário não-root), nginx para o frontend, docker-compose com `db`, `api`, `web` e `mail` (Mailpit).

## Como rodar

Pré-requisito: Docker com o plugin Compose (Docker Desktop no Windows/macOS). Não precisa de Java, Node nem Postgres na máquina.

```bash
git clone https://github.com/New-Legend/desafio-krill-agendamento.git
cd desafio-krill-agendamento
docker compose up --build        # primeira vez leva alguns minutos (compila API e tela)
```

Quando os quatro serviços ficarem `healthy` (`docker compose ps`), abra:

| Serviço | Endereço |
|---|---|
| Aplicação (PWA) | http://localhost:8081 |
| API / Swagger | http://localhost:8080/swagger-ui/index.html (também em http://localhost:8081/swagger-ui/index.html) |
| Caixa de e-mail (Mailpit) | http://localhost:8025 |

Para parar: `docker compose down` (os dados ficam no volume; `docker compose down -v` apaga tudo e recria do zero na próxima subida).

Acesso de outro computador ou do celular na mesma rede: troque `localhost` pelo IP da máquina que roda o compose (ex.: `http://192.168.0.10:8081`). Para acesso pela internet é preciso publicar em um servidor ou usar um túnel (ex.: Cloudflare Tunnel) apontando para a porta 8081.

### Usuários padrão

| Usuário | Senha | Perfil |
|---|---|---|
| `admin` | `admin123` | Administrador — todas as permissões |
| `usuario` | `usuario123` | Consulta — leitura de operação, recebimento e paletes |

As senhas podem ser trocadas pelas variáveis `ADMIN_PASSWORD` e `USUARIO_PASSWORD` (veja `.env.example`).

### Desenvolvimento local

```bash
# banco e e-mail
docker compose up -d db mailpit
# API (porta 8080)
cd backend && DB_URL=jdbc:postgresql://localhost:5432/agendamento mvn spring-boot:run
# frontend (porta 5173, proxy /api -> 8080)
cd frontend && npm ci && npm run dev
```

### Testes

```bash
cd backend && mvn verify          # JUnit 5 + Mockito + Testcontainers + JaCoCo (mínimo 80% em domain/application)
cd frontend && npm test           # Vitest + Vue Test Utils (npm run test:coverage: mínimo 80% de linhas e ramos nas telas críticas)
```

Relatório de cobertura: `backend/target/site/jacoco/index.html`.

## Menu

Um único menu: a grade de módulos da tela inicial (AGE, REC, PAL, CAD, ADM, em duas colunas) e o menu lateral (☰) usam os mesmos grupos, e cada tela aparece uma vez só. Abaixo da grade fica o painel do dia: ocupação do dia e por faixa, status, próximos caminhões e alertas.

Cada tela aberta vira uma aba abaixo do cabeçalho (Início fica fixa): a aba guarda o estado da tela, fecha no × ou com o botão do meio do mouse e pode ser arrastada para mudar a ordem. No celular, a barra mostra o nome da tela e um atalho para o Início.

| Grupo | Telas |
|---|---|
| Agendamentos | Ocupação da semana, Agendamentos, Novo agendamento, Links de agendamento, Relatório |
| Recebimento | Pátio e docas, Divergências, Tempos de descarga |
| Paletes | Vale-palete, Conta-corrente, Cobranças e recibos, Paletes nas lojas |
| Cadastros | CDs, Lojas, Fornecedores, Produtos, Capacidade diária, Capacidade por CD e loja, Docas, Faixas horárias, Caminhões, Parâmetros de descarga, Taxa de descarga, Paletização, Tipos de palete, Conferentes |
| Acessos e auditoria | Usuários, Perfis e permissões, Logs de acesso, Auditoria |

Cada usuário só vê os itens que o perfil permite. CNPJ é digitado com máscara (00.000.000/0000-00) e o erro aparece embaixo do campo antes de enviar. O e-mail de confirmação sai ao agendar pelo sistema e pelo link; com `docker compose up`, as mensagens ficam em http://localhost:8025 (Mailpit).

## Regras de negócio

- Capacidade padrão de **480 paletes/dia**, em **4 faixas de 2 horas** com **120 paletes** cada (tudo editável nos cadastros).
- **Não paletizado conta em dobro** (fator multiplicador do tipo de paletização).
- Ultrapassar a capacidade **não bloqueia**: o sistema mostra alerta na tela, no e-mail interno e via push.
- Ao criar um agendamento, o fornecedor recebe e-mail com os dados e um **link público** para confirmar presença e informar placa/motorista.
- Fluxo da portaria: Agendado → Confirmado → Chegou → Em recebimento → Recebido (com divergência automática se a contagem diferir), ou Cancelado / Não compareceu.
- **Vale-palete** com assinatura do motorista e do conferente (dedo ou mouse), como no Gestão CD: os dois quadros já aparecem no **Novo vale-palete** (nome + assinatura, "Limpar / refazer", "obrigatória" quando a configuração exige as duas), e no vale emitido dá para assinar, refazer e imprimir com as assinaturas. A lista mostra "Pendente: Motorista e Conferente" ou "Assinada (2/2)". Print: `docs/prints/desktop-51-vale-palete-assinaturas.png`.
- Auditoria (permissão 1005) de toda ação, incluindo login: quem, quando, ação, entidade e IP.

### Gestão de acessos (padrão do Gestão CD)

- **Permissões numeradas** a partir de 1001, uma por tela ou ação, agrupadas por módulo: 10xx Administração, 11xx Operação, 12xx Recebimento, 13xx Paletes, 14xx Cadastros. O catálogo fica em `Permissao.java` e sai em `GET /api/v1/auth/permissoes`.
- **Perfis** semeados pela migração V7: Administrador (todas), Recebimento (operação, recebimento e paletes), Portaria (chegada, pátio, doca e vale-palete) e Consulta (leitura). Novos perfis e ajustes em **Administração → Perfis e permissões**, com "Marcar módulo".
- **Usuários:** lista com pílulas de perfil e situação, último acesso, filtros e menu ⋮ (editar, redefinir senha, inativar). Usuário novo ou com senha redefinida entra com **senha provisória** e só usa o sistema depois de trocá-la.
- **Botão Permissões** (ícone de pessoa) no topo de cada tela: mostra os números que a tela usa, se o seu perfil tem cada um, e leva ao editor de perfis.
- **No backend** o JWT só identifica o usuário: a cada requisição o filtro lê usuário e perfil no banco e entrega as permissões como authorities (`PERM_1103`). A tabela rota → permissão (`RegrasAcesso`) cobre toda escrita; o que não estiver nela é negado. Mudar o perfil ou inativar o usuário vale na hora.
- **No front** o guarda de rota e o menu usam os mesmos números; o botão de cada ação só aparece para quem tem a permissão.
- **Logs de acesso** (permissão 1004): entradas, saídas, senhas recusadas e trocas de senha, com IP e navegador.
- **Push** só chega a quem tem a permissão 1102 (Agendamentos: consultar), como o Gestão CD faz com `agendamento_ver`.

### Links de agendamento por CD e loja (porte do Gestão CD)

Mesmo fluxo da tela `/recebimento/agendamentos/links` do Gestão CD: um link fixo por CD ou por loja.

1. Entre como `admin` e abra **Operação → Links de agendamento** (permissão 1107, só o perfil Administrador).
2. Em **CD ou loja** escolha o destino (cada CD ativo — 801, 805, 806 e os que forem cadastrados — ou cada loja ativa de **Cadastros → Lojas**) e clique em **Gerar / regenerar link**. O link sai copiado, no formato `http://localhost:8081/agendar/<token>` (token aleatório de 256 bits). Gerar de novo para o mesmo destino desativa o link anterior.
3. Mande o link aos fornecedores. Quem abre vê só um formulário em branco: fornecedor, CNPJ (opcional), e-mail, nº do pedido, tipo de caminhão, paletização, paletes, transportadora, observação e a grade dos próximos 14 dias × faixas com os paletes livres. Nenhum agendamento ou fornecedor é listado; só a ocupação agregada do CD.
4. Faixa ou dia sem vaga **não bloqueia**: a tela avisa antes de enviar, o agendamento é gravado com os alertas (e o CD recebe o push "Capacidade excedida"), como no cadastro interno. No link de loja, entra também o alerta da capacidade diária da loja.
5. Ao enviar, o fornecedor recebe o número do agendamento e o **link do seu agendamento** (copiar, WhatsApp, e-mail) para confirmar presença, informar placa e remarcar.
6. Na tela de links, **Revogar** desativa o link na hora: quem abrir vê "Este link de agendamento foi desativado. Peça um novo link ao CD."

Auditoria: `GERAR_LINK_AGENDAMENTO`, `REVOGAR_LINK_AGENDAMENTO` e `AGENDAR_PELO_LINK` (com destino, IP e alertas), além do `CRIAR` do agendamento. As rotas públicas `/api/v1/publico/agendar/{token}` têm limite por IP (8 envios a cada 10 minutos e 60 consultas por minuto) e campo-isca contra robôs.

| Tela | Print |
|------|-------|
| Links de agendamento | `docs/prints/desktop-48-links-agendamento.png` |
| Formulário público | `docs/prints/desktop-49-agendar-pelo-link.png` |
| Agendamento enviado | `docs/prints/desktop-50-agendamento-enviado.png` |

A migração V9 inclui os CDs 801 e 805 (se não existirem) e cria o cadastro de lojas, já com as lojas usadas na capacidade, na entrega direta e nos romaneios; a V10 cadastra as 24 lojas da Rede Krill (número e nome, como no Gestão CD) e retira as lojas de exemplo.

### Recebimento completo (porte do Gestão CD)

- **Remarcação:** o fornecedor remarca pelo link público, só em faixa com vaga (o servidor recusa se estourar). O CD remarca pelo detalhe e pode exceder, com alerta.
- **Multi-CD:** cada centro de distribuição pode ter capacidade própria por dia e por faixa. O CD 1 segue as regras do desafio (480/120) e é o padrão de tudo.
- **Cadastros:** fornecedores (CNPJ validado), produtos com peso, tempos de descarga por tipo de caminhão (previsto, tolerância de espera, custo por palete).
- **NF-e:** importação do XML no detalhe do agendamento (parser sem XXE, até 2 MB), com criação dos produtos, peso calculado × peso da nota e avisos.
- **Relatórios:** divergências (CSV), tempos de espera e descarga (CSV) e **rotina do dia** com as pendências do recebimento.
- **Paletes:** conta-corrente por fornecedor, cobrança com recibo impresso, romaneios de envio/retorno para as lojas e saldo por loja.
- **Tipos de palete configuráveis** (PBR, CHEP, Chapatex, Descartável + os que o CD criar), usados no vale, na devolução e nos romaneios.
- **E-mail da cobrança de paletes** (ou do recibo) pelo SMTP/Mailpit, com histórico de envios.
- **WhatsApp:** botão que abre o `wa.me` com a mensagem pronta (agendamento e cobrança). Envio automático pela API do WhatsApp Business é opcional (`WHATSAPP_PROVEDOR=cloud-api`), desligado por padrão.
- **Conferentes por CD:** a conferência só inicia com um conferente ativo do CD (quando houver cadastro).
- **Pátio e docas:** cadastro de docas por CD e quadro do pátio (previstos, aguardando doca, na doca, descarregando, liberados), atribuição/liberação de doca e indicador de **CD lotado agora** (docas) e **capacidade do dia**.
- **Capacidade diária por CD e por loja:** limite de paletes e/ou caminhões por dia, geral ou por dia da semana. O novo agendamento mostra o % do destino (CD ou loja da entrega direta) e alerta ao passar.
- **Taxa de descarga por modal** (sider/carreta, baú, granel e outros), deduzida do tipo de caminhão quando não informada, exibida em cada caminhão do pátio.
- **Produtos:** exportação do catálogo em CSV e **produto avulso** lançado na conferência (fora da NF-e).
- **Vale-palete:** tipos de palete por CD, opção **exigir as duas assinaturas** antes da devolução e conta-corrente agrupada por **transportadora**.

## Arquitetura

Clean Architecture, com dependências apontando para dentro:

```
backend/src/main/java/br/com/redekrill/agendamento
├── domain          regras puras (Agendamento, ValePalete, CalculadoraOcupacao, ValidadorFaixas) — sem Spring
├── application     casos de uso + portas (interfaces de repositório, e-mail, push, ERP, transação)
├── infrastructure  adaptadores: JPA, segurança/JWT, SMTP, Web Push, ERP (Bluesoft e local), configuração
└── web             controllers REST /api/v1, DTOs e tratamento de erros
```

- A API é versionada em **`/api/v1`** e documentada no Swagger com exemplos.
- A regra de capacidade fica no domínio e é testada sem banco; os casos de uso usam portas, então trocar o banco, o e-mail ou o ERP não mexe na regra.
- Criação de agendamento usa `pg_advisory_xact_lock` por dia para dois usuários não estourarem a mesma faixa sem ver o alerta.

## Integração com o ERP (Bluesoft)

O formulário preenche fornecedor, e-mail, paletes e paletização a partir do **Nº do pedido de compra**. A consulta passa pela porta `PedidoCompraGateway`, que tem dois adaptadores:

- `local` (padrão): base de demonstração com os pedidos 450001, 450002 e 450003.
- `bluesoft`: OAuth `client_credentials` em `erp.bluesoft.com.br/<tenant>/oauth2/token` + `GET /api/compras/pedidos-de-compra`. Ative com `ERP_PROVEDOR=bluesoft` e as variáveis `BLUESOFT_*`.

Detalhes em [docs/INTEGRACAO-BLUESOFT.md](docs/INTEGRACAO-BLUESOFT.md).

## Documentos

- [docs/ENTREGA.md](docs/ENTREGA.md): resumo da entrega, decisões e segurança
- [docs/PORTE-GESTAO-CD.md](docs/PORTE-GESTAO-CD.md): o que veio do Gestão CD
- [docs/INTEGRACAO-BLUESOFT.md](docs/INTEGRACAO-BLUESOFT.md)
- [docs/SEGURANCA.md](docs/SEGURANCA.md): relatório OWASP Dependency-Check / npm audit
- [docs/ROTEIRO-VIDEO-E-DEFESA.md](docs/ROTEIRO-VIDEO-E-DEFESA.md)
- [docs/prints/](docs/prints): telas desktop e celular
