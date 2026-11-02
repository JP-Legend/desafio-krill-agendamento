<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { api } from '../api'
import { fmtDataHora } from '../utils'

/**
 * Sino do topo (.request-bell do Gestão CD): contador de não lidas, painel com as últimas notificações
 * e "Marcar lidas". Os avisos vêm do backend (gravados a cada evento: agendamento, link, chegada, capacidade).
 */
const INTERVALO_MS = 30000
const router = useRouter()
const toast = useToast()
const aberto = ref(false)
const naoLidos = ref(0)
const itens = ref([])
const casca = ref(null)
let ultimoId = null
let timer = 0

const ICONES = {
  'Caminhão na portaria': 'pi pi-truck', 'Novo agendamento': 'pi pi-calendar-plus', 'Agendamento pelo link do fornecedor': 'pi pi-link',
  'Capacidade excedida': 'pi pi-exclamation-triangle', 'Agendamento cancelado': 'pi pi-times-circle', 'Agendamento remarcado': 'pi pi-calendar',
  'Recebimento com divergência': 'pi pi-exclamation-circle'
}

async function carregar() {
  try {
    const c = await api('/avisos')
    const novos = ultimoId == null ? [] : c.itens.filter((i) => i.aviso.id > ultimoId && !i.lido)
    novos.slice(0, 3).reverse().forEach((i) => toast.add({ severity: 'info', summary: i.aviso.titulo, detail: i.aviso.mensagem, life: 8000 }))
    ultimoId = c.itens.length ? c.itens[0].aviso.id : ultimoId ?? 0
    naoLidos.value = c.naoLidos
    itens.value = c.itens
  } catch { /* sem rede: mantém a última lista */ }
}

async function marcarLidas() {
  try {
    const c = await api('/avisos/lidas', { method: 'POST' })
    naoLidos.value = c.naoLidos
    itens.value = c.itens
  } catch { /* tenta de novo no próximo clique */ }
}

function abrir(i) {
  aberto.value = false
  if (i.aviso.url) router.push(i.aviso.url)
}

function alternar() {
  aberto.value = !aberto.value
  if (aberto.value) carregar()
}
const fora = (e) => { if (casca.value && !casca.value.contains(e.target)) aberto.value = false }
const esc = (e) => { if (e.key === 'Escape') aberto.value = false }
const visivel = () => { if (document.visibilityState === 'visible') carregar() }
onMounted(() => {
  carregar()
  timer = setInterval(carregar, INTERVALO_MS)
  document.addEventListener('click', fora)
  document.addEventListener('keydown', esc)
  document.addEventListener('visibilitychange', visivel)
  window.addEventListener('krill:avisos', carregar)
  navigator.serviceWorker?.addEventListener?.('message', carregar)
})
onUnmounted(() => {
  clearInterval(timer)
  document.removeEventListener('click', fora)
  document.removeEventListener('keydown', esc)
  document.removeEventListener('visibilitychange', visivel)
  window.removeEventListener('krill:avisos', carregar)
  navigator.serviceWorker?.removeEventListener?.('message', carregar)
})
</script>

<template>
  <div ref="casca" class="request-bell" :class="{ 'is-open': aberto }">
    <button class="request-bell-button" type="button" title="Notificações" :aria-label="naoLidos ? `Notificações: ${naoLidos} não lida(s)` : 'Notificações'" :aria-expanded="aberto" @click.stop="alternar">
      <i class="pi pi-bell" />
    </button>
    <span v-if="naoLidos" class="request-bell-count" aria-hidden="true">{{ naoLidos > 99 ? '99+' : naoLidos }}</span>
    <div v-if="aberto" class="request-bell-panel" role="dialog" aria-label="Notificações">
      <div class="request-bell-heading">
        <strong>Notificações</strong>
        <button v-if="naoLidos" type="button" class="request-bell-acao" @click.stop="marcarLidas">Marcar lidas</button>
      </div>
      <button v-for="i in itens" :key="i.aviso.id" type="button" class="request-mini" :class="{ 'nao-lida': !i.lido }" @click.stop="abrir(i)">
        <i :class="ICONES[i.aviso.titulo] || 'pi pi-bell'" />
        <span>
          <strong>{{ i.aviso.titulo }}</strong>
          <span class="request-mini-texto">{{ i.aviso.mensagem }}</span>
          <small>{{ fmtDataHora(i.aviso.criadoEm) }}</small>
        </span>
      </button>
      <p v-if="!itens.length" class="request-bell-vazio">Nenhuma notificação.</p>
    </div>
  </div>
</template>
