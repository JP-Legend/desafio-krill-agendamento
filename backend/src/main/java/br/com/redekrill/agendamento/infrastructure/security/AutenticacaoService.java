package br.com.redekrill.agendamento.infrastructure.security;

import br.com.redekrill.agendamento.application.usecase.AcessoUseCase;
import org.springframework.stereotype.Service;

import java.util.List;

/** Emite o JWT a partir do login validado no caso de uso. */
@Service
public class AutenticacaoService {

    private final AcessoUseCase acessos;
    private final JwtService jwt;

    public AutenticacaoService(AcessoUseCase acessos, JwtService jwt) {
        this.acessos = acessos;
        this.jwt = jwt;
    }

    public Sessao autenticar(String username, String senha, AcessoUseCase.Origem origem) {
        var ctx = acessos.autenticar(username, senha, origem);
        var u = ctx.usuario();
        return new Sessao(jwt.gerar(u.login(), u.nome(), ctx.perfil().nome()), u.login(), u.nome(), ctx.perfil().nome(),
                ctx.permissoes().stream().sorted().toList(), u.trocarSenha(), jwt.expiracaoSegundos(), destinos(acessos.escopo(u.id())));
    }

    public static Destinos destinos(br.com.redekrill.agendamento.domain.model.Escopo e) {
        return new Destinos(e.todos(), e.cds().stream().sorted().toList(), e.lojas().stream().sorted().toList());
    }

    /** Destinos do usuário: todos = sem restrição; senão os CDs (ids) e lojas (nomes) que ele enxerga. */
    public record Destinos(boolean todos, List<Long> cds, List<String> lojas) {
    }

    public record Sessao(String token, String usuario, String nome, String perfil, List<Integer> permissoes,
                         boolean trocarSenha, long expiraEmSegundos, Destinos destinos) {
    }
}
