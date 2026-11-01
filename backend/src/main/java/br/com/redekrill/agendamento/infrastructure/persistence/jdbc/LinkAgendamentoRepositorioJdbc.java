package br.com.redekrill.agendamento.infrastructure.persistence.jdbc;

import br.com.redekrill.agendamento.application.port.LinkAgendamentoRepositorio;
import br.com.redekrill.agendamento.domain.model.LinkAgendamento;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class LinkAgendamentoRepositorioJdbc implements LinkAgendamentoRepositorio {
    private static final RowMapper<LinkAgendamento> MAP = (r, i) -> new LinkAgendamento(r.getLong("id"),
            r.getString("token"), r.getObject("cd_id", Long.class), r.getString("loja"), r.getBoolean("ativo"),
            r.getString("criado_por"), r.getObject("criado_em", OffsetDateTime.class), r.getString("revogado_por"),
            r.getObject("revogado_em", OffsetDateTime.class));
    private final JdbcClient db;

    public LinkAgendamentoRepositorioJdbc(JdbcClient db) {
        this.db = db;
    }

    @Override
    public List<LinkAgendamento> listar() {
        return db.sql("select * from link_agendamento order by ativo desc, id desc limit 50").query(MAP).list();
    }

    @Override
    public Optional<LinkAgendamento> buscarPorId(Long id) {
        return db.sql("select * from link_agendamento where id = ?").param(id).query(MAP).optional();
    }

    @Override
    public Optional<LinkAgendamento> buscarAtivoPorToken(String token) {
        return db.sql("select * from link_agendamento where token = ? and ativo").param(token).query(MAP).optional();
    }

    @Override
    public void revogarAtivos(Long cdId, String loja, String usuario, OffsetDateTime quando) {
        db.sql("update link_agendamento set ativo = false, revogado_por = ?, revogado_em = ? where ativo "
                        + "and cd_id is not distinct from ? and lower(loja) is not distinct from lower(cast(? as varchar))")
                .params(usuario, quando, cdId, loja).update();
    }

    @Override
    public void revogar(Long id, String usuario, OffsetDateTime quando) {
        db.sql("update link_agendamento set ativo = false, revogado_por = ?, revogado_em = ? where id = ? and ativo")
                .params(usuario, quando, id).update();
    }

    @Override
    public LinkAgendamento salvar(LinkAgendamento l) {
        Long id = db.sql("insert into link_agendamento (token, cd_id, loja, ativo, criado_por, criado_em) "
                        + "values (?, ?, ?, ?, ?, ?) returning id")
                .params(l.token(), l.cdId(), l.loja(), l.ativo(), l.criadoPor(), l.criadoEm()).query(Long.class).single();
        return buscarPorId(id).orElseThrow();
    }
}
