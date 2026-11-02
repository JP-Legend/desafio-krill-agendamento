package br.com.redekrill.agendamento.application.port;

import br.com.redekrill.agendamento.domain.model.UsuarioSistema;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepositorio {
    List<UsuarioSistema> listar();

    Optional<UsuarioSistema> buscarPorId(Long id);

    Optional<UsuarioSistema> buscarPorLogin(String login);

    UsuarioSistema salvar(UsuarioSistema u);

    void registrarAcesso(Long id, Instant quando);

    /** Destinos (CDs/lojas) do usuário; usuários sem restrição devolvem Escopo.TODOS. */
    default br.com.redekrill.agendamento.domain.model.Escopo escopo(Long usuarioId) {
        return br.com.redekrill.agendamento.domain.model.Escopo.TODOS;
    }

    default void salvarEscopo(Long usuarioId, br.com.redekrill.agendamento.domain.model.Escopo escopo) {
    }
}
