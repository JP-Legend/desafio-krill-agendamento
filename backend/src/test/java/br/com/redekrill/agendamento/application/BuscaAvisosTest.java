package br.com.redekrill.agendamento.application;

import br.com.redekrill.agendamento.application.port.AvisoRepositorio;
import br.com.redekrill.agendamento.application.port.BuscaRepositorio;
import br.com.redekrill.agendamento.application.usecase.AvisoUseCase;
import br.com.redekrill.agendamento.application.usecase.BuscaGlobalUseCase;
import br.com.redekrill.agendamento.application.usecase.BuscaGlobalUseCase.Resultado;
import br.com.redekrill.agendamento.domain.model.Aviso;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Busca do topo (respeita as permissões de cada tela) e sino de avisos (contador por usuário e "Marcar lidas"). */
class BuscaAvisosTest {

    static class BuscaFake implements BuscaRepositorio {
        final List<String> chamadas = new ArrayList<>();
        LocalDate ultimaData;
        String ultimosDigitos;

        @Override
        public List<Resultado> agendamentos(String termo, String digitos, LocalDate data, int limite) {
            chamadas.add("agendamentos");
            ultimaData = data;
            ultimosDigitos = digitos;
            return List.of(new Resultado("Agendamentos", "Distribuidora Beta", "03/11/2026 08:00 · Pedido 98765", "/agendamentos/1"));
        }

        @Override
        public List<Resultado> fornecedores(String termo, String digitos, int limite) {
            chamadas.add("fornecedores");
            return List.of();
        }

        @Override
        public List<Resultado> lojas(String termo, int limite) {
            chamadas.add("lojas");
            return List.of();
        }

        @Override
        public List<Resultado> cds(String termo, int limite) {
            chamadas.add("cds");
            return List.of();
        }

        @Override
        public List<Resultado> vales(String termo, LocalDate data, int limite) {
            chamadas.add("vales");
            return List.of();
        }
    }

    @Test
    void buscaSoConsultaAsTelasDoPerfil() {
        BuscaFake repo = new BuscaFake();
        BuscaGlobalUseCase busca = new BuscaGlobalUseCase(repo);
        assertThat(busca.buscar("Beta", Set.of(1102, 1301))).hasSize(1);
        assertThat(repo.chamadas).containsExactly("agendamentos", "vales");

        repo.chamadas.clear();
        busca.buscar("Beta", Set.of(1102, 1301, 1401, 1402));
        assertThat(repo.chamadas).containsExactly("agendamentos", "vales", "fornecedores", "cds", "lojas");

        repo.chamadas.clear();
        assertThat(busca.buscar("Beta", Set.of())).isEmpty();
        assertThat(busca.buscar("B", Set.of(1102))).as("mínimo de 2 caracteres").isEmpty();
        assertThat(busca.buscar(null, Set.of(1102))).isEmpty();
        assertThat(repo.chamadas).isEmpty();
    }

    @Test
    void buscaReconheceDataECnpj() {
        BuscaFake repo = new BuscaFake();
        BuscaGlobalUseCase busca = new BuscaGlobalUseCase(repo);
        busca.buscar("03/11/2026", Set.of(1102));
        assertThat(repo.ultimaData).isEqualTo(LocalDate.of(2026, 11, 3));
        busca.buscar("3/11/26", Set.of(1102));
        assertThat(repo.ultimaData).isEqualTo(LocalDate.of(2026, 11, 3));
        busca.buscar("31/02/2026", Set.of(1102));
        assertThat(repo.ultimaData).isNull();
        busca.buscar("12.345.678/0001-95", Set.of(1102));
        assertThat(repo.ultimosDigitos).isEqualTo("12345678000195");
        assertThat(repo.ultimaData).isNull();
    }

    static class AvisosMem implements AvisoRepositorio {
        final List<Aviso> lista = new ArrayList<>();
        final Map<String, Long> lidos = new HashMap<>();

        @Override
        public Aviso registrar(Aviso a) {
            Aviso salvo = new Aviso((long) lista.size() + 1, a.titulo(), a.mensagem(), a.url(), OffsetDateTime.now());
            lista.add(salvo);
            return salvo;
        }

        @Override
        public List<Aviso> recentes(int limite) {
            return lista.stream().sorted(Comparator.comparing(Aviso::id).reversed()).limit(limite).toList();
        }

        @Override
        public long ultimoLido(String usuario) {
            return lidos.getOrDefault(usuario, 0L);
        }

        @Override
        public int contarDepoisDe(long id) {
            return (int) lista.stream().filter(a -> a.id() > id).count();
        }

        @Override
        public void marcarLido(String usuario, long ateId) {
            lidos.merge(usuario, ateId, Math::max);
        }
    }

    @Test
    void sinoContaNaoLidosPorUsuario() {
        AvisoUseCase avisos = new AvisoUseCase(new AvisosMem());
        assertThat(avisos.caixa("admin").naoLidos()).isZero();
        avisos.registrar("Caminhão na portaria", "Distribuidora Beta · placa ABC1D23", "/portaria");
        avisos.registrar("Agendamento pelo link do fornecedor", "Beta · 03/11/2026", "/agendamentos/1");
        AvisoUseCase.Caixa c = avisos.caixa("admin");
        assertThat(c.naoLidos()).isEqualTo(2);
        assertThat(c.itens().get(0).aviso().titulo()).isEqualTo("Agendamento pelo link do fornecedor");

        assertThat(avisos.marcarLidas("Admin").naoLidos()).isZero();
        assertThat(avisos.caixa("admin").itens()).allMatch(AvisoUseCase.Item::lido);
        assertThat(avisos.caixa("usuario").naoLidos()).as("cada usuário tem a própria leitura").isEqualTo(2);

        avisos.registrar("Capacidade excedida", "x".repeat(400), "");
        AvisoUseCase.Caixa depois = avisos.caixa("admin");
        assertThat(depois.naoLidos()).isEqualTo(1);
        assertThat(depois.itens().get(0).aviso().mensagem()).hasSize(300);
        assertThat(depois.itens().get(0).aviso().url()).isNull();
    }
}
