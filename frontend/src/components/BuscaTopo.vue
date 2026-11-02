<script setup>
import { computed, onMounted, onUnmounted, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { menuItens } from '../menu'

/**
 * Lupa do topo (OmniSearch do Gestão CD): recolhida num ícone; clique, Ctrl+K ou "/" expande o campo na barra;
 * digitar busca telas do menu e registros (300 ms); ↑/↓ escolhem, Enter abre na aba MDI;
 * Escape limpa o termo e, vazio, recolhe; clique fora recolhe.
 */
const ATRASO_MS = 300
const router = useRouter()
const expandido = ref(false)
const termo = ref('')
const registros = ref([])
const buscando = ref(false)
const ativo = ref(0)
const casca = ref(null)
const campo = ref(null)
let timer = 0
let sequencia = 0

const normal = (s) => (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()

const telas = computed(() => {
  const t = normal(termo.value.trim())
  if (!t) return []
  return menuItens().flatMap((g) => g.itens.map((i) => ({ grupo: 'Telas', titulo: i.label, detalhe: g.grupo, url: i.to, icone: i.icon })))
    .filter((i) => normal(`${i.titulo} ${i.detalhe}`).includes(t)).slice(0, 6)
})
const ICONES = { Agendamentos: 'pi pi-calendar', 'Vales-palete': 'pi pi-ticket', Fornecedores: 'pi pi-users', 'Centros de distribuição': 'pi pi-building', Lojas: 'pi pi-shop' }
const resultados = computed(() => [...telas.value, ...registros.value.map((r) => ({ ...r, icone: ICONES[r.grupo] || 'pi pi-search' }))])
const grupos = computed(() => {
  const mapa = []
  resultados.value.forEach((r, indice) => {
    let g = mapa.find((x) => x.grupo === r.grupo)
    if (!g) mapa.push((g = { grupo: r.grupo, itens: [] }))
    g.itens.push({ ...r, indice })
  })
  return mapa
})
const painelAberto = computed(() => expandido.value && termo.value.trim().length > 0)

function campoDeTexto(alvo) {
  if (!(alvo instanceof HTMLElement)) return false
  return ['INPUT', 'TEXTAREA', 'SELECT'].includes(alvo.tagName) || alvo.isContentEditable
}

async function expandir() {
  expandido.value = true
  await nextTick()
  campo.value?.focus()
}

function recolher() {
  expandido.value = false
  campo.value?.blur()
}

function aoDigitar() {
  ativo.value = 0
  clearTimeout(timer)
  const t = termo.value.trim()
  if (t.length < 2) {
    registros.value = []
    buscando.value = false
    return
  }
  buscando.value = true
  timer = setTimeout(async () => {
    const minha = ++sequencia
    try {
      const r = await api('/busca', { params: { q: t } })
      if (minha === sequencia) registros.value = r
    } catch {
      if (minha === sequencia) registros.value = []
    } finally {
      if (minha === sequencia) buscando.value = false
    }
  }, ATRASO_MS)
}

function abrir(r) {
  if (!r) return
  router.push(r.url)
  termo.value = ''
  registros.value = []
  recolher()
}

function teclas(e) {
  if (e.key === 'ArrowDown') { e.preventDefault(); ativo.value = Math.min(ativo.value + 1, resultados.value.length - 1) }
  else if (e.key === 'ArrowUp') { e.preventDefault(); ativo.value = Math.max(ativo.value - 1, 0) }
  else if (e.key === 'Enter') { e.preventDefault(); abrir(resultados.value[ativo.value]) }
  else if (e.key === 'Escape') {
    e.preventDefault()
    if (termo.value) { termo.value = ''; aoDigitar(); return }
    recolher()
  }
}

function fora(e) {
  if (casca.value && !casca.value.contains(e.target)) recolher()
}
function atalho(e) {
  const ctrlK = (e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k'
  const barra = e.key === '/' && !campoDeTexto(e.target) && !e.ctrlKey && !e.metaKey && !e.altKey
  if (ctrlK || barra) { e.preventDefault(); expandir() }
}
onMounted(() => {
  document.addEventListener('click', fora)
  window.addEventListener('keydown', atalho)
})
onUnmounted(() => {
  document.removeEventListener('click', fora)
  window.removeEventListener('keydown', atalho)
  clearTimeout(timer)
})
defineExpose({ expandir })
</script>

<template>
  <div ref="casca" class="busca-topo" :class="{ 'busca-topo--aberta': expandido }" @click="!expandido && expandir()">
    <form class="busca-topo-form" role="search" @submit.prevent="abrir(resultados[ativo])">
      <span class="busca-topo-lupa" aria-hidden="true"><i class="pi pi-search" /></span>
      <label class="sr-only" for="busca-topo-campo">Buscar telas, agendamentos, fornecedores, lojas e vales</label>
      <input id="busca-topo-campo" ref="campo" v-model="termo" class="busca-topo-campo" type="search" placeholder="Busca rápida" autocomplete="off"
             inputmode="search" aria-keyshortcuts="Control+K" role="combobox" :aria-expanded="painelAberto" aria-controls="busca-topo-lista"
             @focus="expandido = true" @input="aoDigitar" @keydown="teclas" />
      <button v-if="expandido" type="button" class="busca-topo-fechar" aria-label="Fechar busca" @click.stop="termo = ''; aoDigitar(); recolher()"><i class="pi pi-times" /></button>
    </form>
    <div v-if="painelAberto" id="busca-topo-lista" class="busca-topo-painel" role="listbox">
      <template v-for="g in grupos" :key="g.grupo">
        <div class="busca-topo-grupo">{{ g.grupo }}</div>
        <button v-for="r in g.itens" :key="`${r.grupo}-${r.url}`" type="button" role="option" class="busca-topo-item" :class="{ ativo: r.indice === ativo }"
                :aria-selected="r.indice === ativo" @mouseenter="ativo = r.indice" @click.stop="abrir(r)">
          <i :class="r.icone" />
          <span><strong>{{ r.titulo }}</strong><small>{{ r.detalhe }}</small></span>
        </button>
      </template>
      <p v-if="buscando" class="busca-topo-vazio">Buscando…</p>
      <p v-else-if="!resultados.length" class="busca-topo-vazio">Nada encontrado para “{{ termo.trim() }}”</p>
      <p class="busca-topo-dica"><kbd>↑</kbd><kbd>↓</kbd> escolher · <kbd>Enter</kbd> abrir · <kbd>Esc</kbd> limpar · <kbd>Ctrl</kbd>+<kbd>K</kbd></p>
    </div>
  </div>
</template>
