package br.com.redekrill.agendamento.application.port;

import br.com.redekrill.agendamento.application.usecase.BuscaGlobalUseCase.Resultado;

import java.time.LocalDate;
import java.util.List;

/** Consultas da busca do topo (OmniSearch). Cada método devolve no máximo {@code limite} registros. */
public interface BuscaRepositorio {
    /** Fornecedor, placa, nº do pedido, CNPJ (só dígitos) ou data do agendamento. */
    List<Resultado> agendamentos(String termo, String digitos, LocalDate data, int limite);

    List<Resultado> fornecedores(String termo, String digitos, int limite);

    List<Resultado> lojas(String termo, int limite);

    List<Resultado> cds(String termo, int limite);

    List<Resultado> vales(String termo, LocalDate data, int limite);
}
