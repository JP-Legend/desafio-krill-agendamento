<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { pode } from '../sessao'
import { deIso, fmtData, hoje, isoData } from '../utils'
import { useEnvio } from '../composables/useEnvio'
import { useNotificar } from '../composables/useNotificar'

/** Uma tela só: padrão geral (dia, faixa, faixas horárias) e, por CD e loja, limites e calendário de recebimento. */
const DIAS_CURTOS = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom']
const notificar = useNotificar()
const { enviando, enviar } = useEnvio()
const editarPadrao = computed(() => pode(1404))
const editar = computed(() => pode(1405))

// ---- padrão geral
const parametros = reactive({ capacidadeDiariaPaletes: null, quantidadeFaixas: null, duracaoFaixaMinutos: null })
const faixas = ref([])
const geral = reactive({ dia: null, faixa: null })
async function carregarPadrao() {
  const [p, f] = await Promise.all([api('/parametros/capacidade'), api('/faixas-horarias')])
  Object.assign(parametros, p)
  faixas.value = f.map((x) => ({ ...x, horaInicio: x.horaInicio.slice(0, 5), horaFim: x.horaFim.slice(0, 5) }))
  geral.dia = p.capacidadeDiariaPaletes
  const caps = [...new Set(f.filter((x) => x.ativo).map((x) => x.capacidadePaletes))]
  geral.faixa = caps.length === 1 ? caps[0] : null
}
const padraoFaixa = computed(() => {
  const caps = [...new Set(faixas.value.filter((x) => x.ativo).map((x) => x.capacidadePaletes))]
  return caps.length === 1 ? caps[0] : null
})
function salvarPadrao() {
  if (!geral.dia) return notificar.aviso('Informe o limite por dia')
  if (faixas.value.some((f) => !f.horaInicio || !f.horaFim || f.horaFim <= f.horaInicio)) return notificar.aviso('Confira o horário das faixas')
  enviar(async () => {
    try {
      await api('/parametros/capacidade', { method: 'PUT', body: { ...parametros, capacidadeDiariaPaletes: geral.dia } })
      for (const f of faixas.value) {
        const body = { horaInicio: f.horaInicio, horaFim: f.horaFim, capacidadePaletes: geral.faixa || f.capacidadePaletes, ativo: f.ativo }
        await api(f.id ? `/faixas-horarias/${f.id}` : '/faixas-horarias', { method: f.id ? 'PUT' : 'POST', body })
      }
      notificar.sucesso('Padrão geral salvo')
      await carregarPadrao()
    } catch (e) { notificar.erro(e) }
  })
}
function novaFaixa() {
  const ultima = faixas.value[faixas.value.length - 1]
  faixas.value.push({ id: null, horaInicio: ultima?.horaFim || '08:00', horaFim: '', capacidadePaletes: geral.faixa || 120, ativo: true })
}

// ---- por CD e loja
const cds = ref([])
const lojas = ref([])
const regras = ref([])
const calendarios = ref([])
const excecoes = ref([])
const chaveDe = (cdId, loja) => (loja ? `l:${loja.toLowerCase()}` : `c:${cdId}`)
const linhas = computed(() => [
  ...cds.value.map((c) => ({ chave: chaveDe(c.id), tipo: 'CD', nome: `CD ${c.codigo} · ${c.nome}`, cdId: c.id, loja: null })),
  ...lojas.value.map((l) => ({ chave: chaveDe(null, l.nome), tipo: 'Loja', nome: l.nome, cdId: null, loja: l.nome }))
].map((d) => {
  const regra = regras.value.find((r) => r.diaSemana == null && chaveDe(r.loja ? null : r.cdId, r.loja) === d.chave) || null
  const cal = calendarios.value.find((c) => chaveDe(c.loja ? null : c.cdId, c.loja) === d.chave) || null
  return { ...d, regra, cal, excecoes: excecoes.value.filter((e) => chaveDe(e.loja ? null : e.cdId, e.loja) === d.chave) }
}))
function diasTexto(cal) {
  const d = cal ? cal.diasSemana : '1111111'
  if (d === '1111111') return 'Todos'
  if (d === '1111110') return 'Seg a sáb'
  if (d === '1111100') return 'Seg a sex'
  return [...d].map((c, i) => (c === '1' ? DIAS_CURTOS[i] : null)).filter(Boolean).join(', ')
}
const feriadoTexto = (cal) => (!cal || cal.recebeFeriado ? 'Recebe' : 'Não recebe')
async function carregarDestinos() {
  const [c, l, r, res] = await Promise.all([api('/cds'), api('/lojas'), api('/capacidades-recebimento'), api('/calendario-recebimento/resumo')])
  cds.value = c.filter((x) => x.ativo)
  lojas.value = l.filter((x) => x.ativo !== false)
  regras.value = r
  calendarios.value = res.calendarios
  excecoes.value = res.excecoes
}

// ---- edição de um destino
const dialogo = ref(false)
const atual = ref(null)
const form = reactive({ dia: null, faixa: null, dias: [], recebeFeriado: true })
const nova = reactive({ data: null, aberto: false, motivo: '' })
function abrir(l) {
  atual.value = l
  const dias = l.cal ? l.cal.diasSemana : '1111111'
  Object.assign(form, { dia: l.regra?.paletes ?? null, faixa: l.regra?.paletesFaixa ?? null, dias: [...dias].map((c) => c === '1'), recebeFeriado: l.cal ? l.cal.recebeFeriado : true })
  Object.assign(nova, { data: null, aberto: false, motivo: '' })
  dialogo.value = true
}
const destinoParams = computed(() => (atual.value?.loja ? { loja: atual.value.loja } : { cdId: atual.value?.cdId }))
const excecoesAtual = computed(() => (atual.value ? excecoes.value.filter((e) => chaveDe(e.loja ? null : e.cdId, e.loja) === atual.value.chave) : []))
function salvarDestino() {
  if (!form.dias.some(Boolean)) return notificar.aviso('Marque ao menos um dia')
  const l = atual.value
  enviar(async () => {
    try {
      const temLimite = form.dia || (!l.loja && form.faixa)
      const regraOutros = { caminhoes: l.regra?.caminhoes ?? null }
      if (temLimite || regraOutros.caminhoes) {
        const body = { ...destinoParams.value, diaSemana: null, paletes: form.dia || null, paletesFaixa: l.loja ? null : form.faixa || null, ...regraOutros }
        await api(l.regra ? `/capacidades-recebimento/${l.regra.id}` : '/capacidades-recebimento', { method: l.regra ? 'PUT' : 'POST', body })
      } else if (l.regra) {
        await api(`/capacidades-recebimento/${l.regra.id}`, { method: 'DELETE' })
      }
      await api('/calendario-recebimento', { method: 'PUT', body: { ...destinoParams.value, diasSemana: form.dias.map((d) => (d ? '1' : '0')).join(''), recebeFeriado: form.recebeFeriado } })
      notificar.sucesso(`${l.nome} salvo`)
      dialogo.value = false
      await carregarDestinos()
    } catch (e) { notificar.erro(e) }
  })
}
function adicionarExcecao() {
  if (!nova.data) return notificar.aviso('Informe a data')
  enviar(async () => {
    try {
      await api('/calendario-recebimento/excecoes', { method: 'POST', body: { ...destinoParams.value, data: isoData(nova.data), aberto: nova.aberto, motivo: nova.motivo.trim() || null } })
      Object.assign(nova, { data: null, aberto: false, motivo: '' })
      await carregarDestinos()
    } catch (e) { notificar.erro(e) }
  })
}
function removerExcecao(e) {
  enviar(async () => {
    try {
      await api(`/calendario-recebimento/excecoes/${e.id}`, { method: 'DELETE' })
      await carregarDestinos()
    } catch (er) { notificar.erro(er) }
  })
}

const pronto = ref(false)
onMounted(async () => {
  try {
    await Promise.all([carregarPadrao(), carregarDestinos()])
    pronto.value = true
  } catch (e) { notificar.erro(e) }
})
defineExpose({ abrir, linhas })
</script>

<template>
  <div class="cabecalho"><h1>Capacidade e calendário</h1></div>

  <section class="painel">
    <div class="titulo-secao"><h2>Padrão geral</h2></div>
    <form class="padrao" @submit.prevent="salvarPadrao">
      <div class="campos-padrao">
        <div class="campo"><label for="pg-dia">Limite por dia (paletes)</label><InputNumber v-model="geral.dia" input-id="pg-dia" :min="1" :max="100000" :disabled="!editarPadrao" /></div>
        <div class="campo"><label for="pg-faixa">Limite por faixa (paletes)</label><InputNumber v-model="geral.faixa" input-id="pg-faixa" :min="1" :max="100000" :disabled="!editarPadrao" :placeholder="padraoFaixa ? '' : 'Varia por faixa'" /></div>
      </div>
      <div class="faixas">
        <label>Faixas horárias</label>
        <div v-for="(f, i) in faixas" :key="f.id || `n${i}`" class="faixa-linha">
          <InputText v-model="f.horaInicio" type="time" :aria-label="`Início da faixa ${i + 1}`" :disabled="!editarPadrao" />
          <span>às</span>
          <InputText v-model="f.horaFim" type="time" :aria-label="`Fim da faixa ${i + 1}`" :disabled="!editarPadrao" />
          <label class="ativa"><Checkbox v-model="f.ativo" binary :input-id="`fx-at-${i}`" :disabled="!editarPadrao" /> Ativa</label>
        </div>
        <Button v-if="editarPadrao" type="button" label="Nova faixa" icon="pi pi-plus" size="small" text @click="novaFaixa" />
      </div>
      <div v-if="editarPadrao" class="rodape-form"><Button type="submit" label="Salvar padrão" icon="pi pi-check" class="btn-confirmar" :loading="enviando" :disabled="enviando" /></div>
    </form>
  </section>

  <section class="painel">
    <div class="titulo-secao"><h2>Por CD e loja</h2></div>
    <DataTable :value="linhas" data-key="chave" size="small" class="tabela-destinos" :loading="!pronto">
      <Column header="Destino"><template #body="{ data }"><span class="pilula" :class="data.tipo === 'CD' ? 'tipo-cd' : 'tipo-loja'">{{ data.tipo }}</span> {{ data.nome }}</template></Column>
      <Column header="Limite/dia"><template #body="{ data }"><span :class="{ herdado: !data.regra?.paletes }">{{ data.regra?.paletes ?? (data.tipo === 'CD' ? `padrão (${geral.dia ?? 480})` : 'sem limite') }}</span></template></Column>
      <Column header="Limite/faixa"><template #body="{ data }"><span :class="{ herdado: !data.regra?.paletesFaixa }">{{ data.tipo === 'Loja' ? '-' : data.regra?.paletesFaixa ?? `padrão (${padraoFaixa ?? 'por faixa'})` }}</span></template></Column>
      <Column header="Dias que recebe"><template #body="{ data }">{{ diasTexto(data.cal) }}</template></Column>
      <Column header="Feriados"><template #body="{ data }">{{ feriadoTexto(data.cal) }}</template></Column>
      <Column header="Exceções"><template #body="{ data }">{{ data.excecoes.length ? data.excecoes.slice(0, 2).map((e) => `${fmtData(e.data).slice(0, 5)} ${e.aberto ? 'aberto' : 'fechado'}`).join(', ') + (data.excecoes.length > 2 ? ` +${data.excecoes.length - 2}` : '') : 'Nenhuma' }}</template></Column>
      <Column v-if="editar" header="" style="width: 90px"><template #body="{ data }"><Button label="Editar" size="small" class="btn-secundario" :aria-label="`Editar ${data.nome}`" @click="abrir(data)" /></template></Column>
    </DataTable>
  </section>

  <Dialog v-model:visible="dialogo" modal :header="atual?.nome" :style="{ width: '480px' }" :breakpoints="{ '520px': '96vw' }">
    <form v-if="atual" class="form-destino" @submit.prevent="salvarDestino">
      <div class="linha2">
        <div class="campo"><label for="d-dia">Limite por dia</label><InputNumber v-model="form.dia" input-id="d-dia" :min="1" :max="100000" :placeholder="atual.loja ? 'Sem limite' : `Padrão (${geral.dia ?? 480})`" /></div>
        <div v-if="!atual.loja" class="campo"><label for="d-faixa">Limite por faixa</label><InputNumber v-model="form.faixa" input-id="d-faixa" :min="1" :max="100000" :placeholder="`Padrão (${padraoFaixa ?? 'por faixa'})`" /></div>
      </div>
      <div class="campo">
        <label>Dias que recebe</label>
        <div class="dias">
          <label v-for="(d, i) in DIAS_CURTOS" :key="d" class="dia" :class="{ marcado: form.dias[i] }"><Checkbox v-model="form.dias[i]" binary :input-id="`d-dia-${i}`" /> {{ d }}</label>
        </div>
      </div>
      <div class="linha-toggle"><ToggleSwitch v-model="form.recebeFeriado" input-id="d-feriado" /><label for="d-feriado">Recebe em feriado</label></div>
      <div class="campo">
        <label>Exceções</label>
        <div v-for="e in excecoesAtual" :key="e.id" class="excecao">
          <strong>{{ fmtData(e.data) }}</strong>
          <span class="pilula" :class="e.aberto ? 'ativo' : 'inativo'">{{ e.aberto ? 'Aberto' : 'Fechado' }}</span>
          <span class="texto-suave">{{ e.motivo }}</span>
          <Button icon="pi pi-times" text rounded size="small" :aria-label="`Remover exceção de ${fmtData(e.data)}`" @click="removerExcecao(e)" />
        </div>
        <div class="nova-excecao">
          <DatePicker v-model="nova.data" input-id="d-ex-data" date-format="dd/mm/yy" :min-date="deIso(hoje())" placeholder="Data" />
          <Select v-model="nova.aberto" input-id="d-ex-tipo" :options="[{ l: 'Fechado', v: false }, { l: 'Aberto', v: true }]" option-label="l" option-value="v" aria-label="Situação" />
          <InputText v-model="nova.motivo" maxlength="80" placeholder="Motivo" aria-label="Motivo" />
          <Button type="button" icon="pi pi-plus" class="btn-secundario" aria-label="Adicionar exceção" :disabled="enviando" @click="adicionarExcecao" />
        </div>
      </div>
      <div class="rodape-form">
        <Button type="button" label="Cancelar" class="btn-secundario" @click="dialogo = false" />
        <Button type="submit" label="Salvar" icon="pi pi-check" class="btn-confirmar" :loading="enviando" :disabled="enviando" />
      </div>
    </form>
  </Dialog>
</template>

<style scoped>
.padrao { display: flex; flex-direction: column; gap: 12px; }
.campos-padrao { display: flex; flex-wrap: wrap; gap: 12px; }
.faixas { display: flex; flex-direction: column; gap: 6px; }
.faixa-linha { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.faixa-linha .p-inputtext { width: 110px; }
.ativa { display: flex; align-items: center; gap: 4px; font-size: 0.85rem; }
.herdado { color: var(--gc-muted, #64748b); }
.tipo-cd { background: var(--gc-navy-soft, #e0e7ff); }
.form-destino { display: flex; flex-direction: column; gap: 12px; }
.linha2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.dias { display: flex; flex-wrap: wrap; gap: 6px; }
.dia { display: flex; align-items: center; gap: 4px; padding: 4px 8px; border: 1px solid var(--gc-border, #cbd5e1); border-radius: 6px; font-size: 0.85rem; }
.dia.marcado { border-color: var(--gc-navy, #1e3a8a); }
.linha-toggle { display: flex; align-items: center; gap: 8px; }
.excecao { display: flex; align-items: center; gap: 8px; }
.nova-excecao { display: grid; grid-template-columns: 130px 110px 1fr auto; gap: 6px; }
@media (max-width: 520px) {
  .linha2 { grid-template-columns: 1fr; }
  .nova-excecao { grid-template-columns: 1fr 1fr; }
}
</style>
