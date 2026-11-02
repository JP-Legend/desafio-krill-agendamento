package br.com.redekrill.agendamento.infrastructure.push;

import br.com.redekrill.agendamento.application.port.NotificadorPush;
import br.com.redekrill.agendamento.application.usecase.AvisoUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Todo evento que avisa a operação grava o aviso do sino (persistido) e depois dispara o Web Push
 * para os navegadores inscritos. Assim o sino mostra o mesmo que chegou no celular.
 */
@Primary
@Component
public class NotificadorCentral implements NotificadorPush {

    private static final Logger log = LoggerFactory.getLogger(NotificadorCentral.class);

    private final AvisoUseCase avisos;
    private final NotificadorWebPush webPush;

    public NotificadorCentral(AvisoUseCase avisos, NotificadorWebPush webPush) {
        this.avisos = avisos;
        this.webPush = webPush;
    }

    @Override
    public void notificarTodos(String titulo, String mensagem, String url) {
        try {
            avisos.registrar(titulo, mensagem, url);
        } catch (RuntimeException e) {
            log.warn("Falha ao gravar aviso do sino: {}", e.getMessage());
        }
        webPush.notificarTodos(titulo, mensagem, url);
    }
}
