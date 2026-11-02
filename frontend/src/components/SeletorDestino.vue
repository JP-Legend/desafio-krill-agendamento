<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api'
import { destinos } from '../sessao'

/**
 * Seletor de CD/loja dentro da tela. Lista só os destinos do usuário (quem vê todos: todos os CDs e lojas ativos).
 * Usuário com um destino só não vê seletor: a tela já abre no destino dele.
 * Modelo: { cdId, loja } (os dois nulos = todos os destinos do usuário, quando `todos` está ligado).
 */
const modelo = defineModel({ type: Object, default: () => ({ cdId: null, loja: null }) })
const props = defineProps({
  inputId: { type: String, default: 'destino' },
  /** Opção "Todos os meus destinos" (listas). Telas de um destino só (ocupação, pátio) desligam. */
  todos: { type: Boolean, default: true },
  /** Telas só de CD (pátio, portaria): lojas não aparecem. */
  somenteCd: { type: Boolean, default: false }
})
const cds = ref([])
const lojas = ref([])
const opcoes = computed(() => [
  ...cds.value.map((c) => ({ chave: `cd:${c.id}`, rotulo: `CD ${c.codigo} · ${c.nome}`, cdId: c.id, loja: null })),
  ...(props.somenteCd ? [] : lojas.value.map((l) => ({ chave: `loja:${l}`, rotulo: l, cdId: null, loja: l })))
])
const lista = computed(() => [...(props.todos && opcoes.value.length > 1 ? [{ chave: '', rotulo: destinos().todos ? 'Todos os destinos' : 'Meus destinos', cdId: null, loja: null }] : []), ...opcoes.value])
const chave = computed({
  get: () => (modelo.value?.loja ? `loja:${modelo.value.loja}` : modelo.value?.cdId ? `cd:${modelo.value.cdId}` : ''),
  set: (k) => {
    const o = lista.value.find((x) => x.chave === k) || { cdId: null, loja: null }
    modelo.value = { cdId: o.cdId, loja: o.loja }
  }
})
const visivel = computed(() => opcoes.value.length > 1)
function padrao() {
  // um destino só, ou tela sem "todos": já seleciona o primeiro
  if (opcoes.value.length && (opcoes.value.length === 1 || !props.todos) && !chave.value) {
    const o = opcoes.value[0]
    modelo.value = { cdId: o.cdId, loja: o.loja }
  }
}
watch(opcoes, padrao)
onMounted(async () => {
  const d = destinos()
  try {
    const [c, l] = await Promise.all([api('/cds'), props.somenteCd ? Promise.resolve([]) : api('/lojas').catch(() => [])])
    cds.value = c.filter((x) => x.ativo && (d.todos || d.cds.includes(x.id)))
    lojas.value = l.filter((x) => x.ativo !== false).map((x) => x.nome || x).filter((n) => d.todos || d.lojas.includes(n))
  } catch {
    cds.value = []
  }
})
defineExpose({ opcoes })
</script>

<template>
  <div v-if="visivel" class="campo seletor-destino">
    <label :for="props.inputId">{{ props.somenteCd ? 'CD' : 'CD ou loja' }}</label>
    <Select v-model="chave" :input-id="props.inputId" :options="lista" option-label="rotulo" option-value="chave" filter />
  </div>
</template>

<style scoped>
.seletor-destino { min-width: 230px; }
</style>
