<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { abas, fecharAba, moverAba, rotuloDaRota } from '../abas'

const route = useRoute()
const router = useRouter()
const arrastando = ref(-1)
const sobre = ref(-1)

function fechar(chave) {
  const destino = fecharAba(chave)
  if (route.path === chave) router.push(destino)
}
function soltar(i) {
  moverAba(arrastando.value, i)
  arrastando.value = -1
  sobre.value = -1
}
</script>

<template>
  <nav class="mdi-barra" aria-label="Abas abertas">
    <div class="mdi-abas" role="tablist">
      <RouterLink to="/inicio" class="mdi-aba mdi-aba-fixa" :class="{ ativa: route.path === '/inicio' }" role="tab" title="Início">
        <i class="pi pi-home" /><span>Início</span>
      </RouterLink>
      <RouterLink
        v-for="(a, i) in abas.lista" :key="a.chave" :to="a.caminho" class="mdi-aba" role="tab" draggable="true" :title="a.rotulo"
        :class="{ ativa: route.path === a.chave, arrastando: arrastando === i, sobre: sobre === i && arrastando !== i }"
        @auxclick.prevent="(e) => e.button === 1 && fechar(a.chave)" @mousedown="(e) => e.button === 1 && e.preventDefault()"
        @dragstart="arrastando = i" @dragover.prevent="sobre = i" @dragleave="sobre = -1" @drop.prevent="soltar(i)" @dragend="arrastando = -1; sobre = -1"
      >
        <span class="mdi-rotulo">{{ a.rotulo }}</span>
        <button type="button" class="mdi-fechar" :aria-label="`Fechar ${a.rotulo}`" @click.prevent.stop="fechar(a.chave)"><i class="pi pi-times" /></button>
      </RouterLink>
    </div>
  </nav>
  <div class="mdi-celular">
    <RouterLink v-if="route.path !== '/inicio'" to="/inicio" class="mdi-home" aria-label="Início"><i class="pi pi-home" /></RouterLink>
    <strong>{{ rotuloDaRota(route) }}</strong>
  </div>
</template>
