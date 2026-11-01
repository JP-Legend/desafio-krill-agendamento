package br.com.redekrill.agendamento.infrastructure.persistence.jdbc;

import br.com.redekrill.agendamento.application.port.LojaRepositorio;
import br.com.redekrill.agendamento.domain.model.Loja;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class LojaRepositorioJdbc implements LojaRepositorio {
    private static final RowMapper<Loja> MAP = (r, i) -> new Loja(r.getLong("id"), r.getString("nome"),
            r.getObject("cd_id", Long.class), r.getBoolean("ativo"));
    private final JdbcClient db;

    public LojaRepositorioJdbc(JdbcClient db) {
        this.db = db;
    }

    @Override
    public List<Loja> listar() {
        return db.sql("select * from loja order by nome").query(MAP).list();
    }

    @Override
    public Optional<Loja> buscarPorId(Long id) {
        return db.sql("select * from loja where id = ?").param(id).query(MAP).optional();
    }

    @Override
    public boolean existeNome(String nome, Long ignorarId) {
        return db.sql("select count(*) from loja where lower(nome) = lower(?) and id <> coalesce(?, -1)")
                .params(nome, ignorarId).query(Long.class).single() > 0;
    }

    @Override
    public Loja salvar(Loja l) {
        Long id = l.id() == null
                ? db.sql("insert into loja (nome, cd_id, ativo) values (?, ?, ?) returning id")
                .params(l.nome(), l.cdId(), l.ativo()).query(Long.class).single()
                : db.sql("update loja set nome = ?, cd_id = ?, ativo = ? where id = ? returning id")
                .params(l.nome(), l.cdId(), l.ativo(), l.id()).query(Long.class).single();
        return buscarPorId(id).orElseThrow();
    }
}
