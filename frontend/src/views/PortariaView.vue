<script setup>
import RegistroChegada from '../components/RegistroChegada.vue'
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { api } from '../api'
import { deIso, fmtData, diaSemana, hoje, isoData } from '../utils'
import { pode } from '../sessao'
import { useNotificar } from '../composables/useNotificar'
import { useEnvio } from '../composables/useEnvio'
import SeletorDestino from '../components/SeletorDestino.vue'

/**
 * Portaria (porte de /recebimento-operacional/portaria/ do Gestão CD): o porteiro registra a chegada do caminhão
 * (placa, motorista e hora), encaminha à doca e registra a saída. A chegada avisa todos (sino e push).
 */
const notificar = useNotificar()
const { enviando, enviar } = useEnvio()
const data = ref(deIso(hoje()))
const destino = ref({ cdId: null, loja: null })
const quadro = ref(null)
const filtro = ref('')
const chegada = reactive({ aberto: false, cartao: null })
let timer = null

const hora = (s) => (s ? new Date(s).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Sao_Paulo' }) : '')
const ehHoje = computed(() => isoData(data.value) === hoje())
const docasLivres = computed(() => (quadro.value?.docas || []).filter((d) => !d.agendamentoId).map((d) => ({ label: d.doca.nome, value: d.doca.id })))
const normal = (s) => (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '')
const confere = (k) => !filtro.value.trim() || [k.placa, k.fornecedor, k.transportadora, k.motorista, String(k.idPedido || '')].some((v) => normal(v).includes(normal(filtro.value)))
const col = (c) => (quadro.value?.colunas?.[c] || []).filter(confere)

const ETAPAS = computed(() => [
  { chave: 'previstos', titulo: 'Aguardando chegada', icone: 'pi pi-calendar', itens: col('PREVISTO') },
  { chave: 'patio', titulo: 'No pátio', icone: 'pi pi-hourglass', itens: col('AGUARDANDO') },
  { chave: 'doca', titulo: 'Na doca', icone: 'pi pi-sign-in', itens: [...col('NA_DOCA'), ...col('DESCARREGANDO')] },
  { chave: 'saida', titulo: 'Liberados para saída', icone: 'pi pi-sign-out', itens: col('LIBERADO').filter((k) => !k.saidaEm) },
  { chave: 'saiu', titulo: 'Saíram', icone: 'pi pi-check-circle', itens: col('LIBERADO').filter((k) => k.saidaEm) }
])

async function carregar() {
  try {
    quadro.value = await api('/patio', { params: { data: isoData(data.value), ...destino.value } })
  } catch (e) {
    notificar.erro(e)
  }
}

function abrirChegada(k) {
  Object.assign(chegada, { aberto: true, cartao: k })
}

function atribuir(k, docaId) {
  enviar(async () => {
    try {
      await api(`/agendamentos/${k.agendamentoId}/doca`, { method: 'PUT', body: { docaId } })
      notificar.sucesso(docaId ? 'Caminhão encaminhado à doca' : 'Doca liberada')
      await carregar()
    } catch (e) {
      notificar.erro(e)
    }
  })
}

function registrarSaida(k) {
  enviar(async () => {
    try {
      await api(`/agendamentos/${k.agendamentoId}/saida`, { method: 'PATCH' })
      notificar.sucesso(`${k.placa || k.fornecedor} saiu do CD`, 'Saída registrada')
      await carregar()
    } catch (e) {
      notificar.erro(e)
    }
  })
}

watch([data, destino], carregar, { deep: true })
onMounted(() => {
  carregar()
  timer = setInterval(carregar, 30000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="cabecalho">
    <h1>Portaria</h1>
    <div class="acoes filtros-inline">
      <SeletorDestino v-model="destino" input-id="ptcd" :todos="false" />
      <div class="campo"><label for="ptd">Data</label><DatePicker v-model="data" input-id="ptd" date-format="dd/mm/yy" show-icon icon-display="input" /></div>
      <div class="campo"><label for="ptb">Placa, fornecedor ou pedido</label><InputText id="ptb" v-model="filtro" type="search" autocomplete="off" /></div>
      <Button icon="pi pi-refresh" class="btn-secundario" aria-label="Atualizar" @click="carregar" />
    </div>
  </div>

  <template v-if="quadro">
    <p class="texto-suave">{{ diaSemana(quadro.data) }}, {{ fmtData(quadro.data) }} · {{ quadro.indicador.docasOcupadas }} de {{ quadro.indicador.docasAtivas }} docas ocupadas<template v-if="pode(1205)"> · <RouterLink to="/patio">Pátio e docas</RouterLink></template></p>
    <div class="portaria-resumo">
      <div v-for="e in ETAPAS" :key="e.chave" class="kpi"><span>{{ e.titulo }}</span><strong>{{ e.itens.length }}</strong></div>
    </div>

    <div class="portaria-etapas">
      <section v-for="e in ETAPAS" :key="e.chave" class="painel etapa">
        <div class="painel-titulo"><h2><i :class="e.icone" /> {{ e.titulo }}</h2><Tag :value="String(e.itens.length)" severity="secondary" /></div>
        <p v-if="!e.itens.length" class="texto-suave">Nenhum caminhão</p>
        <article v-for="k in e.itens" :key="k.agendamentoId" class="veiculo" :class="{ tocavel: e.chave === 'previstos' && pode(1204) && ehHoje }" @click="e.chave === 'previstos' && pode(1204) && ehHoje && abrirChegada(k)">
          <header>
            <RouterLink :to="`/agendamentos/${k.agendamentoId}`" class="placa" @click.stop>{{ k.placa || 'Sem placa' }}</RouterLink>
            <span class="faixa">{{ k.faixa }}</span>
          </header>
          <strong class="fornecedor">{{ k.fornecedor }}</strong>
          <span class="suave">Pedido {{ k.idPedido }} · {{ k.tipoCaminhao }} · {{ k.paletes }} paletes</span>
          <span v-if="k.motorista" class="suave"><i class="pi pi-user" /> {{ k.motorista }}</span>
          <span v-if="k.transportadora" class="suave"><i class="pi pi-truck" /> {{ k.transportadora }}</span>
          <span v-if="k.chegadaEm" class="suave">Chegou às {{ hora(k.chegadaEm) }}<template v-if="k.docaEm"> · doca às {{ hora(k.docaEm) }}</template><template v-if="k.saidaEm"> · saiu às {{ hora(k.saidaEm) }}</template></span>
          <span v-if="e.chave === 'doca'" class="suave">{{ k.codigoStatus === 'EM_RECEBIMENTO' ? 'Em conferência' : 'Aguardando conferência' }}<template v-if="k.doca"> · <Tag :value="k.doca" /></template></span>
          <div class="acoes-veiculo">
            <Button v-if="e.chave === 'previstos' && pode(1204) && ehHoje" label="Registrar chegada" icon="pi pi-truck" class="btn-confirmar" :disabled="enviando" @click.stop="abrirChegada(k)" />
            <Select v-if="e.chave === 'patio' && pode(1206)" :options="docasLivres" option-label="label" option-value="value" placeholder="Encaminhar à doca"
                    :disabled="enviando || !docasLivres.length" :aria-label="`Encaminhar ${k.placa || k.fornecedor} à doca`" @change="(ev) => atribuir(k, ev.value)" />
            <Button v-if="e.chave === 'saida' && pode(1204)" label="Registrar saída" icon="pi pi-sign-out" class="btn-confirmar" :disabled="enviando" @click="registrarSaida(k)" />
          </div>
        </article>
      </section>
    </div>
  </template>

  <RegistroChegada v-model:visivel="chegada.aberto" :cartao="chegada.cartao" @registrado="carregar" />
</template>

<style scoped>
.portaria-resumo { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
.portaria-resumo .kpi strong { font-size: 20px; }
.portaria-etapas { display: grid; grid-template-columns: repeat(5, minmax(220px, 1fr)); gap: 12px; overflow-x: auto; }
.etapa h2 i { color: var(--cor-primaria); }
.veiculo { display: flex; flex-direction: column; gap: 3px; padding: 10px; margin-bottom: 8px; border: 1px solid var(--cor-linha); border-radius: var(--raio); background: var(--cor-painel); font-size: 13px; }
.veiculo header { display: flex; justify-content: space-between; align-items: center; gap: 6px; }
.veiculo .placa { font-size: 16px; font-weight: 800; letter-spacing: .04em; color: var(--cor-primaria); text-decoration: none; }
.veiculo .faixa { font-size: 12px; color: var(--cor-texto-suave); }
.veiculo .fornecedor { color: var(--cor-titulo); }
.veiculo .suave { color: var(--cor-texto-suave); font-size: 12px; }
.acoes-veiculo { display: flex; margin-top: 6px; }
.veiculo.tocavel { cursor: pointer; }
.veiculo.tocavel:hover { border-color: var(--cor-primaria); }
.acoes-veiculo:empty { display: none; }
.acoes-veiculo .p-button, .acoes-veiculo .p-select { width: 100%; min-height: 44px; justify-content: center; }
.campo.c12 strong { display: block; }
@media (max-width: 1100px) {
  .portaria-etapas { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .portaria-resumo { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .portaria-resumo .kpi span { font-size: 11px; }
  .portaria-etapas { grid-template-columns: 1fr; }
}
</style>
