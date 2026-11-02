package br.com.redekrill.agendamento.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Destinos (CDs e lojas) que o usuário enxerga. {@code todos} = sem restrição (padrão dos usuários existentes e do
 * Administrador). O destino de um agendamento é a loja quando é entrega direta; senão, o CD.
 */
public record Escopo(boolean todos, Set<Long> cds, Set<String> lojas) {
    public static final Escopo TODOS = new Escopo(true, Set.of(), Set.of());

    public Escopo {
        cds = cds == null ? Set.of() : Set.copyOf(cds);
        lojas = lojas == null ? Set.of() : Set.copyOf(lojas.stream().map(String::trim).toList());
    }

    public boolean permite(Long cdId, String loja) {
        if (todos) return true;
        if (loja != null && !loja.isBlank()) return lojas.contains(loja.trim());
        return cds.contains(cdId == null ? Agendamento.CD_PADRAO : cdId);
    }

    /** Registro com destino gravado (vale, cobrança): sem destino, só quem vê todos. */
    public boolean permiteDestino(Long cdId, String loja) {
        if (todos) return true;
        if (cdId == null && (loja == null || loja.isBlank())) return false;
        return permite(cdId, loja);
    }

    /** Movimento CD ↔ loja: vale para quem tem o CD ou a loja. */
    public boolean permiteCdOuLoja(Long cdId, String loja) {
        return todos || cds.contains(cdId) || permiteLoja(loja);
    }

    /** Destino único do usuário (null quando vê vários ou todos). */
    public Long unicoCd() {
        return !todos && cds.size() == 1 && lojas.isEmpty() ? cds.iterator().next() : null;
    }

    public String unicaLoja() {
        return !todos && lojas.size() == 1 && cds.isEmpty() ? lojas.iterator().next() : null;
    }

    public boolean permiteCd(Long cdId) {
        return todos || cds.contains(cdId);
    }

    public boolean permiteLoja(String loja) {
        return todos || (loja != null && lojas.contains(loja.trim()));
    }

    /** Primeiro destino (para telas sem filtro): CD tem preferência. */
    public Long primeiroCd() {
        return cds.stream().sorted().findFirst().orElse(null);
    }

    public String primeiraLoja() {
        return lojas.stream().sorted().findFirst().orElse(null);
    }

    public List<Object> resumo() {
        return List.of(todos, cds, lojas);
    }
}
