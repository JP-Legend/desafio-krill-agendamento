package br.com.redekrill.agendamento.application.dto;

import br.com.redekrill.agendamento.domain.model.Agendamento;

/** Agendamento com as descrições dos cadastros relacionados, pronto para exibição/e-mail. */
public record AgendamentoDetalhe(Agendamento agendamento, String faixaDescricao, String tipoCaminhao,
                                 String tipoPaletizacao, String destino) {
    public AgendamentoDetalhe(Agendamento agendamento, String faixaDescricao, String tipoCaminhao, String tipoPaletizacao) {
        this(agendamento, faixaDescricao, tipoCaminhao, tipoPaletizacao, null);
    }

    /** "no CD 806 · Bertioga" ou "na Loja 14 - KRILL BERTIOGA", para "Sua entrega …". */
    public String entregaEm() {
        if (destino == null || destino.isBlank()) return "no CD";
        return (destino.startsWith("Loja") ? "na " : "no ") + destino;
    }
}
