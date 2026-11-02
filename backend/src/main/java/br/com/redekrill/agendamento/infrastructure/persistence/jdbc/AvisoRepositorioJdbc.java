package br.com.redekrill.agendamento.infrastructure.persistence.jdbc;

import br.com.redekrill.agendamento.application.port.AvisoRepositorio;
import br.com.redekrill.agendamento.domain.model.Aviso;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class AvisoRepositorioJdbc implements AvisoRepositorio {
    private static final RowMapper<Aviso> MAP = (r, i) -> new Aviso(r.getLong("id"), r.getString("titulo"),
            r.getString("mensagem"), r.getString("url"), r.getObject("criado_em", OffsetDateTime.class),
            (Long) r.getObject("cd_id"), r.getString("loja"));
    private final JdbcClient db;

    public AvisoRepositorioJdbc(JdbcClient db) {
        this.db = db;
    }

    @Override
    public Aviso registrar(Aviso a) {
        return db.sql("insert into aviso (titulo, mensagem, url, cd_id, loja) values (?, ?, ?, ?, ?) returning *")
                .params(a.titulo(), a.mensagem(), a.url(), a.cdId(), a.loja()).query(MAP).single();
    }

    @Override
    public List<Aviso> recentes(int limite) {
        return db.sql("select * from aviso order by id desc limit ?").param(limite).query(MAP).list();
    }

    @Override
    public long ultimoLido(String usuario) {
        return db.sql("select ultimo_lido_id from aviso_leitura where usuario = ?").param(usuario)
                .query(Long.class).optional().orElse(0L);
    }

    @Override
    public int contarDepoisDe(long id) {
        return db.sql("select count(*) from aviso where id > ?").param(id).query(Integer.class).single();
    }

    @Override
    public void marcarLido(String usuario, long ateId) {
        db.sql("""
                insert into aviso_leitura (usuario, ultimo_lido_id, lido_em) values (?, ?, now())
                on conflict (usuario) do update set ultimo_lido_id = greatest(aviso_leitura.ultimo_lido_id, excluded.ultimo_lido_id),
                    lido_em = now()""").params(usuario, ateId).update();
    }
}
