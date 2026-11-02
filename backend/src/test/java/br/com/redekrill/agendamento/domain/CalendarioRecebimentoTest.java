package br.com.redekrill.agendamento.domain;

import br.com.redekrill.agendamento.domain.exception.RegraNegocioException;
import br.com.redekrill.agendamento.domain.model.CalendarioRecebimento;
import br.com.redekrill.agendamento.domain.model.CapacidadeRecebimento;
import br.com.redekrill.agendamento.domain.model.DiaRecebimento;
import br.com.redekrill.agendamento.domain.model.ExcecaoRecebimento;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarioRecebimentoTest {

    static final LocalDate FINADOS = LocalDate.of(2026, 11, 2); // segunda-feira
    static final LocalDate SABADO = LocalDate.of(2026, 11, 7);
    static final Map<LocalDate, String> FERIADOS = Map.of(FINADOS, "Finados");

    @Test
    void semCalendarioRecebeTodosOsDiasInclusiveFeriado() {
        CalendarioRecebimento livre = CalendarioRecebimento.livre(1L, null);
        assertThat(livre.situacao(FINADOS, FERIADOS, Map.of()).aberto()).isTrue();
        assertThat(livre.situacao(FINADOS, FERIADOS, Map.of()).motivo()).isEqualTo("Feriado: Finados (recebe normalmente)");
        assertThat(livre.situacao(SABADO.plusDays(1), FERIADOS, Map.of()).aberto()).isTrue();
    }

    @Test
    void diaDaSemanaEFeriadoFecham() {
        CalendarioRecebimento c = new CalendarioRecebimento(1L, null, "Loja 12", "1111100", false);
        assertThat(c.situacao(SABADO, FERIADOS, Map.of())).isEqualTo(new DiaRecebimento(SABADO, false, "Não recebe aos sábados"));
        assertThat(c.situacao(SABADO.plusDays(1), FERIADOS, Map.of()).motivo()).isEqualTo("Não recebe aos domingos");
        assertThat(c.situacao(FINADOS, FERIADOS, Map.of())).isEqualTo(new DiaRecebimento(FINADOS, false, "Feriado: Finados"));
        assertThat(c.situacao(FINADOS.plusDays(1), FERIADOS, Map.of()).aberto()).isTrue();
    }

    @Test
    void recebeEmFeriadoQuandoMarcado() {
        CalendarioRecebimento c = new CalendarioRecebimento(1L, 2L, null, "1111110", true);
        assertThat(c.situacao(FINADOS, FERIADOS, Map.of()).aberto()).isTrue();
    }

    @Test
    void excecaoTemPrioridade() {
        CalendarioRecebimento c = new CalendarioRecebimento(1L, 2L, null, "1111100", false);
        var abre = Map.of(SABADO, new ExcecaoRecebimento(1L, 2L, null, SABADO, true, "Mutirão"));
        assertThat(c.situacao(SABADO, FERIADOS, abre)).isEqualTo(new DiaRecebimento(SABADO, true, "Aberto excepcionalmente: Mutirão"));
        var fecha = Map.of(FINADOS.plusDays(1), new ExcecaoRecebimento(2L, 2L, null, FINADOS.plusDays(1), false, "Inventário"));
        assertThat(c.situacao(FINADOS.plusDays(1), FERIADOS, fecha).motivo()).isEqualTo("Fechado: Inventário");
        var feriadoAberto = Map.of(FINADOS, new ExcecaoRecebimento(3L, 2L, null, FINADOS, true, null));
        assertThat(c.situacao(FINADOS, FERIADOS, feriadoAberto).aberto()).isTrue();
    }

    @Test
    void validacoes() {
        assertThatThrownBy(() -> new CalendarioRecebimento(null, 1L, null, "0000000", false))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("ao menos um dia");
        assertThatThrownBy(() -> new CalendarioRecebimento(null, 1L, null, "11111", false)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new CalendarioRecebimento(null, 1L, "Loja 1", "1111111", false)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new CapacidadeRecebimento(null, null, "Loja 1", null, null, null, 30))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("só para CD");
        assertThat(new CapacidadeRecebimento(null, 1L, null, null, null, null, 30).paletesFaixa()).isEqualTo(30);
    }

    @Test
    void codigoDeBarrasDoValeIdentificaAEmpresaEValeParaValesAntigos() {
        var C = br.com.redekrill.agendamento.domain.model.CodigoLeituraVale.class;
        assertThat(br.com.redekrill.agendamento.domain.model.CodigoLeituraVale.gerar("krill", "VP-20260115-0007")).isEqualTo("KRILL-VP-20260115-0007");
        assertThat(br.com.redekrill.agendamento.domain.model.CodigoLeituraVale.interpretar(" krill-vp-20260115-0007 ", "KRILL")).isEqualTo("VP-20260115-0007");
        assertThat(br.com.redekrill.agendamento.domain.model.CodigoLeituraVale.interpretar("VP-20260115-0007", "KRILL")).isEqualTo("VP-20260115-0007");
        assertThat(br.com.redekrill.agendamento.domain.model.CodigoLeituraVale.interpretar("Pedido 123", "KRILL")).isNull();
        assertThatThrownBy(() -> br.com.redekrill.agendamento.domain.model.CodigoLeituraVale.interpretar("OUTRA-VP-20260115-0007", "KRILL"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("outra empresa");
        assertThat(C).isNotNull();
    }
}
