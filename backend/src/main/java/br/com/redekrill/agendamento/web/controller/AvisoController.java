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
    private static final java.util.regex.Pattern AGENDAMENTO = java.util.regex.Pattern.compile("^/agendamentos/(\\d+)");
    private final AvisoUseCase useCase;
    private final br.com.redekrill.agendamento.infrastructure.persistence.jdbc.EscopoDestinos destinos;

    public AvisoController(AvisoUseCase useCase, br.com.redekrill.agendamento.infrastructure.persistence.jdbc.EscopoDestinos destinos) {
        this.useCase = useCase;
        this.destinos = destinos;
    }

    /** Usuário restrito: só avisos do CD/loja dele (pelo destino gravado ou pelo agendamento do link). Avisos gerais, todos. */
    private java.util.function.Predicate<br.com.redekrill.agendamento.domain.model.Aviso> visivel() {
        var e = br.com.redekrill.agendamento.application.port.EscopoAtual.get();
        if (e.todos()) return a -> true;
        return a -> {
            if (a == null) return false;
            if (a.loja() != null) return e.permiteLoja(a.loja());
            if (a.cdId() != null) return e.permiteCd(a.cdId());
            var m = a.url() == null ? null : AGENDAMENTO.matcher(a.url());
            if (m != null && m.find()) return destinos.agendamentoVisivel(e, Long.valueOf(m.group(1)));
            return a.url() == null || !a.url().startsWith("/painel");
        };
    }

    @GetMapping
    @Operation(summary = "Últimos avisos e quantos o usuário ainda não leu")
    public AvisoUseCase.Caixa caixa(HttpServletRequest req) {
        return useCase.caixa(Requisicao.solicitante(req).usuario(), visivel());
    }

    @PostMapping("/lidas")
    @Operation(summary = "Marca todos os avisos como lidos para o usuário logado")
    public AvisoUseCase.Caixa marcarLidas(HttpServletRequest req) {
        return useCase.marcarLidas(Requisicao.solicitante(req).usuario(), visivel());
    }
}
