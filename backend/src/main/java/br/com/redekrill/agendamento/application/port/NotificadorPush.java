package br.com.redekrill.agendamento.application.port;

public interface NotificadorPush {
    void notificarTodos(String titulo, String mensagem, String url);

    /** Aviso de um destino: só quem enxerga aquele CD (loja nula) ou aquela loja recebe. */
    default void notificarDestino(String titulo, String mensagem, String url, Long cdId, String loja) {
        notificarTodos(titulo, mensagem, url);
    }
}
