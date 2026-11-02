package br.com.redekrill.agendamento.domain.model;

import java.time.LocalDate;

/** Se o destino recebe mercadoria na data e, quando não recebe (ou é feriado), o motivo. */
public record DiaRecebimento(LocalDate data, boolean aberto, String motivo) {
}
