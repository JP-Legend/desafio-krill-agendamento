package br.com.redekrill.agendamento.web.controller;

import br.com.redekrill.agendamento.application.usecase.LinkAgendamentoUseCase;
import br.com.redekrill.agendamento.application.usecase.LinkAgendamentoUseCase.LinkResumo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/** Tela "Links de agendamento" (permissão 1107): gerar, listar e revogar links públicos por CD ou loja. */
@RestController
@RequestMapping("/api/v1/links-agendamento")
@Tag(name = "Links de agendamento")
public class LinkAgendamentoController {

    private final LinkAgendamentoUseCase useCase;

    public LinkAgendamentoController(LinkAgendamentoUseCase useCase) {
        this.useCase = useCase;
    }

    public record GerarLink(Long cdId, @Size(max = 80) String loja) {
    }

    public record LinkResp(Long id, String destino, Long cdId, String loja, String url, boolean ativo, String criadoPor,
                           OffsetDateTime criadoEm, String revogadoPor, OffsetDateTime revogadoEm) {
        static LinkResp de(LinkResumo r) {
            var l = r.link();
            return new LinkResp(l.id(), r.destino(), l.cdId(), l.loja(), r.url(), l.ativo(), l.criadoPor(), l.criadoEm(),
                    l.revogadoPor(), l.revogadoEm());
        }
    }

    @GetMapping
    @Operation(summary = "Últimos 50 links (ativos primeiro)")
    public List<LinkResp> listar() {
        return useCase.listar().stream().map(LinkResp::de).toList();
    }

    @GetMapping("/destinos")
    @Operation(summary = "Destinos possíveis: todos os CDs, cada CD e cada loja")
    public List<LinkAgendamentoUseCase.Destino> destinos() {
        return useCase.destinos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gera ou regenera o link do destino (o link ativo do mesmo destino deixa de funcionar)")
    public LinkResp gerar(@Valid @RequestBody GerarLink b, HttpServletRequest req) {
        return LinkResp.de(useCase.gerar(b.cdId(), b.loja(), Requisicao.solicitante(req)));
    }

    @PatchMapping("/{id}/revogacao")
    @Operation(summary = "Revoga o link")
    public LinkResp revogar(@PathVariable Long id, HttpServletRequest req) {
        return LinkResp.de(useCase.revogar(id, Requisicao.solicitante(req)));
    }
}
