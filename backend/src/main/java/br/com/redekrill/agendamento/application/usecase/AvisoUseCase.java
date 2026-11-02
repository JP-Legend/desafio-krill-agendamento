package br.com.redekrill.agendamento.application.usecase;

import br.com.redekrill.agendamento.application.port.AvisoRepositorio;
import br.com.redekrill.agendamento.domain.model.Aviso;

import java.util.List;

/** Sino de notificações do topo (mesmo comportamento do .request-bell do Gestão CD): lista, contador e "Marcar lidas". */
public class AvisoUseCase {

    public static final int LIMITE = 30;

    public record Item(Aviso aviso, boolean lido) {
    }

    public record Caixa(int naoLidos, List<Item> itens) {
    }

    private final AvisoRepositorio avisos;

    public AvisoUseCase(AvisoRepositorio avisos) {
        this.avisos = avisos;
    }

    public Aviso registrar(String titulo, String mensagem, String url) {
        return avisos.registrar(new Aviso(null, titulo, mensagem, url, null));
    }

    public Caixa caixa(String usuario) {
        long lido = avisos.ultimoLido(chave(usuario));
        List<Item> itens = avisos.recentes(LIMITE).stream().map(a -> new Item(a, a.id() <= lido)).toList();
        return new Caixa(avisos.contarDepoisDe(lido), itens);
    }

    public Caixa marcarLidas(String usuario) {
        avisos.recentes(1).stream().findFirst().ifPresent(a -> avisos.marcarLido(chave(usuario), a.id()));
        return caixa(usuario);
    }

    private static String chave(String usuario) {
        return usuario == null ? "" : usuario.toLowerCase();
    }
}
