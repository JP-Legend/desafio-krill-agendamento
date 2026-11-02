package br.com.redekrill.agendamento.infrastructure.persistence.jdbc;

import br.com.redekrill.agendamento.domain.model.Agendamento;
import br.com.redekrill.agendamento.domain.model.Escopo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decide, no banco, se um agendamento (e o que pendura nele: vale, nota) é do escopo do usuário. */
@Component
public class EscopoDestinos {
    private static final Pattern AGENDAMENTO = Pattern.compile("^/api/v1/(?:notificacoes/)?agendamentos/(\\d+)(?:/.*)?$");
    private static final Pattern VALE = Pattern.compile("^/api/v1/vales-palete/(\\d+)(?:/.*)?$");
    private static final Pattern NOTA = Pattern.compile("^/api/v1/notas/(\\d+)$");
    private final JdbcClient db;

    public EscopoDestinos(JdbcClient db) {
        this.db = db;
    }

    /** Ids de agendamento dentre os informados que o escopo enxerga. */
    public Set<Long> visiveis(Escopo e, Collection<Long> ids) {
        if (e.todos() || ids.isEmpty()) return new HashSet<>(ids);
        Set<Long> ok = new HashSet<>();
        db.sql("select a.id, a.cd_id, l.loja from agendamento a left join agendamento_logistica l on l.agendamento_id = a.id where a.id in (:ids)")
                .param("ids", List.copyOf(ids)).query((r, i) -> {
                    long cd = r.getLong("cd_id");
                    Long cdId = r.wasNull() ? Agendamento.CD_PADRAO : cd;
                    if (e.permite(cdId, r.getString("loja"))) ok.add(r.getLong("id"));
                    return 0;
                }).list();
        return ok;
    }

    public boolean agendamentoVisivel(Escopo e, Long id) {
        return id != null && visiveis(e, List.of(id)).contains(id);
    }

    /** Vale sem agendamento só aparece para quem vê todos os destinos. */
    public boolean valeVisivel(Escopo e, Long valeId) {
        if (e.todos()) return true;
        return db.sql("select cd_id, loja from vale_palete where id = ?").param(valeId)
                .query((r, i) -> e.permiteDestino((Long) r.getObject("cd_id"), r.getString("loja"))).optional().orElse(false);
    }

    /** Recurso por id na URL: false = fora do escopo (responde 404). */
    public boolean urlPermitida(Escopo e, String uri) {
        if (e.todos()) return true;
        Matcher m = AGENDAMENTO.matcher(uri);
        if (m.matches()) return agendamentoVisivel(e, Long.valueOf(m.group(1)));
        m = VALE.matcher(uri);
        if (m.matches()) return valeVisivel(e, Long.valueOf(m.group(1)));
        m = NOTA.matcher(uri);
        if (m.matches()) {
            Optional<Long> ag = db.sql("select agendamento_id from nota_fiscal where id = ?").param(Long.valueOf(m.group(1))).query(Long.class).optional();
            return ag.isPresent() && agendamentoVisivel(e, ag.get());
        }
        return true;
    }

    public Map<String, Object> resumo(Escopo e) {
        return Map.of("cds", e.cds(), "lojas", e.lojas());
    }
}
