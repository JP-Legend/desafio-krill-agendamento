package br.com.redekrill.agendamento.application.port;

import br.com.redekrill.agendamento.domain.model.Escopo;

/**
 * Escopo do usuário da requisição atual (preenchido pelo filtro de autenticação). Sem usuário (link público do
 * fornecedor, tarefas internas) vale {@link Escopo#TODOS}.
 */
public final class EscopoAtual {
    private static final ThreadLocal<Escopo> ATUAL = new ThreadLocal<>();

    private EscopoAtual() {
    }

    public static Escopo get() {
        Escopo e = ATUAL.get();
        return e == null ? Escopo.TODOS : e;
    }

    public static boolean restrito() {
        return !get().todos();
    }

    public static void definir(Escopo e) {
        if (e == null) ATUAL.remove(); else ATUAL.set(e);
    }

    public static void limpar() {
        ATUAL.remove();
    }

    /** Roda sem restrição (ex.: cálculo de capacidade do destino inteiro). */
    public static <T> T semRestricao(java.util.function.Supplier<T> s) {
        Escopo antes = ATUAL.get();
        ATUAL.remove();
        try {
            return s.get();
        } finally {
            if (antes != null) ATUAL.set(antes);
        }
    }
}
