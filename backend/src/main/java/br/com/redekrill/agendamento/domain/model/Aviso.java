package br.com.redekrill.agendamento.domain.model;

import java.time.OffsetDateTime;

/** Aviso do sino do topo: gerado pelos eventos da operação (novo agendamento, chegada na portaria, capacidade). */
public record Aviso(Long id, String titulo, String mensagem, String url, OffsetDateTime criadoEm, Long cdId, String loja) {
    public Aviso(Long id, String titulo, String mensagem, String url, OffsetDateTime criadoEm) {
        this(id, titulo, mensagem, url, criadoEm, null, null);
    }

    public Aviso {
        titulo = corte(titulo, 80);
        mensagem = corte(mensagem, 300);
        url = url == null || url.isBlank() ? null : corte(url, 200);
    }

    private static String corte(String s, int max) {
        String v = s == null ? "" : s.strip();
        return v.length() > max ? v.substring(0, max - 1) + "…" : v;
    }
}
