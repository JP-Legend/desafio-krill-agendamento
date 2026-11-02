package br.com.redekrill.agendamento.domain.model;

import java.time.LocalDate;

/** Feriado nacional (ou ponto facultativo federal) cadastrado. */
public record Feriado(LocalDate data, String nome) {
}
