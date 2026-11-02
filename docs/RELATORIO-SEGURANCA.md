# Relatório de segurança — Agendamento Rede Krill

Análise feita em 02/11/2026 (horário de Brasília) sobre o código do repositório e as imagens Docker geradas pelo `docker compose up -d --build`. As saídas completas das ferramentas estão em [`docs/seguranca/`](seguranca/).

## Ferramentas

| Ferramenta | Versão | Alvo | Saída |
|---|---|---|---|
| OSV-Scanner | 2.7.0 | Dependências Maven (`backend/pom.xml`) e npm (`frontend/package-lock.json`) | `osv-scanner.txt` |
| Trivy | 0.76.1 (base atualizada em 02/11/2026) | Imagens `krill-agendamento-api` e `krill-agendamento-web` (sistema + jars) | `trivy-api.txt`, `trivy-web.txt` |
| npm audit | npm 11.21.1 | Pacotes do frontend (todos e só produção) | `npm-audit.txt`, `npm-audit-producao.txt` |
| Semgrep (análise estática) | 1.182.1 | Código Java, JavaScript/Vue, Dockerfiles e nginx. Regras `p/java`, `p/owasp-top-ten`, `p/javascript`, `p/secrets` (208 regras em 355 arquivos) | `semgrep.txt`, `semgrep.json` |

**OWASP Dependency-Check:** o plugin `dependency-check-maven` 12.1.6 está configurado no `pom.xml` (`mvn org.owasp:dependency-check-maven:check`). Sem chave do NVD, o download inicial da base (cerca de 415 mil registros) não termina em tempo razoável neste ambiente. Por isso a verificação de dependências foi feita com OSV-Scanner e Trivy, que consultam as bases GitHub Advisory, OSV e NVD. Com `NVD_API_KEY` definida, o relatório do Dependency-Check sai em `backend/target/dependency-check-report.html`.

## Resultado por severidade

| Alvo | Antes | Depois |
|---|---|---|
| OSV-Scanner (Maven + npm) | 22: 4 críticas, 2 altas, 16 médias | **20: 4 críticas, 2 altas, 14 médias** |
| Trivy, imagem `api`: sistema Alpine | 0 | 0 |
| Trivy, imagem `api`: bibliotecas Java | 8: 2 críticas, 2 altas, 4 médias | **6: 2 críticas, 2 altas, 2 médias** |
| Trivy, imagem `web` (nginx Alpine 3.24.2) | 0 | 0 |
| npm audit, só produção (`--omit=dev`) | 0 | 0 |
| npm audit, com dependências de desenvolvimento | 18: 2 críticas, 14 moderadas, 2 baixas | 18 (só ferramentas de teste) |
| Semgrep | 2 avisos (WARNING), 0 erros | 2 avisos analisados (falso positivo) |

Das 4 críticas do OSV-Scanner, 2 são do `spring-webmvc` e 2 do `tinypool` (dependência de desenvolvimento do Vitest, não vai para a imagem).

## O que foi corrigido nesta análise

- `commons-lang3` 3.17.0 → **3.18.0** (CVE-2025-48924, recursão sem limite).
- `log4j-api` 2.24.3 → **2.25.5** (CVE-2026-49844, JSON malformado). A aplicação usa Logback; o `log4j-api` vem só como ponte.
- `frontend/nginx.conf`: o `X-Forwarded-For` enviado à API passa a ser o IP real da conexão (`$remote_addr`) em vez de acrescentar o valor recebido do cliente. Assim o IP gravado na auditoria e usado no limite de requisições do link público não pode ser forjado por quem acessa pelo nginx.
- Depois das trocas: `mvn verify` com 119 testes passando e o gate do JaCoCo atendido, `docker compose up -d --build` com os quatro serviços `healthy` e login do administrador respondendo com token.

Correções anteriores (27/10/2026): Spring Boot 3.5.6 → 3.5.16, Tomcat 10.1.61, Jackson 2.21.8, PostgreSQL JDBC 42.7.14, Netty 4.1.139, BouncyCastle 1.86, jose4j 0.9.6, async-http-client 2.16.1 e imagem nginx `stable-alpine` sem root com `apk upgrade` (58 achados na imagem web zerados).

## Achados restantes e justificativa

| Achado | Severidade | Por que não afeta / por que fica | Plano |
|---|---|---|---|
| CVE-2026-47884, `spring-webmvc` 6.2.19 | Crítica | Só ocorre com `XsltView`. A API devolve apenas JSON e não usa views | Correção só no Spring 7 (Boot 4). Migrar em versão futura |
| CVE-2026-47890, `spring-webmvc` 6.2.19 | Crítica | Exige Server-Sent Events com fragmentos de view, que não existem no projeto | Idem |
| CVE-2026-107227 e CVE-2026-107230, `async-http-client` 2.16.1 | Alta | WebSocket com compressão e pool com Kerberos/NTLM/SPNEGO. O cliente é usado só pela biblioteca de Web Push para POST HTTPS aos serviços de push | Correção só na 3.x, incompatível com `web-push` 5.1.1 |
| CVE-2026-107226 e CVE-2026-107228, `async-http-client` 2.16.1 | Média | Mesmo uso restrito acima | Idem |
| `commons-compress` 1.24.0 (2 médias) | Média | Escopo `test` (Testcontainers). Não entra no jar nem na imagem | Atualizar junto com o Testcontainers |
| `tinypool`, `vitest`, `@vitest/mocker`, `glob`, `minimatch`, `deepmerge` | 2 críticas e moderadas | Só desenvolvimento/testes. `npm audit --omit=dev` dá 0 e a imagem final só tem os arquivos estáticos no nginx | Correção pede Vitest 4 (mudança de versão maior). Fazer junto com a próxima atualização do Vite |
| Semgrep `request-host-used` em `frontend/nginx.conf` (linhas 17 e 24) | Aviso | O `Host` é repassado a um upstream fixo (`api:8080`). A API não monta links com o `Host`: os links de e-mail e do fornecedor usam `APP_URL_PUBLICA` | Falso positivo, mantido |

Risco residual: a porta 8080 da API também fica publicada no compose (para o Swagger). Quem acessa direto pode mandar `X-Forwarded-For` próprio. Em produção a API deve ficar só atrás do proxy.

## Controles da própria aplicação

- **Senhas:** BCrypt (`CodificadorSenhaBCrypt`). Senha provisória obrigatória no primeiro acesso e após redefinição. Mínimo de 8 caracteres com letras e números. No login com usuário inexistente um hash fictício é comparado, para o tempo de resposta não revelar se o usuário existe.
- **Sessão:** JWT assinado com segredo vindo de `JWT_SECRET`, API stateless.
- **Permissões no backend:** a cada requisição o `JwtFiltro` carrega do banco as permissões numeradas do perfil e `RegrasAcesso` liga cada rota de escrita a um número. Rota de escrita fora da tabela é negada. Sem a permissão, a API responde 403, mesmo que a tela esconda o botão.
- **Escopo por CD e loja:** o usuário pode ficar restrito a CDs e lojas escolhidos. A regra fica no domínio (`Escopo`) e é aplicada pelos casos de uso por meio da porta `EscopoAtual`.
- **Auditoria:** quem, quando, ação, entidade, IP e antes/depois (JSONB), inclusive login com sucesso e falha, além dos logs de acesso (entrada, saída, senha recusada, troca de senha).
- **Limite de requisições:** rota pública de agendamento por link com limite por IP (8 envios em 10 minutos e 60 consultas por minuto, HTTP 429) e campo-isca contra robôs.
- **Validação de entrada:** Bean Validation nos DTOs, erros padronizados sem stack trace, consultas parametrizadas (JPA e `JdbcClient`), CSV protegido contra injeção de fórmula, XML da NF-e sem DTD/entidades externas (sem XXE) e limitado a 2 MB, assinatura do vale só em PNG até 300 KB.
- **CORS:** só as origens de `CORS_ORIGINS` (padrão `http://localhost:5173` e `http://localhost:8081`).
- **Cabeçalhos:** CSP, `X-Frame-Options`, `X-Content-Type-Options` e `Referrer-Policy` na API e no nginx.
- **Segredos:** só por variável de ambiente (`JWT_SECRET`, `DB_PASSWORD`, `VAPID_*`, `BLUESOFT_*`, senhas iniciais). Modelo em `.env.example`.
- **Tokens públicos:** link do fornecedor e links de agendamento com token aleatório, guardado no banco só como hash SHA-256.
- **Containers:** sem root (`app` na API, uid 101 no nginx), imagens Alpine mínimas, `apk upgrade` no build e healthcheck.

## Como repetir

```bash
osv-scanner scan source -r backend/pom.xml frontend/package-lock.json
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image krill-agendamento-api
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image krill-agendamento-web
cd frontend && npm audit && npm audit --omit=dev
docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep semgrep scan --config p/java --config p/owasp-top-ten --config p/javascript --config p/secrets --metrics=off
```
