package br.com.redekrill.agendamento.web.controller;

import br.com.redekrill.agendamento.application.usecase.BuscaGlobalUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/busca")
@Tag(name = "Busca", description = "Busca do topo: agendamentos, vales-palete, fornecedores, CDs e lojas")
public class BuscaController {
    private final BuscaGlobalUseCase useCase;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private br.com.redekrill.agendamento.infrastructure.persistence.jdbc.EscopoDestinos destinos;

    public BuscaController(BuscaGlobalUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @Operation(summary = "Procura por fornecedor, pedido, CNPJ, placa, data (dd/mm/aaaa) ou código; só nas telas do perfil")
    public List<BuscaGlobalUseCase.Resultado> buscar(@RequestParam(required = false) String q) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Set<Integer> permissoes = auth == null ? Set.of() : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("PERM_")).map(a -> Integer.parseInt(a.substring(5))).collect(Collectors.toSet());
        var r = useCase.buscar(q, permissoes);
        var e = br.com.redekrill.agendamento.application.port.EscopoAtual.get();
        if (e.todos() || destinos == null) return r;
        // Usuário restrito: só agendamentos/vales do CD ou loja dele; cadastros de CD/loja de outros destinos somem.
        return r.stream().filter(x -> {
            String u = x.url() == null ? "" : x.url();
            var m = java.util.regex.Pattern.compile("^/agendamentos/(\\d+)").matcher(u);
            if (m.find()) return destinos.agendamentoVisivel(e, Long.valueOf(m.group(1)));
            m = java.util.regex.Pattern.compile("^/vales-palete/(\\d+)").matcher(u);
            if (m.find()) return destinos.valeVisivel(e, Long.valueOf(m.group(1)));
            return !(x.grupo().contains("CD") || x.grupo().toLowerCase().contains("loja"));
        }).toList();
    }
}
