package br.com.redekrill.agendamento.web.controller;

import br.com.redekrill.agendamento.application.usecase.LinkAgendamentoUseCase;
import br.com.redekrill.agendamento.application.usecase.LinkAgendamentoUseCase.PedidoPublico;
import br.com.redekrill.agendamento.web.dto.Respostas.OcupacaoDiaResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Formulário público do link de agendamento (/agendar/{token}): sem login, limitado por IP, e só expõe
 * a ocupação agregada do CD. Nunca lista agendamentos nem fornecedores.
 */
@RestController
@RequestMapping("/api/v1/publico/agendar/{token}")
@Tag(name = "Link público de agendamento")
@SecurityRequirements
public class AgendarPublicoController {

    private final LinkAgendamentoUseCase useCase;
    private final LimitadorPublico limitador;

    public AgendarPublicoController(LinkAgendamentoUseCase useCase, LimitadorPublico limitador) {
        this.useCase = useCase;
        this.limitador = limitador;
    }

    public record Opcao(Long id, String nome) {
    }

    public record FormularioResp(String destino, String loja, List<Opcao> cds, List<Opcao> tiposCaminhao,
                                 List<Opcao> tiposPaletizacao) {
    }

    public record Agendar(Long cdId,
                          @NotNull(message = "Data é obrigatória") LocalDate data,
                          @NotNull(message = "Faixa horária é obrigatória") Long faixaHorariaId,
                          @NotNull(message = "Nº do pedido é obrigatório") @Positive(message = "Nº do pedido deve ser positivo") Long idPedido,
                          @NotBlank(message = "Fornecedor é obrigatório") @Size(max = 80) String fornecedor,
                          @Size(max = 18) String cnpj,
                          @NotBlank(message = "E-mail é obrigatório") @Size(max = 80) @Email(message = "E-mail inválido") String emailFornecedor,
                          @NotNull(message = "Tipo de caminhão é obrigatório") Long tipoCaminhaoId,
                          @NotNull(message = "Tipo de paletização é obrigatório") Long tipoPaletizacaoId,
                          @NotNull(message = "Quantidade de paletes é obrigatória") @Min(value = 1, message = "Quantidade de paletes deve ser maior que zero")
                          @Max(10000) Integer quantidadePaletes,
                          @Size(max = 80) String transportadora,
                          @Size(max = 500) String observacao,
                          String siteWeb) {
    }

    public record AgendadoResp(Long id, LocalDate data, String faixaHoraria, String destino, int paletesEquivalentes,
                               List<String> alertas, String linkFornecedor) {
    }

    @GetMapping
    @Operation(summary = "Destino do link e opções do formulário")
    public FormularioResp formulario(@PathVariable String token, HttpServletRequest req) {
        limitador.consulta(Requisicao.ip(req));
        var f = useCase.formulario(token);
        return new FormularioResp(f.destino(), f.loja(),
                f.cds().stream().map(c -> new Opcao(c.id(), "CD " + c.codigo() + " · " + c.nome())).toList(),
                f.caminhoes().stream().map(c -> new Opcao(c.id(), c.nome())).toList(),
                f.paletizacoes().stream().map(p -> new Opcao(p.id(), p.nome())).toList());
    }

    @GetMapping("/disponibilidade")
    @Operation(summary = "Ocupação agregada dos próximos 14 dias (paletes livres por faixa)")
    public List<OcupacaoDiaResp> disponibilidade(@PathVariable String token, @RequestParam(required = false) Long cdId,
                                                 HttpServletRequest req) {
        limitador.consulta(Requisicao.ip(req));
        return useCase.disponibilidade(token, cdId).stream().map(OcupacaoDiaResp::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria o agendamento (não bloqueia por capacidade; devolve alertas)")
    public AgendadoResp agendar(@PathVariable String token, @Valid @RequestBody Agendar b, HttpServletRequest req) {
        if (b.siteWeb() != null && !b.siteWeb().isBlank()) {
            // Envio automatizado (campo escondido preenchido): resposta neutra, nada é gravado.
            return new AgendadoResp(null, b.data(), null, null, 0, List.of(), null);
        }
        limitador.envio(Requisicao.ip(req));
        var r = useCase.agendar(token, new PedidoPublico(b.cdId(), b.data(), b.faixaHorariaId(), b.idPedido(),
                b.fornecedor(), b.cnpj(), b.emailFornecedor(), b.tipoCaminhaoId(), b.tipoPaletizacaoId(),
                b.quantidadePaletes(), b.transportadora(), b.observacao()), Requisicao.ip(req));
        var d = r.resultado().agendamento();
        return new AgendadoResp(d.agendamento().getId(), d.agendamento().getData(), d.faixaDescricao(), r.destino(),
                d.agendamento().getPaletesEquivalentes(), r.alertas(), r.resultado().linkFornecedor());
    }
}
