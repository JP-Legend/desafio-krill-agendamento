package br.com.redekrill.agendamento.web.controller;

import br.com.redekrill.agendamento.application.usecase.ValePaleteUseCase;
import br.com.redekrill.agendamento.domain.model.ValePalete;
import br.com.redekrill.agendamento.web.dto.Requisicoes;
import br.com.redekrill.agendamento.web.dto.Respostas.ValePaleteResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vales-palete")
@Tag(name = "Vale-palete")
public class ValePaleteController {

    private final ValePaleteUseCase useCase;

    @org.springframework.beans.factory.annotation.Value("${app.empresa-codigo:KRILL}")
    private String empresa = "KRILL";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private br.com.redekrill.agendamento.infrastructure.persistence.jdbc.EscopoDestinos destinos;

    public ValePaleteController(ValePaleteUseCase useCase) {
        this.useCase = useCase;
    }

    /** Vale de outro CD/loja: 404 (o usuário só vê os vales dos agendamentos do escopo dele). */
    private ValePaleteResp visivel(ValePaleteResp v) {
        var e = br.com.redekrill.agendamento.application.port.EscopoAtual.get();
        if (e.todos() || destinos == null) return v;
        if (v.agendamentoId() == null || !destinos.agendamentoVisivel(e, v.agendamentoId()))
            throw new br.com.redekrill.agendamento.domain.exception.RecursoNaoEncontradoException("Vale-palete", v.codigo());
        return v;
    }

    @GetMapping
    public List<ValePaleteResp> listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                       @RequestParam(required = false) ValePalete.Status status,
                                       @RequestParam(required = false) String fornecedor) {
        var lista = useCase.listar(inicio, fim, status, fornecedor).stream().map(this::com).toList();
        var e = br.com.redekrill.agendamento.application.port.EscopoAtual.get();
        if (e.todos() || destinos == null) return lista;
        var ok = destinos.visiveis(e, lista.stream().map(ValePaleteResp::agendamentoId).filter(java.util.Objects::nonNull).toList());
        return lista.stream().filter(v -> v.agendamentoId() != null && ok.contains(v.agendamentoId())).toList();
    }

    @GetMapping("/{id}")
    public ValePaleteResp buscar(@PathVariable Long id) {
        return com(useCase.buscar(id));
    }

    @GetMapping("/leitura")
    @io.swagger.v3.oas.annotations.Operation(summary = "Abre o vale pela leitura do código de barras (EMPRESA-VP-...) ou pelo código digitado")
    public ValePaleteResp leitura(@RequestParam String codigo) {
        String c = br.com.redekrill.agendamento.domain.model.CodigoLeituraVale.interpretar(codigo, empresa);
        if (c == null) throw new br.com.redekrill.agendamento.domain.exception.RegraNegocioException("Código de vale-palete inválido");
        return visivel(com(useCase.buscarPorCodigo(c)));
    }

    @GetMapping("/codigo/{codigo}")
    @Operation(summary = "Busca pelo código VP-... (leitura do código de barras/QR)")
    public ValePaleteResp porCodigo(@PathVariable String codigo) {
        return visivel(com(useCase.buscarPorCodigo(codigo)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ValePaleteResp emitir(@Valid @RequestBody Requisicoes.NovoVale b, HttpServletRequest req) {
        return com(useCase.emitir(new ValePaleteUseCase.NovoVale(b.agendamentoId(), b.data(), b.fornecedor(),
                b.placa(), b.motorista(), new ValePalete.Quantidades(b.quantidades()),
                b.observacao()), Requisicao.solicitante(req)));
    }

    @PutMapping("/{id}/assinaturas/{quem}")
    @Operation(summary = "Grava assinatura do MOTORISTA ou CONFERENTE")
    public ValePaleteResp assinar(@PathVariable Long id, @PathVariable ValePalete.Assinante quem,
                                  @Valid @RequestBody Requisicoes.Assinatura b, HttpServletRequest req) {
        return com(useCase.assinar(id, quem, b.nome(), b.imagem(), Requisicao.solicitante(req)));
    }

    @DeleteMapping("/{id}/assinaturas/{quem}")
    @Operation(summary = "Refazer assinatura")
    public ValePaleteResp refazer(@PathVariable Long id, @PathVariable ValePalete.Assinante quem, HttpServletRequest req) {
        return com(useCase.refazerAssinatura(id, quem, Requisicao.solicitante(req)));
    }

    @PostMapping("/{id}/devolucoes")
    @Operation(summary = "Registra devolução de paletes (total quita o vale)")
    public ValePaleteResp devolver(@PathVariable Long id, @Valid @RequestBody Requisicoes.Devolucao b, HttpServletRequest req) {
        return com(useCase.registrarDevolucao(id,
                new ValePalete.Quantidades(b.quantidades()), Requisicao.solicitante(req)));
    }

    @PatchMapping("/{id}/cancelamento")
    public ValePaleteResp cancelar(@PathVariable Long id, HttpServletRequest req) {
        return com(useCase.cancelar(id, Requisicao.solicitante(req)));
    }

    private ValePaleteResp com(ValePalete v) {
        return ValePaleteResp.de(v).comLeitura(br.com.redekrill.agendamento.domain.model.CodigoLeituraVale.gerar(empresa, v.getCodigo()));
    }
}
