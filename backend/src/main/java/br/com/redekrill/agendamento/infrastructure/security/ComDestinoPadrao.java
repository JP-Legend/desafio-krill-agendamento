package br.com.redekrill.agendamento.infrastructure.security;

import br.com.redekrill.agendamento.domain.model.Escopo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Telas de um destino só (ocupação, pátio, capacidade/calendário) sem cdId/loja na URL: para o usuário restrito
 * vale o primeiro destino dele, nunca o CD padrão de outro escopo.
 */
final class ComDestinoPadrao extends HttpServletRequestWrapper {
    private static final Set<String> CAMINHOS = Set.of("/api/v1/ocupacao", "/api/v1/ocupacao/periodo", "/api/v1/patio",
            "/api/v1/dashboard", "/api/v1/rotina", "/api/v1/capacidades-recebimento/ocupacao", "/api/v1/calendario-recebimento",
            "/api/v1/calendario-recebimento/dias", "/api/v1/docas", "/api/v1/conferentes");
    private final Map<String, String[]> params;

    private ComDestinoPadrao(HttpServletRequest req, String nome, String valor) {
        super(req);
        Map<String, String[]> p = new HashMap<>(req.getParameterMap());
        p.put(nome, new String[]{valor});
        this.params = Collections.unmodifiableMap(p);
    }

    /** Listas de agendamentos (e relatórios feitos delas) que o seletor de CD/loja da tela filtra. */
    private static final Set<String> LISTAS = Set.of("/api/v1/agendamentos", "/api/v1/divergencias",
            "/api/v1/divergencias.csv", "/api/v1/relatorios/descarga", "/api/v1/relatorios/descarga.csv", "/api/v1/rotina",
            "/api/v1/relatorios/agendamentos.csv", "/api/v1/dashboard", "/api/v1/vales-palete", "/api/v1/busca", "/api/v1/paletes/saldos",
            "/api/v1/paletes/cobrancas", "/api/v1/paletes/movimentos", "/api/v1/paletes/lojas");

    static boolean filtraLista(String uri) {
        return LISTAS.contains(uri);
    }

    static boolean precisa(String uri) {
        return CAMINHOS.contains(uri);
    }

    static HttpServletRequest de(HttpServletRequest req, Escopo e) {
        Long cd = e.primeiroCd();
        if (cd != null) return new ComDestinoPadrao(req, "cdId", String.valueOf(cd));
        String loja = e.primeiraLoja();
        if (loja == null) return req;
        // pátio, docas e conferentes são do CD: para a loja, a lista já vem filtrada pelos agendamentos dela
        if (req.getRequestURI().startsWith("/api/v1/docas")
                || req.getRequestURI().startsWith("/api/v1/conferentes") || req.getRequestURI().startsWith("/api/v1/rotina")
                || req.getRequestURI().startsWith("/api/v1/dashboard")) return req;
        return new ComDestinoPadrao(req, "loja", loja);
    }

    @Override
    public String getParameter(String name) {
        String[] v = params.get(name);
        return v == null || v.length == 0 ? null : v[0];
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return params;
    }

    @Override
    public java.util.Enumeration<String> getParameterNames() {
        return Collections.enumeration(params.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return params.get(name);
    }
}
