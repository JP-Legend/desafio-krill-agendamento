package br.com.redekrill.agendamento.application.port;

import br.com.redekrill.agendamento.domain.model.Aviso;

import java.util.List;

/** Avisos do sino e a posição de leitura de cada usuário (último aviso lido). */
public interface AvisoRepositorio {
    Aviso registrar(Aviso aviso);

    List<Aviso> recentes(int limite);

    long ultimoLido(String usuario);

    int contarDepoisDe(long id);

    void marcarLido(String usuario, long ateId);
}
