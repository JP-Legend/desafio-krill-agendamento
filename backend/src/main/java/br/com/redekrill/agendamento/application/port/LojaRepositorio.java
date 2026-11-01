package br.com.redekrill.agendamento.application.port;

import br.com.redekrill.agendamento.domain.model.Loja;

import java.util.List;
import java.util.Optional;

public interface LojaRepositorio {
    List<Loja> listar();

    Optional<Loja> buscarPorId(Long id);

    boolean existeNome(String nome, Long ignorarId);

    Loja salvar(Loja loja);
}
