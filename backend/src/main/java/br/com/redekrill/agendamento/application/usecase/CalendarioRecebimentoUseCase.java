package br.com.redekrill.agendamento.application.usecase;

import br.com.redekrill.agendamento.application.dto.Solicitante;
import br.com.redekrill.agendamento.application.port.CalendarioRecebimentoRepositorio;
import br.com.redekrill.agendamento.application.port.CentroDistribuicaoRepositorio;
import br.com.redekrill.agendamento.application.port.Transacao;
import br.com.redekrill.agendamento.domain.exception.RecursoNaoEncontradoException;
import br.com.redekrill.agendamento.domain.exception.RegraNegocioException;
import br.com.redekrill.agendamento.domain.model.CalendarioRecebimento;
import br.com.redekrill.agendamento.domain.model.CentroDistribuicao;
import br.com.redekrill.agendamento.domain.model.DiaRecebimento;
import br.com.redekrill.agendamento.domain.model.ExcecaoRecebimento;
import br.com.redekrill.agendamento.domain.model.Feriado;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calendário de recebimento por CD ou loja: dias da semana, feriados e exceções por data.
 * Dia fechado é regra rígida: o agendamento é recusado (a capacidade, ao contrário, só alerta).
 */
public class CalendarioRecebimentoUseCase {

    static final int PERIODO_MAXIMO_DIAS = 400;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Destino normalizado: loja (entrega direta) ou CD (nulo = CD padrão). */
    public record Destino(Long cdId, String loja) {
        public static Destino de(Long cdId, String loja) {
            String l = loja == null || loja.isBlank() ? null : loja.trim();
            Destino d = new Destino(l != null ? null : (cdId == null ? 1L : cdId), l);
            if (!br.com.redekrill.agendamento.application.port.EscopoAtual.get().permite(d.cdId(), d.loja()))
                throw new br.com.redekrill.agendamento.domain.exception.RecursoNaoEncontradoException("CD ou loja", l != null ? l : d.cdId());
            return d;
        }
    }

    public record Calendario(CalendarioRecebimento calendario, boolean configurado, List<ExcecaoRecebimento> excecoes) {
    }

    private final CalendarioRecebimentoRepositorio repo;
    private final CentroDistribuicaoRepositorio cds;
    private final AuditoriaUseCase auditoria;
    private final Transacao transacao;

    public CalendarioRecebimentoUseCase(CalendarioRecebimentoRepositorio repo, CentroDistribuicaoRepositorio cds,
                                        AuditoriaUseCase auditoria, Transacao transacao) {
        this.repo = repo;
        this.cds = cds;
        this.auditoria = auditoria;
        this.transacao = transacao;
    }

    public Calendario obter(Long cdId, String loja, LocalDate hoje) {
        Destino d = destinoValido(cdId, loja);
        var cal = repo.buscar(d.cdId(), d.loja());
        return new Calendario(cal.orElse(CalendarioRecebimento.livre(d.cdId(), d.loja())), cal.isPresent(),
                repo.excecoes(d.cdId(), d.loja(), hoje.minusDays(30), hoje.plusYears(2)));
    }

    public CalendarioRecebimento salvar(Long cdId, String loja, String diasSemana, boolean recebeFeriado, Solicitante s) {
        Destino d = destinoValido(cdId, loja);
        return transacao.executar(() -> {
            CalendarioRecebimento antes = repo.buscar(d.cdId(), d.loja()).orElse(null);
            CalendarioRecebimento salvo = repo.salvar(new CalendarioRecebimento(null, d.cdId(), d.loja(), diasSemana, recebeFeriado));
            Map<String, Object> det = new HashMap<>();
            det.put("destino", descricao(d));
            det.put("diasSemana", salvo.diasSemana());
            det.put("recebeFeriado", salvo.recebeFeriado());
            if (antes != null) {
                det.put("antesDiasSemana", antes.diasSemana());
                det.put("antesRecebeFeriado", antes.recebeFeriado());
            }
            auditoria.registrar(s, antes == null ? "CRIAR" : "ALTERAR", "CALENDARIO_RECEBIMENTO", salvo.id(), det);
            return salvo;
        });
    }

    public ExcecaoRecebimento adicionarExcecao(Long cdId, String loja, LocalDate data, boolean aberto, String motivo, Solicitante s) {
        Destino d = destinoValido(cdId, loja);
        return transacao.executar(() -> {
            ExcecaoRecebimento e = repo.salvarExcecao(new ExcecaoRecebimento(null, d.cdId(), d.loja(), data, aberto, motivo));
            auditoria.registrar(s, "CRIAR", "EXCECAO_RECEBIMENTO", e.id(), Map.of("destino", descricao(d),
                    "data", data.toString(), "aberto", aberto, "motivo", e.motivo() == null ? "-" : e.motivo()));
            return e;
        });
    }

    public void excluirExcecao(Long id, Solicitante s) {
        transacao.executar(() -> {
            ExcecaoRecebimento e = repo.buscarExcecao(id).orElseThrow(() -> new RecursoNaoEncontradoException("Exceção", id));
            Destino.de(e.cdId(), e.loja());
            repo.excluirExcecao(id);
            auditoria.registrar(s, "EXCLUIR", "EXCECAO_RECEBIMENTO", id, Map.of("destino",
                    descricao(new Destino(e.cdId(), e.loja())), "data", e.data().toString(), "aberto", e.aberto()));
            return null;
        });
    }

    public List<Feriado> feriados(LocalDate inicio, LocalDate fim) {
        validarPeriodo(inicio, fim);
        return repo.feriados(inicio, fim);
    }

    /** Situação de cada dia do período para o destino (sem expor dados de agendamentos). */
    public record Resumo(List<CalendarioRecebimento> calendarios, List<ExcecaoRecebimento> excecoes) {
    }

    /** Todos os calendários e exceções futuras (tabela da tela de capacidade), só dos destinos do escopo. */
    public Resumo resumo(LocalDate hoje) {
        var e = br.com.redekrill.agendamento.application.port.EscopoAtual.get();
        return new Resumo(repo.listar().stream().filter(c -> e.permite(c.cdId(), c.loja())).toList(),
                repo.excecoesDesde(hoje).stream().filter(x -> e.permite(x.cdId(), x.loja())).toList());
    }

    public List<DiaRecebimento> dias(LocalDate inicio, LocalDate fim, Long cdId, String loja) {
        validarPeriodo(inicio, fim);
        Destino d = Destino.de(cdId, loja);
        CalendarioRecebimento cal = repo.buscar(d.cdId(), d.loja()).orElse(CalendarioRecebimento.livre(d.cdId(), d.loja()));
        Map<LocalDate, String> feriados = repo.feriados(inicio, fim).stream()
                .collect(Collectors.toMap(Feriado::data, Feriado::nome, (a, b) -> a));
        Map<LocalDate, ExcecaoRecebimento> excecoes = repo.excecoes(d.cdId(), d.loja(), inicio, fim).stream()
                .collect(Collectors.toMap(ExcecaoRecebimento::data, Function.identity(), (a, b) -> a));
        return inicio.datesUntil(fim.plusDays(1)).map(x -> cal.situacao(x, feriados, excecoes)).toList();
    }

    public DiaRecebimento dia(LocalDate data, Long cdId, String loja) {
        return dias(data, data, cdId, loja).get(0);
    }

    /** Recusa o agendamento em dia em que o destino não recebe mercadoria. */
    public void exigirAberto(LocalDate data, Long cdId, String loja) {
        if (data == null) return;
        DiaRecebimento dia = dia(data, cdId, loja);
        if (!dia.aberto()) {
            Destino d = Destino.de(cdId, loja);
            throw new RegraNegocioException(capitalizar(descricao(d)) + " não recebe mercadoria em " + data.format(BR)
                    + " (" + dia.motivo() + "). Escolha outro dia.");
        }
    }

    private Destino destinoValido(Long cdId, String loja) {
        Destino d = Destino.de(cdId, loja);
        if (d.cdId() != null) cds.buscarPorId(d.cdId()).orElseThrow(() -> new RecursoNaoEncontradoException("CD", d.cdId()));
        return d;
    }

    private String descricao(Destino d) {
        if (d.loja() != null) return d.loja().toLowerCase().startsWith("loja") ? "a " + d.loja() : "a loja " + d.loja();
        return "o CD " + cds.buscarPorId(d.cdId()).map(CentroDistribuicao::codigo).orElse(String.valueOf(d.cdId()));
    }

    private static String capitalizar(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) throw new RegraNegocioException("Informe início e fim do período");
        if (fim.isBefore(inicio)) throw new RegraNegocioException("Data final anterior à data inicial");
        if (ChronoUnit.DAYS.between(inicio, fim) > PERIODO_MAXIMO_DIAS) {
            throw new RegraNegocioException("Período máximo de " + PERIODO_MAXIMO_DIAS + " dias");
        }
    }
}
