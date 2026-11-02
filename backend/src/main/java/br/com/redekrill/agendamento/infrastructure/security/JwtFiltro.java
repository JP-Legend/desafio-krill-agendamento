package br.com.redekrill.agendamento.infrastructure.security;

import br.com.redekrill.agendamento.application.usecase.AcessoUseCase;
import br.com.redekrill.agendamento.domain.model.Permissao;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Valida o JWT e carrega, a cada requisição, o usuário e as permissões do perfil no banco: inativar o usuário
 * ou mudar o perfil vale na hora, sem esperar o token expirar. Com troca de senha pendente só /auth responde.
 */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private final JwtService jwt;
    private final AcessoUseCase acessos;
    private final br.com.redekrill.agendamento.infrastructure.persistence.jdbc.EscopoDestinos destinos;

    public JwtFiltro(JwtService jwt, AcessoUseCase acessos,
                     br.com.redekrill.agendamento.infrastructure.persistence.jdbc.EscopoDestinos destinos) {
        this.jwt = jwt;
        this.acessos = acessos;
        this.destinos = destinos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        br.com.redekrill.agendamento.domain.model.Escopo escopo = null;
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims c = jwt.validar(header.substring(7));
                var ctx = acessos.contextoAtual(c.getSubject()).orElse(null);
                if (ctx != null) {
                    if (ctx.usuario().trocarSenha() && !req.getRequestURI().startsWith("/api/v1/auth/")) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        res.setCharacterEncoding("UTF-8");
                        res.getWriter().write("{\"status\":403,\"mensagem\":\"Troque a senha provisória para continuar\",\"detalhes\":[]}");
                        return;
                    }
                    var authorities = ctx.permissoes().stream().map(p -> new SimpleGrantedAuthority(Permissao.porCodigo(p)
                            .map(Permissao::authority).orElse("PERM_" + p))).toList();
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(ctx.usuario().login(), null, authorities));
                    escopo = acessos.escopo(ctx.usuario().id());
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        if (escopo == null) {
            chain.doFilter(req, res);
            return;
        }
        // Usuário restrito: CD/loja pedidos na URL têm de ser dele; telas sem destino recebem o dele.
        if (escopo.todos()) {
            // sem restrição: só aplica o destino escolhido na tela
            HttpServletRequest efetiva = req;
            var efetivo = escopo;
            String cdSel = req.getParameter("cdId");
            String lojaSel = req.getParameter("loja");
            try {
                if ("GET".equals(req.getMethod()) && ComDestinoPadrao.filtraLista(req.getRequestURI())) {
                    if (lojaSel != null && !lojaSel.isBlank()) efetivo = new br.com.redekrill.agendamento.domain.model.Escopo(false, java.util.Set.of(), java.util.Set.of(lojaSel.trim()));
                    else if (cdSel != null && !cdSel.isBlank()) efetivo = new br.com.redekrill.agendamento.domain.model.Escopo(false, java.util.Set.of(Long.valueOf(cdSel.trim())), java.util.Set.of());
                }
            } catch (NumberFormatException e) {
                efetivo = escopo;
            }
            br.com.redekrill.agendamento.application.port.EscopoAtual.definir(efetivo.todos() ? null : efetivo);
            try {
                chain.doFilter(efetiva, res);
            } finally {
                br.com.redekrill.agendamento.application.port.EscopoAtual.limpar();
            }
            return;
        }
        String cd = req.getParameter("cdId");
        String loja = req.getParameter("loja");
        try {
            if (cd != null && !cd.isBlank() && !escopo.permiteCd(Long.valueOf(cd.trim()))
                    || loja != null && !loja.isBlank() && !escopo.permiteLoja(loja)) {
                negar(res);
                return;
            }
        } catch (NumberFormatException e) {
            negar(res);
            return;
        }
        if (!destinos.urlPermitida(escopo, req.getRequestURI())) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"status\":404,\"mensagem\":\"Registro não encontrado\",\"detalhes\":[]}");
            return;
        }
        HttpServletRequest efetiva = req;
        if ((cd == null || cd.isBlank()) && (loja == null || loja.isBlank()) && "GET".equals(req.getMethod())
                && ComDestinoPadrao.precisa(req.getRequestURI())) {
            efetiva = ComDestinoPadrao.de(req, escopo);
        }
        // Destino escolhido no seletor da tela (GET): as listas mostram só ele.
        var efetivo = escopo;
        String cdSel = efetiva.getParameter("cdId");
        String lojaSel = efetiva.getParameter("loja");
        if ("GET".equals(req.getMethod()) && ComDestinoPadrao.filtraLista(req.getRequestURI())) {
            if (lojaSel != null && !lojaSel.isBlank()) efetivo = new br.com.redekrill.agendamento.domain.model.Escopo(false, java.util.Set.of(), java.util.Set.of(lojaSel.trim()));
            else if (cdSel != null && !cdSel.isBlank()) efetivo = new br.com.redekrill.agendamento.domain.model.Escopo(false, java.util.Set.of(Long.valueOf(cdSel.trim())), java.util.Set.of());
        }
        br.com.redekrill.agendamento.application.port.EscopoAtual.definir(efetivo);
        try {
            chain.doFilter(efetiva, res);
        } finally {
            br.com.redekrill.agendamento.application.port.EscopoAtual.limpar();
        }
    }

    private static void negar(HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"status\":403,\"mensagem\":\"Você não tem acesso a este CD ou loja\",\"detalhes\":[]}");
    }
}
