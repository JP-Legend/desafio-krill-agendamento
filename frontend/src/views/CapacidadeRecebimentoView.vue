<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api } from '../api'
import { pode } from '../sessao'
import { deIso, fmtData, hoje, isoData, somarDias } from '../utils'
import { useEnvio } from '../composables/useEnvio'
import { useNotificar } from '../composables/useNotificar'
import BarraOcupacao from '../components/BarraOcupacao.vue'

/**
 * Um único lugar para tudo o que define quando e quanto cada destino recebe: CD ou loja (entrega direta).
 * Limites (alertam, nunca bloqueiam) e calendário (dia fechado recusa o agendamento).
 */
const DIAS = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo']
const DIAS_CURTOS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']
const notificar = useNotificar()
const { enviando, enviar } = useEnvio()
const editar = computed(() => pode(1405))

const cds = ref([])
const lojas = ref([])
const padrao = reactive({ dia: null, faixa: null })
const destino = reactive({ tipo: 'CD', cdId: null, loja: null })
const ehLoja = computed(() => destino.tipo === 'LOJA')
const params = computed(() => (ehLoja.value ? { loja: destino.loja } : { cdId: destino.cdId }))
const pronto = computed(() => (ehLoja.value ? !!destino.loja : !!destino.cdId))
const nomeDestino = computed(() => {
  if (ehLoja.value) return destino.loja || 'Loja'
  const c = cds.value.find((x) => x.id === destino.cdId)
  return c ? `CD ${c.codigo} · ${c.nome}` : 'CD'
})

// ---- limites
const regras = ref([])
const regrasDestino = computed(() => regras.value.filter((r) => (ehLoja.value
  ? (r.loja || '').toLowerCase() === (destino.loja || '').toLowerCase()
  : r.cdId === destino.cdId)))
const dialogo = ref(false)
const form = reactive({ id: null, diaSemana: null, paletes: null, caminhoes: null, paletesFaixa: null })
function abrir(r) {
  Object.assign(form, r ? { id: r.id, diaSemana: r.diaSemana, paletes: r.paletes, caminhoes: r.caminhoes, paletesFaixa: r.paletesFaixa }
    : { id: null, diaSemana: null, paletes: null, caminhoes: null, paletesFaixa: null })
  dialogo.value = true
}
function salvarRegra() {
  if (!form.paletes && !form.caminhoes && !form.paletesFaixa) return notificar.aviso('Informe ao menos um limite')
  const body = { ...params.value, diaSemana: form.diaSemana, paletes: form.paletes, caminhoes: form.caminhoes, paletesFaixa: ehLoja.value ? null : form.paletesFaixa }
  enviar(async () => {
    try {
      await api(form.id ? `/capacidades-recebimento/${form.id}` : '/capacidades-recebimento', { method: form.id ? 'PUT' : 'POST', body })
      dialogo.value = false
      notificar.sucesso('Limite salvo')
      await carregarRegras()
      consultar()
    } catch (e) { notificar.erro(e) }
  })
}
function excluirRegra(r) {
  enviar(async () => {
    try {
      await api(`/capacidades-recebimento/${r.id}`, { method: 'DELETE' })
      notificar.sucesso('Limite excluído')
      await carregarRegras()
      consultar()
    } catch (e) { notificar.erro(e) }
  })
}
async function carregarRegras() {
  regras.value = await api('/capacidades-recebimento')
}
const efetivoDia = computed(() => regrasDestino.value.find((r) => r.diaSemana == null)?.paletes ?? (ehLoja.value ? null : padrao.dia))
const efetivoFaixa = computed(() => regrasDestino.value.find((r) => r.diaSemana == null)?.paletesFaixa ?? (ehLoja.value ? null : padrao.faixa))

// ---- calendário
const cal = reactive({ dias: [true, true, true, true, true, true, false], recebeFeriado: false, configurado: false })
const excecoes = ref([])
const feriados = ref([])
const situacao = ref([])
const nova = reactive({ data: null, aberto: false, motivo: '' })
async function carregarCalendario() {
  const r = await api('/calendario-recebimento', { params: params.value })
  cal.dias = [...r.calendario.diasSemana].map((c) => c === '1')
  cal.recebeFeriado = r.calendario.recebeFeriado
  cal.configurado = r.configurado
  excecoes.value = r.excecoes
  const ini = hoje()
  const fim = somarDias(ini, 400)
  ;[feriados.value, situacao.value] = await Promise.all([
    api('/calendario-recebimento/feriados', { params: { inicio: ini, fim } }),
    api('/calendario-recebimento/dias', { params: { inicio: ini, fim, ...params.value } })
  ])
}
const situacaoPorData = computed(() => Object.fromEntries(situacao.value.map((d) => [d.data, d])))
function salvarCalendario() {
  if (!cal.dias.some(Boolean)) return notificar.aviso('Marque ao menos um dia da semana')
  enviar(async () => {
    try {
      await api('/calendario-recebimento', { method: 'PUT', body: { ...params.value, diasSemana: cal.dias.map((d) => (d ? '1' : '0')).join(''), recebeFeriado: cal.recebeFeriado } })
      notificar.sucesso(`Calendário de ${nomeDestino.value} salvo`)
      await carregarCalendario()
    } catch (e) { notificar.erro(e) }
  })
}
function adicionarExcecao() {
  if (!nova.data) return notificar.aviso('Informe a data')
  enviar(async () => {
    try {
      await api('/calendario-recebimento/excecoes', { method: 'POST', body: { ...params.value, data: isoData(nova.data), aberto: nova.aberto, motivo: nova.motivo.trim() || null } })
      Object.assign(nova, { data: null, aberto: false, motivo: '' })
      notificar.sucesso('Exceção salva')
      await carregarCalendario()
    } catch (e) { notificar.erro(e) }
  })
}
function abrirFeriado(f) {
  Object.assign(nova, { data: deIso(f.data), aberto: !cal.recebeFeriado, motivo: f.nome })
}
function excluirExcecao(e) {
  enviar(async () => {
    try {
      await api(`/calendario-recebimento/excecoes/${e.id}`, { method: 'DELETE' })
      notificar.sucesso('Exceção removida')
      await carregarCalendario()
    } catch (err) { notificar.erro(err) }
  })
}

// ---- está cheio?
const dataConsulta = ref(deIso(hoje()))
const ocupacao = ref(null)
async function consultar() {
  if (!pronto.value) return
  try {
    ocupacao.value = await api('/capacidades-recebimento/ocupacao', { params: { data: isoData(dataConsulta.value), ...params.value } })
  } catch (e) { notificar.erro(e) }
}
const diaConsulta = ref(null)
watch(dataConsulta, async () => {
  consultar()
  if (pronto.value) diaConsulta.value = (await api('/calendario-recebimento/dias', { params: { inicio: isoData(dataConsulta.value), fim: isoData(dataConsulta.value), ...params.value } }).catch(() => [null]))[0]
})

async function trocarDestino() {
  if (!pronto.value) return
  try {
    await Promise.all([carregarRegras(), carregarCalendario()])
    consultar()
    diaConsulta.value = situacaoPorData.value[isoData(dataConsulta.value)] || null
  } catch (e) { notificar.erro(e) }
}
watch(() => [destino.tipo, destino.cdId, destino.loja], trocarDestino)

onMounted(async () => {
  try {
    const [c, l, p, f] = await Promise.all([api('/cds'), api('/lojas'), api('/parametros/capacidade').catch(() => null), api('/faixas-horarias').catch(() => [])])
    cds.value = c.filter((x) => x.ativo)
    lojas.value = l.filter((x) => x.ativo).map((x) => x.nome)
    padrao.dia = p?.capacidadeDiariaPaletes ?? null
    const caps = [...new Set(f.filter((x) => x.ativo).map((x) => x.capacidadePaletes))]
    padrao.faixa = caps.length === 1 ? caps[0] : null
    destino.cdId = cds.value[0]?.id ?? null
  } catch (e) { notificar.erro(e) }
})
</script>

<template>
  <div class="cabecalho"><h1>Capacidade e calendário de recebimento</h1></div>

  <section class="painel destino">
    <div class="tipo-destino" role="radiogroup" aria-label="Tipo de destino">
      <button type="button" role="radio" :aria-checked="!ehLoja" :class="{ ativo: !ehLoja }" @click="destino.tipo = 'CD'"><i class="pi pi-building" /> Centro de distribuição</button>
      <button type="button" role="radio" :aria-checked="ehLoja" :class="{ ativo: ehLoja }" @click="destino.tipo = 'LOJA'"><i class="pi pi-shop" /> Loja (entrega direta)</button>
    </div>
    <div class="campo seletor">
      <template v-if="!ehLoja">
        <label for="dest-cd">CD</label>
        <Select v-model="destino.cdId" input-id="dest-cd" :options="cds" :option-label="(c) => `${c.codigo} · ${c.nome}`" option-value="id" />
      </template>
      <template v-else>
        <label for="dest-loja">Loja</label>
        <Select v-model="destino.loja" input-id="dest-loja" :options="lojas" filter placeholder="Escolha a loja" />
      </template>
    </div>
  </section>

  <template v-if="pronto">
    <div class="grade-destino">
      <section class="painel">
        <div class="titulo-secao">
          <h2>Limites de {{ nomeDestino }}</h2>
          <Button v-if="editar" label="Novo limite" icon="pi pi-plus" size="small" class="btn-confirmar" @click="abrir(null)" />
        </div>
        <p class="texto-suave nota">
          Só alertam, nunca bloqueiam. Não paletizado conta em dobro.
          <template v-if="!ehLoja"> Sem limite cadastrado, valem {{ padrao.dia ?? 480 }} paletes por dia e {{ padrao.faixa ?? 120 }} por faixa.</template>
          <template v-else> Sem limite cadastrado, a loja não tem limite.</template>
        </p>
        <div class="efetivos">
          <div><span>Por dia</span><strong>{{ efetivoDia != null ? `${efetivoDia} paletes` : 'Sem limite' }}</strong></div>
          <div v-if="!ehLoja"><span>Por faixa</span><strong>{{ efetivoFaixa != null ? `${efetivoFaixa} paletes` : 'Conforme cada faixa' }}</strong></div>
        </div>
        <DataTable :value="regrasDestino" size="small" striped-rows data-key="id">
          <template #empty><div class="vazio">Nenhum limite próprio cadastrado</div></template>
          <Column header="Dia"><template #body="{ data }">{{ data.diaSemana ? DIAS[data.diaSemana - 1] : 'Todos os dias' }}</template></Column>
          <Column header="Paletes/dia" class="num"><template #body="{ data }">{{ data.paletes ?? '—' }}</template></Column>
          <Column v-if="!ehLoja" header="Paletes/faixa" class="num"><template #body="{ data }">{{ data.paletesFaixa ?? '—' }}</template></Column>
          <Column header="Caminhões/dia" class="num"><template #body="{ data }">{{ data.caminhoes ?? '—' }}</template></Column>
          <Column v-if="editar" header="" style="width: 92px">
            <template #body="{ data }">
              <Button icon="pi pi-pencil" text rounded aria-label="Editar limite" @click="abrir(data)" />
              <Button icon="pi pi-trash" text rounded severity="danger" aria-label="Excluir limite" :disabled="enviando" @click="excluirRegra(data)" />
            </template>
          </Column>
        </DataTable>
      </section>

      <section class="painel">
        <div class="titulo-secao">
          <h2>Calendário de {{ nomeDestino }}</h2>
          <span v-if="!cal.configurado" class="pilula">Sem calendário: recebe todos os dias</span>
        </div>
        <p class="texto-suave nota">Dia fechado não aparece para agendar e o sistema recusa o agendamento.</p>
        <fieldset class="dias-semana" :disabled="!editar">
          <legend>Dias em que recebe mercadoria</legend>
          <label v-for="(d, i) in DIAS_CURTOS" :key="d" class="dia" :class="{ marcado: cal.dias[i] }">
            <Checkbox v-model="cal.dias[i]" binary :input-id="`dia-${i}`" />{{ d }}
          </label>
        </fieldset>
        <label class="linha-toggle"><ToggleSwitch v-model="cal.recebeFeriado" input-id="rec-feriado" :disabled="!editar" /> Recebe em feriado</label>
        <div v-if="editar" class="acoes-secao"><Button label="Salvar calendário" icon="pi pi-check" class="btn-confirmar" size="small" :loading="enviando" :disabled="enviando" @click="salvarCalendario" /></div>

        <h3>Exceções por data</h3>
        <form v-if="editar" class="nova-excecao" @submit.prevent="adicionarExcecao">
          <div class="campo"><label for="ex-data">Data</label><DatePicker v-model="nova.data" input-id="ex-data" date-format="dd/mm/yy" show-icon icon-display="input" /></div>
          <div class="campo"><label for="ex-tipo">Situação</label><Select v-model="nova.aberto" input-id="ex-tipo" :options="[{ l: 'Fechado', v: false }, { l: 'Aberto', v: true }]" option-label="l" option-value="v" /></div>
          <div class="campo motivo"><label for="ex-motivo">Motivo</label><InputText id="ex-motivo" v-model="nova.motivo" maxlength="120" /></div>
          <Button type="submit" label="Adicionar" icon="pi pi-plus" size="small" class="btn-secundario" :disabled="enviando" />
        </form>
        <ul class="lista-datas">
          <li v-for="e in excecoes" :key="e.id">
            <span class="data">{{ fmtData(e.data) }}</span>
            <span class="pilula" :class="e.aberto ? 'ok' : 'fechado'">{{ e.aberto ? 'Aberto' : 'Fechado' }}</span>
            <span class="texto-suave">{{ e.motivo }}</span>
            <Button v-if="editar" icon="pi pi-times" text rounded size="small" aria-label="Remover exceção" :disabled="enviando" @click="excluirExcecao(e)" />
          </li>
          <li v-if="!excecoes.length" class="texto-suave">Nenhuma exceção cadastrada</li>
        </ul>

        <h3>Feriados nacionais</h3>
        <ul class="lista-datas feriados">
          <li v-for="f in feriados" :key="f.data">
            <span class="data">{{ fmtData(f.data) }}</span>
            <span>{{ f.nome }}</span>
            <span class="pilula" :class="situacaoPorData[f.data]?.aberto ? 'ok' : 'fechado'">{{ situacaoPorData[f.data]?.aberto ? 'Recebe' : 'Não recebe' }}</span>
            <Button v-if="editar" :label="situacaoPorData[f.data]?.aberto ? 'Fechar' : 'Abrir'" text size="small" @click="abrirFeriado(f)" />
          </li>
        </ul>
      </section>
    </div>

    <section class="painel consulta">
      <h2>Está cheio?</h2>
      <div class="filtros">
        <div class="campo"><label for="cq-data">Data</label><DatePicker v-model="dataConsulta" input-id="cq-data" date-format="dd/mm/yy" show-icon icon-display="input" /></div>
      </div>
      <p v-if="diaConsulta && !diaConsulta.aberto" class="texto-excedido">{{ nomeDestino }} não recebe nesta data · {{ diaConsulta.motivo }}</p>
      <div v-if="ocupacao" class="resultado">
        <strong :class="ocupacao.cheio ? 'texto-excedido' : 'texto-ok'">
          {{ ocupacao.cheio ? `Sim, ${ocupacao.destino} está cheio` : ocupacao.limitado ? `Não, ${ocupacao.destino} ainda tem vaga` : `Sem limite cadastrado para ${ocupacao.destino}` }}
        </strong>
        <BarraOcupacao v-if="ocupacao.capacidadePaletes" :percentual="ocupacao.percentualPaletes" :ocupado="ocupacao.paletes" :capacidade="ocupacao.capacidadePaletes" />
        <span v-if="ocupacao.capacidadeCaminhoes">{{ ocupacao.caminhoes }} de {{ ocupacao.capacidadeCaminhoes }} caminhões ({{ Math.round(ocupacao.percentualCaminhoes) }}%)</span>
        <span v-else>{{ ocupacao.caminhoes }} caminhão(ões) agendado(s)</span>
      </div>
    </section>
  </template>

  <Dialog v-model:visible="dialogo" modal :header="form.id ? 'Editar limite' : 'Novo limite'" :style="{ width: '480px' }" :breakpoints="{ '520px': '96vw' }">
    <form class="grade" novalidate @submit.prevent="salvarRegra">
      <div class="campo c12"><label for="lim-dia">Dia da semana</label>
        <Select v-model="form.diaSemana" input-id="lim-dia" :options="[{ l: 'Todos os dias', v: null }, ...DIAS.map((d, i) => ({ l: d, v: i + 1 }))]" option-label="l" option-value="v" />
      </div>
      <div class="campo c6"><label for="lim-pal">Paletes por dia</label><InputNumber v-model="form.paletes" input-id="lim-pal" :min="1" :max="100000" /></div>
      <div class="campo c6"><label for="lim-cam">Caminhões por dia</label><InputNumber v-model="form.caminhoes" input-id="lim-cam" :min="1" :max="10000" /></div>
      <div v-if="!ehLoja" class="campo c6"><label for="lim-fx">Paletes por faixa</label><InputNumber v-model="form.paletesFaixa" input-id="lim-fx" :min="1" :max="100000" /></div>
    </form>
    <template #footer>
      <Button label="Cancelar" class="btn-secundario" :disabled="enviando" @click="dialogo = false" />
      <Button label="Salvar" icon="pi pi-check" class="btn-confirmar" :loading="enviando" :disabled="enviando" @click="salvarRegra" />
    </template>
  </Dialog>
</template>

<style scoped>
.destino { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 16px; }
.tipo-destino { display: inline-flex; border: 1px solid var(--gc-border); border-radius: var(--raio); overflow: hidden; }
.tipo-destino button { display: inline-flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 14px; border: 0; background: var(--gc-panel); color: var(--gc-text); font: inherit; font-weight: 600; cursor: pointer; }
.tipo-destino button + button { border-left: 1px solid var(--gc-border); }
.tipo-destino button.ativo { background: var(--gc-navy); color: var(--gc-topbar-text); }
.seletor { min-width: 280px; flex: 1 1 280px; max-width: 420px; }
.grade-destino { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 12px; margin-top: 12px; align-items: start; }
.titulo-secao { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.titulo-secao h2 { margin: 0; }
.nota { margin: 6px 0 10px; font-size: 12px; }
.efetivos { display: flex; gap: 10px; margin-bottom: 10px; }
.efetivos div { display: flex; flex-direction: column; gap: 2px; padding: 8px 12px; border: 1px solid var(--gc-border); border-left: 3px solid var(--gc-navy); border-radius: var(--raio); }
.efetivos span { font-size: 11px; color: var(--cor-texto-suave); text-transform: uppercase; letter-spacing: .06em; }
.dias-semana { display: flex; flex-wrap: wrap; gap: 6px; margin: 0 0 10px; padding: 0; border: 0; }
.dias-semana legend { margin-bottom: 6px; font-size: 12px; font-weight: 600; }
.dia { display: inline-flex; align-items: center; gap: 6px; padding: 6px 10px; border: 1px solid var(--gc-border); border-radius: var(--raio); font-size: 13px; cursor: pointer; }
.dia.marcado { border-color: var(--gc-navy); background: var(--gc-navy-50); }
.linha-toggle { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; }
.acoes-secao { display: flex; justify-content: flex-end; margin-top: 10px; }
h3 { margin: 16px 0 8px; font-size: 13px; color: var(--cor-titulo); }
.nova-excecao { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 8px; margin-bottom: 8px; }
.nova-excecao .campo { grid-column: auto; width: 150px; }
.nova-excecao .motivo { flex: 1 1 160px; }
.lista-datas { display: grid; gap: 4px; margin: 0; padding: 0; list-style: none; font-size: 13px; }
.lista-datas li { display: flex; align-items: center; gap: 8px; min-height: 32px; padding: 2px 4px; border-bottom: 1px solid var(--gc-border); }
.lista-datas .data { min-width: 84px; font-variant-numeric: tabular-nums; font-weight: 600; }
.feriados { max-height: 280px; overflow-y: auto; }
.feriados li span:nth-child(2) { flex: 1; }
.pilula.ok { background: var(--gc-toast-success-bg, var(--gc-navy-50)); color: var(--cor-ok); }
.pilula.fechado { background: var(--gc-navy-50); color: var(--cor-excedido); }
.consulta { margin-top: 12px; }
.resultado { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; max-width: 520px; }
.texto-excedido { color: var(--cor-excedido); }
.texto-ok { color: var(--cor-ok); }
@media (max-width: 1000px) { .grade-destino { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 640px) {
  .tipo-destino { width: 100%; }
  .tipo-destino button { flex: 1; justify-content: center; padding: 0 8px; font-size: 13px; }
  .seletor { min-width: 0; max-width: none; }
}
</style>
