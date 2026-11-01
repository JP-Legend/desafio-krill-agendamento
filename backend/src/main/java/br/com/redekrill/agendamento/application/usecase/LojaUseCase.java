package br.com.redekrill.agendamento.application.usecase;

import br.com.redekrill.agendamento.application.dto.Solicitante;
import br.com.redekrill.agendamento.application.port.LojaRepositorio;
import br.com.redekrill.agendamento.application.port.Transacao;
import br.com.redekrill.agendamento.domain.exception.RecursoNaoEncontradoException;
import br.com.redekrill.agendamento.domain.exception.RegraNegocioException;
import br.com.redekrill.agendamento.domain.model.Loja;

import java.util.List;
import java.util.Map;

/** Cadastro mínimo de lojas (destino de entrega direta e de link de agendamento). */
public class LojaUseCase {
    private final LojaRepositorio lojas;
    private final AuditoriaUseCase auditoria;
    private final Transacao transacao;

    public LojaUseCase(LojaRepositorio lojas, AuditoriaUseCase auditoria, Transacao transacao) {
        this.lojas = lojas;
        this.auditoria = auditoria;
        this.transacao = transacao;
    }

    public List<Loja> listar() {
        return lojas.listar();
    }

    public Loja salvar(Loja loja, Solicitante s) {
        return transacao.executar(() -> {
            if (loja.id() != null) lojas.buscarPorId(loja.id()).orElseThrow(() -> new RecursoNaoEncontradoException("Loja", loja.id()));
            if (lojas.existeNome(loja.nome(), loja.id())) throw new RegraNegocioException("Já existe loja com o nome " + loja.nome());
            Loja salva = lojas.salvar(loja);
            auditoria.registrar(s, loja.id() == null ? "CRIAR" : "ALTERAR", "LOJA", salva.id(), Map.of("nome", salva.nome()));
            return salva;
        });
    }
}
