package br.com.redekrill.agendamento.web.controller;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite de requisições por IP nas rotas públicas (como o agLimite do Gestão CD): janela deslizante em memória.
 * Envios: 8 a cada 10 minutos; consultas: 60 por minuto.
 */
@Component
public class LimitadorPublico {

    static final int ENVIOS = 8;
    static final long JANELA_ENVIOS_MS = 10 * 60_000L;
    static final int CONSULTAS = 60;
    static final long JANELA_CONSULTAS_MS = 60_000L;

    private final Map<String, Deque<Long>> marcas = new ConcurrentHashMap<>();
    private final Clock clock;

    public LimitadorPublico(Clock clock) {
        this.clock = clock;
    }

    public void envio(String ip) {
        verificar("envio:" + ip, ENVIOS, JANELA_ENVIOS_MS, "Muitos envios em pouco tempo. Aguarde alguns minutos e tente novamente.");
    }

    public void consulta(String ip) {
        verificar("consulta:" + ip, CONSULTAS, JANELA_CONSULTAS_MS, "Muitas consultas em pouco tempo. Aguarde um minuto.");
    }

    private void verificar(String chave, int maximo, long janela, String mensagem) {
        long agora = clock.millis();
        if (marcas.size() > 10_000) marcas.clear();
        Deque<Long> fila = marcas.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && agora - fila.peekFirst() > janela) fila.pollFirst();
            if (fila.size() >= maximo) throw new LimiteExcedidoException(mensagem);
            fila.addLast(agora);
        }
    }

    public static class LimiteExcedidoException extends RuntimeException {
        public LimiteExcedidoException(String mensagem) {
            super(mensagem);
        }
    }
}
