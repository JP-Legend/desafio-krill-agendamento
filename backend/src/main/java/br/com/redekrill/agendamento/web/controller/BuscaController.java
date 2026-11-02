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

    public BuscaController(BuscaGlobalUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    @Operation(summary = "Procura por fornecedor, pedido, CNPJ, placa, data (dd/mm/aaaa) ou código; só nas telas do perfil")
    public List<BuscaGlobalUseCase.Resultado> buscar(@RequestParam(required = false) String q) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Set<Integer> permissoes = auth == null ? Set.of() : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("PERM_")).map(a -> Integer.parseInt(a.substring(5))).collect(Collectors.toSet());
        return useCase.buscar(q, permissoes);
    }
}
