package br.com.redekrill.agendamento.domain.model;

/** Loja que recebe entrega direta (destino de link de agendamento, capacidade e romaneios de paletes). */
public record Loja(Long id, String nome, Long cdId, boolean ativo) {
    public Loja {
        nome = Textos.obrigatorio(nome, "Nome da loja", 80);
    }
}
