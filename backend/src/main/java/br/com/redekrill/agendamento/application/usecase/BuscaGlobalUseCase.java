package br.com.redekrill.agendamento.application.usecase;

import br.com.redekrill.agendamento.application.port.BuscaRepositorio;
import br.com.redekrill.agendamento.domain.model.Permissao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Busca do topo (OmniSearch do Gestão CD): procura registros nas telas que o perfil pode consultar.
 * Agendamentos exigem 1102, vales 1301, CDs e lojas 1401, fornecedores 1402 — a mesma regra de cada tela.
 */
public class BuscaGlobalUseCase {

    public static final int MINIMO = 2;
    private static final int POR_GRUPO = 6;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final DateTimeFormatter BR_CURTA = DateTimeFormatter.ofPattern("d/M/uu").withResolverStyle(java.time.format.ResolverStyle.STRICT);

    public record Resultado(String grupo, String titulo, String detalhe, String url) {
    }

    private final BuscaRepositorio repositorio;

    public BuscaGlobalUseCase(BuscaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public List<Resultado> buscar(String bruto, Set<Integer> permissoes) {
        String termo = bruto == null ? "" : bruto.strip();
        if (termo.length() > 80) termo = termo.substring(0, 80);
        if (termo.length() < MINIMO) return List.of();
        String digitos = termo.replaceAll("\\D", "");
        LocalDate data = data(termo);
        List<Resultado> r = new ArrayList<>();
        if (permissoes.contains(Permissao.AGENDAMENTOS_VER.codigo())) r.addAll(repositorio.agendamentos(termo, digitos, data, POR_GRUPO));
        if (permissoes.contains(Permissao.VALE_VER.codigo())) r.addAll(repositorio.vales(termo, data, POR_GRUPO));
        if (permissoes.contains(Permissao.FORNECEDORES.codigo())) r.addAll(repositorio.fornecedores(termo, digitos, POR_GRUPO));
        if (permissoes.contains(Permissao.CDS.codigo())) {
            r.addAll(repositorio.cds(termo, POR_GRUPO));
            r.addAll(repositorio.lojas(termo, POR_GRUPO));
        }
        return r;
    }

    /** Aceita 03/11/2026, 3/11/26 e 03/11 (ano corrente). */
    static LocalDate data(String termo) {
        String t = termo.strip();
        if (!t.matches("\\d{1,2}/\\d{1,2}(/\\d{2,4})?")) return null;
        try {
            if (t.chars().filter(c -> c == '/').count() == 1) t = t + "/" + LocalDate.now().getYear();
            return LocalDate.parse(t, t.length() <= 8 ? BR_CURTA : BR);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
