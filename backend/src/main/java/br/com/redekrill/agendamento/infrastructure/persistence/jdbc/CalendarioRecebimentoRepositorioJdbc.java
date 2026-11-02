package br.com.redekrill.agendamento.infrastructure.persistence.jdbc;

import br.com.redekrill.agendamento.application.port.CalendarioRecebimentoRepositorio;
import br.com.redekrill.agendamento.domain.model.CalendarioRecebimento;
import br.com.redekrill.agendamento.domain.model.ExcecaoRecebimento;
import br.com.redekrill.agendamento.domain.model.Feriado;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class CalendarioRecebimentoRepositorioJdbc implements CalendarioRecebimentoRepositorio {
    private static final String DESTINO = "coalesce(cd_id, 0) = coalesce(cast(? as bigint), 0) and lower(coalesce(loja, '')) = lower(coalesce(cast(? as varchar), ''))";
    private static final RowMapper<CalendarioRecebimento> CAL = (r, i) -> new CalendarioRecebimento(r.getLong("id"),
            r.getObject("cd_id", Long.class), r.getString("loja"), r.getString("dias_semana"), r.getBoolean("recebe_feriado"));
    private static final RowMapper<ExcecaoRecebimento> EXC = (r, i) -> new ExcecaoRecebimento(r.getLong("id"),
            r.getObject("cd_id", Long.class), r.getString("loja"), r.getObject("data", LocalDate.class), r.getBoolean("aberto"),
            r.getString("motivo"));
    private final JdbcClient db;

    public CalendarioRecebimentoRepositorioJdbc(JdbcClient db) {
        this.db = db;
    }

    @Override
    public Optional<CalendarioRecebimento> buscar(Long cdId, String loja) {
        return db.sql("select * from calendario_recebimento where " + DESTINO).params(cdId, loja).query(CAL).optional();
    }

    @Override
    public List<CalendarioRecebimento> listar() {
        return db.sql("select * from calendario_recebimento order by loja nulls first, cd_id").query(CAL).list();
    }

    @Override
    public CalendarioRecebimento salvar(CalendarioRecebimento c) {
        Long id = buscar(c.cdId(), c.loja()).map(CalendarioRecebimento::id).orElse(null);
        Long salvo = id == null
                ? db.sql("insert into calendario_recebimento (cd_id, loja, dias_semana, recebe_feriado) values (?, ?, ?, ?) returning id")
                .params(c.cdId(), c.loja(), c.diasSemana(), c.recebeFeriado()).query(Long.class).single()
                : db.sql("update calendario_recebimento set dias_semana = ?, recebe_feriado = ? where id = ? returning id")
                .params(c.diasSemana(), c.recebeFeriado(), id).query(Long.class).single();
        return db.sql("select * from calendario_recebimento where id = ?").param(salvo).query(CAL).single();
    }

    @Override
    public List<ExcecaoRecebimento> excecoes(Long cdId, String loja, LocalDate inicio, LocalDate fim) {
        return db.sql("select * from excecao_recebimento where " + DESTINO + " and data between ? and ? order by data")
                .params(cdId, loja, inicio, fim).query(EXC).list();
    }

    @Override
    public Optional<ExcecaoRecebimento> buscarExcecao(Long id) {
        return db.sql("select * from excecao_recebimento where id = ?").param(id).query(EXC).optional();
    }

    @Override
    public ExcecaoRecebimento salvarExcecao(ExcecaoRecebimento e) {
        Long id = db.sql("insert into excecao_recebimento (cd_id, loja, data, aberto, motivo) values (?, ?, ?, ?, ?) "
                        + "on conflict (coalesce(cd_id, 0), lower(coalesce(loja, '')), data) do update set aberto = excluded.aberto, motivo = excluded.motivo "
                        + "returning id")
                .params(e.cdId(), e.loja(), e.data(), e.aberto(), e.motivo()).query(Long.class).single();
        return buscarExcecao(id).orElseThrow();
    }

    @Override
    public void excluirExcecao(Long id) {
        db.sql("delete from excecao_recebimento where id = ?").param(id).update();
    }

    @Override
    public List<Feriado> feriados(LocalDate inicio, LocalDate fim) {
        return db.sql("select data, nome from feriado where data between ? and ? order by data").params(inicio, fim)
                .query((r, i) -> new Feriado(r.getObject("data", LocalDate.class), r.getString("nome"))).list();
    }
}
