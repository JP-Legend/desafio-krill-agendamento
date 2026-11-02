package br.com.redekrill.agendamento.web.controller;

import br.com.redekrill.agendamento.application.usecase.CalendarioRecebimentoUseCase;
import br.com.redekrill.agendamento.domain.model.CalendarioRecebimento;
import br.com.redekrill.agendamento.domain.model.DiaRecebimento;
import br.com.redekrill.agendamento.domain.model.ExcecaoRecebimento;
import br.com.redekrill.agendamento.domain.model.Feriado;
import br.com.redekrill.agendamento.web.dto.Requisicoes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/calendario-recebimento")
@Tag(name = "Calendário de recebimento", description = "Dias da semana, feriados e exceções em que cada CD ou loja recebe mercadoria")
public class CalendarioRecebimentoController {
    private final CalendarioRecebimentoUseCase useCase;
    private final Clock clock;

    public CalendarioRecebimentoController(CalendarioRecebimentoUseCase useCase, Clock clock) {
        this.useCase = useCase;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Calendário do destino (CD ou loja) e as exceções por data")
    public CalendarioRecebimentoUseCase.Calendario obter(@RequestParam(required = false) Long cdId, @RequestParam(required = false) String loja) {
        return useCase.obter(cdId, loja, LocalDate.now(clock));
    }

    @PutMapping
    public CalendarioRecebimento salvar(@Valid @RequestBody Requisicoes.CalendarioRecebimento b, HttpServletRequest req) {
        return useCase.salvar(b.cdId(), b.loja(), b.diasSemana(), Boolean.TRUE.equals(b.recebeFeriado()), Requisicao.solicitante(req));
    }

    @PostMapping("/excecoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ExcecaoRecebimento adicionarExcecao(@Valid @RequestBody Requisicoes.ExcecaoRecebimento b, HttpServletRequest req) {
        return useCase.adicionarExcecao(b.cdId(), b.loja(), b.data(), b.aberto(), b.motivo(), Requisicao.solicitante(req));
    }

    @DeleteMapping("/excecoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirExcecao(@PathVariable Long id, HttpServletRequest req) {
        useCase.excluirExcecao(id, Requisicao.solicitante(req));
    }

    @GetMapping("/dias")
    @Operation(summary = "Se o destino recebe em cada dia do período e, se não, o motivo")
    public List<DiaRecebimento> dias(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                     @RequestParam(required = false) Long cdId, @RequestParam(required = false) String loja) {
        return useCase.dias(inicio, fim, cdId, loja);
    }

    @GetMapping("/feriados")
    public List<Feriado> feriados(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return useCase.feriados(inicio, fim);
    }
}
