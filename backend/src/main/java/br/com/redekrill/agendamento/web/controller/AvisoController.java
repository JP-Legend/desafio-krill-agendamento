package br.com.redekrill.agendamento.web.controller;

import br.com.redekrill.agendamento.application.usecase.AvisoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/avisos")
@Tag(name = "Avisos", description = "Sino de notificações do topo")
public class AvisoController {
    private final AvisoUseCase useCase;

    public AvisoController(AvisoUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @Operation(summary = "Últimos avisos e quantos o usuário ainda não leu")
    public AvisoUseCase.Caixa caixa(HttpServletRequest req) {
        return useCase.caixa(Requisicao.solicitante(req).usuario());
    }

    @PostMapping("/lidas")
    @Operation(summary = "Marca todos os avisos como lidos para o usuário logado")
    public AvisoUseCase.Caixa marcarLidas(HttpServletRequest req) {
        return useCase.marcarLidas(Requisicao.solicitante(req).usuario());
    }
}
