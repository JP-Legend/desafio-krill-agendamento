package br.com.redekrill.agendamento.domain.model;

import br.com.redekrill.agendamento.domain.exception.RegraNegocioException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conteúdo do código de barras (Code128) do vale-palete: {@code EMPRESA-VP-AAAAMMDD-NNNN}.
 * É derivado do código do vale, então vale também para os vales já emitidos (nada é gravado a mais).
 * Na leitura, o prefixo diz de qual empresa é o vale; código digitado sem prefixo também é aceito.
 */
public final class CodigoLeituraVale {
    private static final Pattern LIDO = Pattern.compile("^(?:([A-Z0-9]{2,12})-)?(VP-[0-9A-Z-]{4,40})$");

    private CodigoLeituraVale() {
    }

    public static String gerar(String empresa, String codigoVale) {
        return empresa.toUpperCase(Locale.ROOT) + "-" + codigoVale;
    }

    /** Código do vale contido na leitura; recusa vale de outra empresa. Nulo se não for um código de vale. */
    public static String interpretar(String lido, String empresa) {
        if (lido == null) return null;
        String t = lido.trim().toUpperCase(Locale.ROOT).replace('\'', '-');
        Matcher m = LIDO.matcher(t);
        if (!m.matches()) return null;
        if (m.group(1) != null && !m.group(1).equalsIgnoreCase(empresa)) {
            throw new RegraNegocioException("Este vale-palete é de outra empresa (" + m.group(1) + ")");
        }
        return m.group(2);
    }
}
