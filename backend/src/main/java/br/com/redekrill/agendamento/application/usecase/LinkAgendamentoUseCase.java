package br.com.redekrill.agendamento.application.usecase;

import br.com.redekrill.agendamento.application.dto.NovoAgendamento;
import br.com.redekrill.agendamento.application.dto.ResultadoAgendamento;
import br.com.redekrill.agendamento.application.dto.Solicitante;
import br.com.redekrill.agendamento.application.port.CapacidadeRecebimentoRepositorio;
import br.com.redekrill.agendamento.application.port.CentroDistribuicaoRepositorio;
import br.com.redekrill.agendamento.application.port.FornecedorRepositorio;
import br.com.redekrill.agendamento.application.port.GeradorToken;
import br.com.redekrill.agendamento.application.port.LinkAgendamentoRepositorio;
import br.com.redekrill.agendamento.application.port.TipoCaminhaoRepositorio;
import br.com.redekrill.agendamento.application.port.TipoPaletizacaoRepositorio;
import br.com.redekrill.agendamento.application.port.Transacao;
import br.com.redekrill.agendamento.domain.exception.RecursoNaoEncontradoException;
import br.com.redekrill.agendamento.domain.exception.RegraNegocioException;
import br.com.redekrill.agendamento.domain.model.AlertaCapacidade;
import br.com.redekrill.agendamento.domain.model.CapacidadeRecebimento;
import br.com.redekrill.agendamento.domain.model.CentroDistribuicao;
import br.com.redekrill.agendamento.domain.model.Fornecedor;
import br.com.redekrill.agendamento.domain.model.LinkAgendamento;
import br.com.redekrill.agendamento.domain.model.OcupacaoDia;
import br.com.redekrill.agendamento.domain.model.TipoCaminhao;
import br.com.redekrill.agendamento.domain.model.TipoPaletizacao;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Links públicos de agendamento (porte de /recebimento/agendamentos/links do Gestão CD): o CD gera um link
 * fixo por CD, por loja ou para todos os CDs; quem abre agenda a entrega num formulário em branco, sem ver
 * agendamentos de ninguém. Capacidade estourada só gera alerta, como no cadastro interno.
 */
public class LinkAgendamentoUseCase {

    static final int DIAS_DISPONIVEIS = 14;

    private final LinkAgendamentoRepositorio links;
    private final AgendamentoUseCase agendamentos;
    private final PatioUseCase patio;
    private final CentroDistribuicaoRepositorio cds;
    private final CapacidadeRecebimentoRepositorio capacidades;
    private final br.com.redekrill.agendamento.application.port.LojaRepositorio lojas;
    private final FornecedorRepositorio fornecedores;
    private final TipoCaminhaoRepositorio caminhoes;
    private final TipoPaletizacaoRepositorio paletizacoes;
    private final GeradorToken tokens;
    private final AuditoriaUseCase auditoria;
    private final Transacao transacao;
    private final Clock clock;
    private final String urlPublica;

    public record Destino(Long cdId, String loja, String descricao) {
    }

    public record LinkResumo(LinkAgendamento link, String destino, String url) {
    }

    /** O que o formulário público precisa: destino, CDs a escolher (só no link de todos os CDs) e tipos. */
    public record Formulario(String destino, String loja, List<CentroDistribuicao> cds, List<TipoCaminhao> caminhoes,
                             List<TipoPaletizacao> paletizacoes) {
    }

    public record PedidoPublico(LocalDate data, Long faixaHorariaId, Long idPedido, String fornecedor,
                                String cnpj, String emailFornecedor, Long tipoCaminhaoId, Long tipoPaletizacaoId,
                                Integer quantidadePaletes, String transportadora, String observacao) {
    }

    public record Agendado(ResultadoAgendamento resultado, String destino, List<String> alertas) {
    }

    public LinkAgendamentoUseCase(LinkAgendamentoRepositorio links, AgendamentoUseCase agendamentos, PatioUseCase patio,
                                  CentroDistribuicaoRepositorio cds, CapacidadeRecebimentoRepositorio capacidades,
                                  br.com.redekrill.agendamento.application.port.LojaRepositorio lojas, FornecedorRepositorio fornecedores, TipoCaminhaoRepositorio caminhoes,
                                  TipoPaletizacaoRepositorio paletizacoes, GeradorToken tokens, AuditoriaUseCase auditoria,
                                  Transacao transacao, Clock clock, String urlPublica) {
        this.links = links;
        this.agendamentos = agendamentos;
        this.patio = patio;
        this.cds = cds;
        this.capacidades = capacidades;
        this.lojas = lojas;
        this.fornecedores = fornecedores;
        this.caminhoes = caminhoes;
        this.paletizacoes = paletizacoes;
        this.tokens = tokens;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.clock = clock;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    // ---- Tela "Links de agendamento" (admin)

    /** Destinos do select: cada CD ativo e cada loja ativa do cadastro (ou com capacidade cadastrada). */
    public List<Destino> destinos() {
        List<Destino> r = new ArrayList<>();
        cds.listar().stream().filter(CentroDistribuicao::ativo)
                .sorted(java.util.Comparator.comparing(CentroDistribuicao::codigo)).forEach(c -> r.add(new Destino(c.id(), null, descricaoCd(c))));
        TreeSet<String> nomes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        lojas.listar().stream().filter(br.com.redekrill.agendamento.domain.model.Loja::ativo)
                .map(br.com.redekrill.agendamento.domain.model.Loja::nome).forEach(nomes::add);
        capacidades.listar().stream().map(CapacidadeRecebimento::loja).filter(Objects::nonNull).forEach(nomes::add);
        nomes.forEach(l -> r.add(new Destino(null, l, descricaoLoja(l))));
        return r;
    }

    public List<LinkResumo> listar() {
        return links.listar().stream().map(this::resumo).toList();
    }

    /** Gera (ou regenera) o link do destino: o link ativo do mesmo CD/loja deixa de funcionar. */
    public LinkResumo gerar(Long cdId, String loja, Solicitante s) {
        String lojaLimpa = loja == null || loja.isBlank() ? null : loja.trim();
        if ((cdId == null) == (lojaLimpa == null)) throw new RegraNegocioException("Escolha um CD ou uma loja");
        if (cdId != null) cdAtivo(cdId);
        return transacao.executar(() -> {
            OffsetDateTime agora = OffsetDateTime.now(clock);
            links.revogarAtivos(cdId, lojaLimpa, s.usuario(), agora);
            LinkAgendamento salvo = links.salvar(LinkAgendamento.novo(tokens.novo(), cdId, lojaLimpa, s.usuario(), agora));
            LinkResumo r = resumo(salvo);
            auditoria.registrar(s, "GERAR_LINK_AGENDAMENTO", "LINK_AGENDAMENTO", salvo.id(), Map.of("destino", r.destino()));
            return r;
        });
    }

    public LinkResumo revogar(Long id, Solicitante s) {
        return transacao.executar(() -> {
            LinkAgendamento l = links.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontradoException("Link", id));
            if (!l.ativo()) throw new RegraNegocioException("Link já revogado");
            links.revogar(id, s.usuario(), OffsetDateTime.now(clock));
            LinkResumo r = resumo(links.buscarPorId(id).orElseThrow());
            auditoria.registrar(s, "REVOGAR_LINK_AGENDAMENTO", "LINK_AGENDAMENTO", id, Map.of("destino", r.destino()));
            return r;
        });
    }

    // ---- Página pública /agendar/{token}

    public Formulario formulario(String token) {
        LinkAgendamento l = ativo(token);
        return new Formulario(destino(l), l.loja(), l.cdId() == null ? List.of() : List.of(cdAtivo(l.cdId())),
                caminhoes.listar().stream().filter(TipoCaminhao::ativo).toList(),
                paletizacoes.listar().stream().filter(TipoPaletizacao::ativo).toList());
    }

    /** Dias em que o destino do link recebe (próximos 14 dias), com o motivo dos dias fechados. */
    public List<br.com.redekrill.agendamento.domain.model.DiaRecebimento> calendario(String token) {
        LinkAgendamento l = ativo(token);
        LocalDate hoje = LocalDate.now(clock);
        if (calendario == null) return hoje.datesUntil(hoje.plusDays(DIAS_DISPONIVEIS))
                .map(d -> new br.com.redekrill.agendamento.domain.model.DiaRecebimento(d, true, null)).toList();
        return calendario.dias(hoje, hoje.plusDays(DIAS_DISPONIVEIS - 1), l.cdId(), l.loja());
    }

    private CalendarioRecebimentoUseCase calendario;

    public LinkAgendamentoUseCase usarCalendario(CalendarioRecebimentoUseCase calendario) {
        this.calendario = calendario;
        return this;
    }

    /** Ocupação agregada dos próximos 14 dias do CD do link (nenhum dado de outros agendamentos). */
    public List<OcupacaoDia> disponibilidade(String token) {
        LinkAgendamento l = ativo(token);
        LocalDate hoje = LocalDate.now(clock);
        return agendamentos.ocupacaoPeriodo(hoje, hoje.plusDays(DIAS_DISPONIVEIS - 1), l.cdId());
    }

    /** Cria o agendamento pelo link. Excesso de capacidade não bloqueia: volta como alerta e avisa o CD. */
    public Agendado agendar(String token, PedidoPublico p, String ip) {
        LinkAgendamento l = ativo(token);
        Long cdId = l.cdId();
        LocalDate hoje = LocalDate.now(clock);
        if (p.data() == null) throw new RegraNegocioException("Data é obrigatória");
        if (p.data().isBefore(hoje) || p.data().isAfter(hoje.plusDays(DIAS_DISPONIVEIS - 1))) {
            throw new RegraNegocioException("Escolha uma data entre hoje e os próximos " + DIAS_DISPONIVEIS + " dias");
        }
        Long fornecedorId = null;
        if (p.cnpj() != null && !p.cnpj().isBlank()) {
            String cnpj = p.cnpj().replaceAll("\\D", "");
            if (!Fornecedor.cnpjValido(cnpj)) throw new RegraNegocioException("CNPJ inválido");
            fornecedorId = fornecedores.buscarPorCnpj(cnpj).filter(Fornecedor::ativo).map(Fornecedor::id).orElse(null);
        }
        Solicitante s = new Solicitante("fornecedor:" + p.emailFornecedor(), ip);
        ResultadoAgendamento r = agendamentos.criar(new NovoAgendamento(p.data(), p.faixaHorariaId(), p.idPedido(),
                p.fornecedor(), p.emailFornecedor(), p.tipoCaminhaoId(), p.tipoPaletizacaoId(), p.quantidadePaletes(),
                p.observacao(), cdId, fornecedorId), l.loja(), s);
        Long id = r.agendamento().agendamento().getId();
        List<String> alertas = new ArrayList<>(r.alertas().stream().map(AlertaCapacidade::mensagem).toList());
        if (l.loja() != null || (p.transportadora() != null && !p.transportadora().isBlank())) {
            alertas.addAll(patio.registrarLogistica(id, l.loja(), p.transportadora(), null, s));
        }
        String destino = destino(l);
        Map<String, Object> det = new HashMap<>();
        det.put("link", l.id());
        det.put("destino", destino);
        det.put("fornecedor", r.agendamento().agendamento().getFornecedor());
        det.put("alertas", alertas);
        auditoria.registrar(s, "AGENDAR_PELO_LINK", "AGENDAMENTO", id, det);
        return new Agendado(r, destino, alertas);
    }

    // ----

    private LinkAgendamento ativo(String token) {
        if (token == null || token.length() < 20 || token.length() > 64) {
            throw new RecursoNaoEncontradoException("Link", "inválido");
        }
        // Links antigos "todos os CDs" (sem CD nem loja) não abrem mais: só ficam na lista para revogar.
        return links.buscarAtivoPorToken(token).filter(l -> !l.todosOsCds())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Link", "desativado"));
    }

    private CentroDistribuicao cdAtivo(Long cdId) {
        CentroDistribuicao c = cds.buscarPorId(cdId).orElseThrow(() -> new RecursoNaoEncontradoException("CD", cdId));
        if (!c.ativo()) throw new RegraNegocioException("CD inativo");
        return c;
    }

    private LinkResumo resumo(LinkAgendamento l) {
        return new LinkResumo(l, destino(l), l.ativo() ? urlPublica + "/agendar/" + l.token() : null);
    }

    private String destino(LinkAgendamento l) {
        if (l.loja() != null) return descricaoLoja(l.loja());
        if (l.cdId() == null) return "Link antigo sem CD definido";
        return cds.buscarPorId(l.cdId()).map(this::descricaoCd).orElse("CD " + l.cdId());
    }

    private static String descricaoLoja(String loja) {
        return loja.toLowerCase().startsWith("loja") ? loja : "Loja " + loja;
    }

    private String descricaoCd(CentroDistribuicao c) {
        String cd = "CD " + c.codigo();
        return c.nome() == null || c.nome().isBlank() || c.nome().equalsIgnoreCase(cd) ? cd : cd + " · " + c.nome();
    }
}
