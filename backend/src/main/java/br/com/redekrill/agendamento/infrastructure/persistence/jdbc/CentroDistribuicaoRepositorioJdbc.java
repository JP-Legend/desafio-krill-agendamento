package br.com.redekrill.agendamento.infrastructure.persistence.jdbc;

import br.com.redekrill.agendamento.application.port.CentroDistribuicaoRepositorio;
import br.com.redekrill.agendamento.domain.model.CentroDistribuicao;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CentroDistribuicaoRepositorioJdbc implements CentroDistribuicaoRepositorio {
    private static final RowMapper<CentroDistribuicao> MAP = (r, i) -> new CentroDistribuicao(r.getLong("id"),
            r.getString("codigo"), r.getString("nome"), r.getString("cidade"), r.getObject("capacidade_diaria_paletes", Integer.class),
            r.getObject("capacidade_faixa_paletes", Integer.class), r.getBoolean("ativo"));
    /** Os limites do CD vêm da regra geral (todos os dias) da capacidade de recebimento: um único lugar de configuração. */
    private static final String SELECT = "select cd.*, c.paletes as capacidade_diaria_paletes, c.paletes_faixa as capacidade_faixa_paletes "
            + "from centro_distribuicao cd left join capacidade_recebimento c on c.cd_id = cd.id and c.dia_semana is null ";
    private final JdbcClient db;

    public CentroDistribuicaoRepositorioJdbc(JdbcClient db) {
        this.db = db;
    }

    @Override
    public List<CentroDistribuicao> listar() {
        return db.sql(SELECT + "order by cd.id").query(MAP).list();
    }

    @Override
    public Optional<CentroDistribuicao> buscarPorId(Long id) {
        return db.sql(SELECT + "where cd.id = ?").param(id).query(MAP).optional();
    }

    @Override
    public boolean existeCodigo(String codigo, Long ignorarId) {
        return db.sql("select count(*) from centro_distribuicao where lower(codigo) = lower(?) and id <> coalesce(?, -1)")
                .params(codigo, ignorarId).query(Long.class).single() > 0;
    }

    @Override
    public CentroDistribuicao salvar(CentroDistribuicao c) {
        Long id = c.id() == null
                ? db.sql("insert into centro_distribuicao (codigo, nome, cidade, ativo) values (?, ?, ?, ?) returning id")
                .params(c.codigo(), c.nome(), c.cidade(), c.ativo())
                .query(Long.class).single()
                : db.sql("update centro_distribuicao set codigo = ?, nome = ?, cidade = ?, ativo = ? where id = ? returning id")
                .params(c.codigo(), c.nome(), c.cidade(), c.ativo(), c.id())
                .query(Long.class).single();
        return buscarPorId(id).orElseThrow();
    }
}
