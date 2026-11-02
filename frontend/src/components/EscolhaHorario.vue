<script setup>
import { computed } from 'vue'
import { diaSemana, fmtData } from '../utils'

/**
 * Grade de dias × faixas para remarcar (mesmo visual do painel de ocupação).
 * somenteComVaga: fornecedor só escolhe faixa com vaga; o CD vê tudo (excesso só alerta).
 */
const props = defineProps({
  dias: { type: Array, default: () => [] },
  paletes: { type: Number, required: true },
  atual: { type: Object, default: null },
  somenteComVaga: { type: Boolean, default: false },
  /** { 'AAAA-MM-DD': 'motivo' } dos dias em que o destino não recebe (calendário de recebimento). */
  fechados: { type: Object, default: () => ({}) }
})
const modelo = defineModel({ type: Object, default: null })

const linhas = computed(() =>
  props.dias.map((d) => ({
    data: d.data,
    fechado: props.fechados[d.data] || null,
    faixas: d.faixas.map((f) => {
      const livreFaixa = d.capacidade ? f.capacidade - f.paletesOcupados : Infinity
      // capacidade 0 = destino sem limite cadastrado (loja): sempre cabe
      const livreDia = d.capacidade ? d.capacidade - d.paletesOcupados : Infinity
      const cabe = props.paletes <= livreFaixa && props.paletes <= livreDia
      const atual = props.atual && props.atual.data === d.data && props.atual.faixaHorariaId === f.faixaHorariaId
      return { ...f, livre: Math.max(0, Math.min(livreFaixa, livreDia)), cabe, atual, bloqueada: atual || (props.somenteComVaga && !cabe) }
    })
  }))
)
function escolher(d, f) {
  if (!f.bloqueada) modelo.value = { data: d.data, faixaHorariaId: f.faixaHorariaId, descricao: f.descricao, cabe: f.cabe }
}
const selecionada = (d, f) => modelo.value?.data === d.data && modelo.value?.faixaHorariaId === f.faixaHorariaId
</script>

<template>
  <div class="escolha">
    <div v-for="d in linhas" :key="d.data" class="dia">
      <span class="rotulo">{{ diaSemana(d.data) }} <strong>{{ fmtData(d.data).slice(0, 5) }}</strong></span>
      <div v-if="d.fechado" class="fechado" role="note"><i class="pi pi-ban" /> Não recebe · {{ d.fechado }}</div>
      <button v-for="f in d.fechado ? [] : d.faixas" :key="f.faixaHorariaId" type="button" class="faixa"
              :class="{ cheia: !f.cabe, atual: f.atual, sel: selecionada(d, f) }" :disabled="f.bloqueada"
              :aria-label="`${fmtData(d.data)} ${f.descricao}`" @click="escolher(d, f)">
        <span>{{ f.descricao }}</span>
        <small>{{ f.atual ? 'atual' : !Number.isFinite(f.livre) ? 'livre' : f.cabe ? `${f.livre} livres` : 'sem vaga' }}</small>
      </button>
    </div>
  </div>
</template>

<style scoped>
.escolha { display: flex; flex-direction: column; gap: 6px; max-height: 360px; overflow: auto; }
.dia { display: grid; grid-template-columns: 90px repeat(4, 1fr); gap: 6px; align-items: stretch; }
.rotulo { font-size: 12px; display: flex; flex-direction: column; justify-content: center; text-transform: capitalize; }
.faixa { border: 1px solid var(--gc-pill-ok-border); background: var(--gc-toast-ok-bg); color: var(--gc-heading); border-radius: 6px; padding: 5px 4px; cursor: pointer; display: flex; flex-direction: column; font: inherit; font-size: 12px; }
.faixa small { color: var(--cor-texto-suave); }
.faixa.cheia { border-color: var(--gc-toast-error-border); background: var(--gc-toast-error-bg); }
.faixa.atual { border-style: dashed; background: var(--gc-input-disabled); }
.faixa.sel { outline: 2px solid var(--gc-blue-700); background: var(--gc-toast-info-bg); }
.faixa:disabled { cursor: not-allowed; opacity: 0.55; }
.fechado { grid-column: 2 / -1; display: flex; align-items: center; gap: 6px; min-height: 40px; padding: 0 10px; border: 1px dashed var(--gc-border); border-radius: 6px; background: var(--gc-input-disabled); color: var(--cor-texto-suave); font-size: 12px; }
@media (max-width: 560px) { .dia { grid-template-columns: 1fr 1fr; } .rotulo { grid-column: span 2; flex-direction: row; gap: 4px; } .fechado { grid-column: span 2; } }
</style>
