package br.com.redekrill.agendamento.web.controller;

import br.com.redekrill.agendamento.application.usecase.LojaUseCase;
import br.com.redekrill.agendamento.domain.model.Loja;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/lojas")
@Tag(name = "Cadastros do recebimento")
public class LojaController {
    private final LojaUseCase useCase;

    public LojaController(LojaUseCase useCase) {
        this.useCase = useCase;
    }

    public record LojaReq(@NotBlank(message = "Nome é obrigatório") @Size(max = 80) String nome, Long cdId, Boolean ativo) {
        Loja para(Long id) {
            return new Loja(id, nome, cdId, ativo == null || ativo);
        }
    }

    @GetMapping
    @Operation(summary = "Lojas (entrega direta e links de agendamento)")
    public List<Loja> listar() {
        var e = br.com.redekrill.agendamento.application.port.EscopoAtual.get();
        return useCase.listar().stream().filter(l -> e.permiteLoja(l.nome())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Loja criar(@Valid @RequestBody LojaReq b, HttpServletRequest req) {
        return useCase.salvar(b.para(null), Requisicao.solicitante(req));
    }

    @PutMapping("/{id}")
    public Loja alterar(@PathVariable Long id, @Valid @RequestBody LojaReq b, HttpServletRequest req) {
        return useCase.salvar(b.para(id), Requisicao.solicitante(req));
    }
}
