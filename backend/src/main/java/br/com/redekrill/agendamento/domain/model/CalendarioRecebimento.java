package br.com.redekrill.agendamento.domain.model;

import br.com.redekrill.agendamento.domain.exception.RegraNegocioException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Calendário de recebimento de um destino (CD ou loja): em quais dias da semana recebe mercadoria e se
 * recebe em feriado. {@code diasSemana} tem 7 posições, de segunda a domingo ('1' = recebe).
 * É regra rígida (o agendamento em dia fechado é recusado), diferente dos limites de capacidade, que só alertam.
 */
public record CalendarioRecebimento(Long id, Long cdId, String loja, String diasSemana, boolean recebeFeriado) {

    public static final String TODOS_OS_DIAS = "1111111";
    private static final String[] PLURAL = {"às segundas-feiras", "às terças-feiras", "às quartas-feiras",
            "às quintas-feiras", "às sextas-feiras", "aos sábados", "aos domingos"};

    public CalendarioRecebimento {
        loja = Textos.opcional(loja, "Loja", 80);
        if ((cdId == null) == (loja == null)) throw new RegraNegocioException("Informe o CD ou a loja (apenas um dos dois)");
        if (diasSemana == null || !diasSemana.matches("[01]{7}")) throw new RegraNegocioException("Dias da semana inválidos");
        if (!diasSemana.contains("1")) throw new RegraNegocioException("Marque ao menos um dia da semana para receber");
    }

    /** Sem calendário cadastrado o destino recebe todos os dias, inclusive feriados. */
    public static CalendarioRecebimento livre(Long cdId, String loja) {
        return new CalendarioRecebimento(null, cdId, loja, TODOS_OS_DIAS, true);
    }

    public boolean recebeNo(DayOfWeek dia) {
        return diasSemana.charAt(dia.getValue() - 1) == '1';
    }

    /** Situação do dia: exceção da data &gt; feriado (quando não recebe em feriado) &gt; dia da semana. */
    public DiaRecebimento situacao(LocalDate data, Map<LocalDate, String> feriados, Map<LocalDate, ExcecaoRecebimento> excecoes) {
        ExcecaoRecebimento e = excecoes.get(data);
        if (e != null) {
            String motivo = e.motivo() == null ? "" : ": " + e.motivo();
            return new DiaRecebimento(data, e.aberto(), (e.aberto() ? "Aberto excepcionalmente" : "Fechado") + motivo);
        }
        String feriado = feriados.get(data);
        if (feriado != null && !recebeFeriado) return new DiaRecebimento(data, false, "Feriado: " + feriado);
        if (!recebeNo(data.getDayOfWeek())) {
            return new DiaRecebimento(data, false, "Não recebe " + PLURAL[data.getDayOfWeek().getValue() - 1]);
        }
        return new DiaRecebimento(data, true, feriado == null ? null : "Feriado: " + feriado + " (recebe normalmente)");
    }
}
