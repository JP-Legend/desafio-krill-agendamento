<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { sessao, pode, logado, encerrarSessao, atualizarSessao } from './sessao'
import { api } from './api'
import BuscaTopo from './components/BuscaTopo.vue'
import SinoAvisos from './components/SinoAvisos.vue'
import { ativarPush, desativarPush, inscricaoAtual, pushSuportado } from './push'
import { useNotificar } from './composables/useNotificar'
import { menuItens } from './menu'
import { abrirAba, chaveCache, limparAbas } from './abas'
import AbasMdi from './components/AbasMdi.vue'

const route = useRoute()
const router = useRouter()
const menuUsuario = ref(false)
/** Iniciais do nome (primeiro e último), como o avatar do Gestão CD em produção. */
const inicial = computed(() => {
  const partes = (sessao.atual?.nome || sessao.atual?.usuario || '?').trim().split(/\s+/)
  return ((partes[0][0] || '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase()
})
const primeiroNome = computed(() => (sessao.atual?.nome || sessao.atual?.usuario || '').trim().split(/\s+/)[0])
const fecharMenuUsuario = () => { menuUsuario.value = false }
watch(() => route.fullPath, () => { if (logado.value) abrirAba(route) }, { immediate: true })
const notificar = useNotificar()
const menuAberto = ref(false)
const online = ref(navigator.onLine)
const pushAtivo = ref(false)
const mostrarTopo = computed(() => !route.meta.publica)

const itens = computed(() => menuItens())
const itensPlanos = computed(() => itens.value.flatMap((g) => g.itens))

const atualizarRede = () => { online.value = navigator.onLine }
onMounted(async () => {
  window.addEventListener('online', atualizarRede)
  document.addEventListener('click', fecharMenuUsuario)
  window.addEventListener('offline', atualizarRede)
  sincronizarPush()
  if (logado.value) api('/auth/eu').then(atualizarSessao).catch(() => {})
})
onUnmounted(() => {
  window.removeEventListener('online', atualizarRede)
  document.removeEventListener('click', fecharMenuUsuario)
  window.removeEventListener('offline', atualizarRede)
})

/** Mesma regra do Gestão CD: ativo = há inscrição push neste navegador E a permissão está concedida. */
async function sincronizarPush() {
  const sub = await inscricaoAtual().catch(() => null)
  pushAtivo.value = !!sub && typeof Notification !== 'undefined' && Notification.permission === 'granted'
}

function abrirMenuUsuario() {
  menuUsuario.value = !menuUsuario.value
  if (menuUsuario.value) sincronizarPush()
}

async function alternarPush() {
  const desativar = pushAtivo.value
  try {
    if (desativar) await desativarPush()
    else await ativarPush()
  } catch (e) {
    notificar.erro(e)
  }
  await sincronizarPush()
  if (desativar && !pushAtivo.value) notificar.sucesso('Este navegador não receberá mais avisos', 'Notificações desativadas')
  else if (!desativar && pushAtivo.value) notificar.sucesso('Você receberá avisos de chegada na portaria, novos agendamentos e capacidade excedida', 'Notificações ativadas')
}

async function sair() {
  await api('/auth/logout', { method: 'POST' }).catch(() => {})
  encerrarSessao()
  limparAbas()
  router.push({ name: 'login' })
}
</script>

<template>
  <Toast position="top-right" />
  <template v-if="mostrarTopo">
    <header class="topo">
      <button class="btn-topo btn-hamburguer" aria-label="Abrir menu" @click="menuAberto = true"><i class="pi pi-bars" /></button>
      <RouterLink to="/inicio" class="marca">
        <img src="/img/rede-krill-logo.png" alt="Rede Krill" width="570" height="528" />
        <span>Agendamento Rede Krill</span>
      </RouterLink>
      <div class="espaco" />
      <div class="topo-acoes">
      <BuscaTopo />
      <SinoAvisos v-if="pode(1102)" />
      <div class="user-menu" :class="{ aberto: menuUsuario }">
        <button type="button" class="user-trigger" aria-haspopup="true" :aria-expanded="menuUsuario" :title="`${sessao.atual?.nome} (${sessao.atual?.usuario})`" @click.stop="abrirMenuUsuario">
          <span class="user-avatar" aria-hidden="true">{{ inicial }}</span>
          <span class="user-trigger-label">{{ sessao.atual?.nome }}</span>
          <span class="user-trigger-label-curto">{{ primeiroNome }}</span>
        </button>
        <div v-if="menuUsuario" class="user-dropdown" role="menu" @click="menuUsuario = false">
          <button v-if="pushSuportado()" type="button" role="menuitem" @click="alternarPush">{{ pushAtivo ? 'Desativar notificações' : 'Ativar notificações' }}<span>&gt;</span></button>
          <RouterLink to="/trocar-senha" role="menuitem">Trocar senha<span>&gt;</span></RouterLink>
          <button type="button" class="sair" role="menuitem" @click="sair">Sair<span>&gt;</span></button>
        </div>
      </div>
      </div>
    </header>
    <div v-if="!online" class="offline">Sem conexão. Os dados serão atualizados quando a rede voltar.</div>
    <AbasMdi />
    <Drawer v-model:visible="menuAberto" header="Menu">
      <nav class="menu-lateral" @click="menuAberto = false">
        <RouterLink to="/inicio"><i class="pi pi-home" />Início</RouterLink>
        <template v-for="g in itens" :key="g.grupo">
          <div class="grupo">{{ g.grupo }}</div>
          <RouterLink v-for="i in g.itens" :key="i.to" :to="i.to"><i :class="i.icon" />{{ i.label }}</RouterLink>
        </template>
      </nav>
    </Drawer>
    <main class="conteudo">
      <RouterView v-slot="{ Component, route: r }">
        <KeepAlive :max="12"><component :is="Component" :key="chaveCache(r)" /></KeepAlive>
      </RouterView>
    </main>
  </template>
  <RouterView v-else />
</template>
