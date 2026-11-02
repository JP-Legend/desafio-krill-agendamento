package br.com.redekrill.agendamento.infrastructure.persistence.jdbc;

import br.com.redekrill.agendamento.application.port.BuscaRepositorio;
import br.com.redekrill.agendamento.application.usecase.BuscaGlobalUseCase.Resultado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class BuscaRepositorioJdbc implements BuscaRepositorio {
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final JdbcClient db;

    public BuscaRepositorioJdbc(JdbcClient db) {
        this.db = db;
    }

    private static String like(String termo) {
        return "%" + termo.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static String cnpj(String c) {
        if (c == null || c.length() != 14) return c;
        return c.substring(0, 2) + "." + c.substring(2, 5) + "." + c.substring(5, 8) + "/" + c.substring(8, 12) + "-" + c.substring(12);
    }

    @Override
    public List<Resultado> agendamentos(String termo, String digitos, LocalDate data, int limite) {
        String dig = digitos.length() >= 3 ? digitos : null;
        return db.sql("""
                select a.id, a.data, a.fornecedor, a.id_pedido, a.placa, a.status, f.hora_inicio, cd.codigo as cd
                from agendamento a
                join faixa_horaria f on f.id = a.faixa_horaria_id
                left join centro_distribuicao cd on cd.id = a.cd_id
                left join fornecedor fo on fo.id = a.fornecedor_id
                where lower(a.fornecedor) like :t escape '\\' or lower(coalesce(a.placa, '')) like :t escape '\\'
                   or (cast(:dig as text) is not null and (cast(a.id_pedido as text) like cast(:dl as text) or coalesce(fo.cnpj, '') like cast(:dl as text)
                       or replace(coalesce(a.placa, ''), '-', '') like cast(:dl as text)))
                   or (cast(:data as date) is not null and a.data = cast(:data as date))
                order by abs(a.data - current_date), a.id desc
                limit :lim""")
                .param("t", like(termo)).param("dig", dig).param("dl", dig == null ? null : "%" + dig + "%")
                .param("data", data).param("lim", limite)
                .query((r, i) -> new Resultado("Agendamentos", r.getString("fornecedor"),
                        r.getDate("data").toLocalDate().format(BR) + " " + r.getString("hora_inicio").substring(0, 5)
                                + " · Pedido " + r.getLong("id_pedido")
                                + (r.getString("placa") == null ? "" : " · " + r.getString("placa"))
                                + (r.getString("cd") == null ? "" : " · CD " + r.getString("cd")),
                        "/agendamentos/" + r.getLong("id"))).list();
    }

    @Override
    public List<Resultado> fornecedores(String termo, String digitos, int limite) {
        String dig = digitos.length() >= 3 ? "%" + digitos + "%" : null;
        return db.sql("""
                select id, cnpj, razao_social, nome_fantasia from fornecedor
                where lower(razao_social) like :t escape '\\' or lower(coalesce(nome_fantasia, '')) like :t escape '\\'
                   or (cast(:dig as text) is not null and cnpj like :dig)
                order by razao_social limit :lim""")
                .param("t", like(termo)).param("dig", dig).param("lim", limite)
                .query((r, i) -> new Resultado("Fornecedores", r.getString("razao_social"),
                        "CNPJ " + cnpj(r.getString("cnpj")) + (r.getString("nome_fantasia") == null ? "" : " · " + r.getString("nome_fantasia")),
                        "/cadastros/fornecedores?id=" + r.getLong("id"))).list();
    }

    @Override
    public List<Resultado> lojas(String termo, int limite) {
        return db.sql("""
                select l.id, l.nome, cd.codigo as cd from loja l left join centro_distribuicao cd on cd.id = l.cd_id
                where lower(l.nome) like :t escape '\\' order by l.nome limit :lim""")
                .param("t", like(termo)).param("lim", limite)
                .query((r, i) -> new Resultado("Lojas", r.getString("nome"),
                        r.getString("cd") == null ? "Loja" : "Abastecida pelo CD " + r.getString("cd"), "/cadastros/lojas?id=" + r.getLong("id"))).list();
    }

    @Override
    public List<Resultado> cds(String termo, int limite) {
        return db.sql("""
                select id, codigo, nome, cidade from centro_distribuicao
                where lower(codigo) like :t escape '\\' or lower(nome) like :t escape '\\' or lower(coalesce(cidade, '')) like :t escape '\\'
                order by codigo limit :lim""")
                .param("t", like(termo)).param("lim", limite)
                .query((r, i) -> new Resultado("Centros de distribuição", r.getString("codigo") + " · " + r.getString("nome"),
                        r.getString("cidade") == null ? "Centro de distribuição" : r.getString("cidade"), "/cadastros/cds?id=" + r.getLong("id"))).list();
    }

    @Override
    public List<Resultado> vales(String termo, LocalDate data, int limite) {
        return db.sql("""
                select id, codigo, data, fornecedor, placa from vale_palete
                where lower(codigo) like :t escape '\\' or lower(fornecedor) like :t escape '\\' or lower(coalesce(placa, '')) like :t escape '\\'
                   or (cast(:data as date) is not null and data = cast(:data as date))
                order by id desc limit :lim""")
                .param("t", like(termo)).param("data", data).param("lim", limite)
                .query((r, i) -> new Resultado("Vales-palete", r.getString("codigo"),
                        r.getDate("data").toLocalDate().format(BR) + " · " + r.getString("fornecedor")
                                + (r.getString("placa") == null ? "" : " · " + r.getString("placa")),
                        "/vales-palete/" + r.getLong("id"))).list();
    }
}
