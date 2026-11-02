<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pode, sessao } from '../sessao'
import { porModulo, usePermissoes } from '../composables/usePermissoes'

/** Ícone no título de cada tela: só ao clicar mostra as permissões que a tela e cada ação exigem. */
const route = useRoute()
const router = useRouter()
const tela = route.name
const telas = tela === 'agendamentos' ? ['agendamentos', 'detalhe'] : [tela]
const aberto = ref(false)
const { catalogo } = usePermissoes()
const exigidas = computed(() => {
  const p = route.matched.find((m) => m.name === tela)?.meta?.permissao
  return p == null ? [] : [].concat(p)
})
const daTela = computed(() => catalogo.value.filter((p) => exigidas.value.includes(p.codigo) || p.telas.some((t) => telas.includes(t))))
const grupos = computed(() => porModulo([...daTela.value].sort((a, b) => a.codigo - b.codigo)))

function editarPerfil() {
  aberto.value = false
  router.push({ name: 'perfis', query: { perfil: sessao.atual?.perfil } })
}
</script>

<template>
  <Button icon="pi pi-key" text rounded size="small" class="botao-permissoes" aria-label="Permissões da tela" title="Permissões da tela" @click="aberto = true" />
  <Dialog v-model:visible="aberto" modal header="Permissões da tela" :style="{ width: '480px' }" :breakpoints="{ '560px': '96vw' }">
    <p class="texto-suave">Perfil atual: <strong>{{ sessao.atual?.perfil }}</strong></p>
    <p v-if="!daTela.length" class="texto-suave">Tela liberada a todos os usuários</p>
    <template v-for="g in grupos" :key="g.modulo">
      <ul class="lista-permissoes">
        <li v-for="p in g.itens" :key="p.codigo" :class="{ negada: !pode(p.codigo) }">
          <span class="numero-permissao">{{ p.codigo }}</span>
          <span class="nome-permissao">{{ p.nome }}<small>{{ exigidas.includes(p.codigo) ? 'Abrir a tela' : 'Ação na tela' }} · {{ p.modulo }}</small></span>
          <Tag :value="pode(p.codigo) ? 'Liberada' : 'Sem acesso'" :severity="pode(p.codigo) ? 'success' : 'secondary'" />
        </li>
      </ul>
    </template>
    <template #footer>
      <Button label="Fechar" class="btn-secundario" @click="aberto = false" />
      <Button v-if="pode(1003)" label="Editar perfis" icon="pi pi-key" class="btn-primario" @click="editarPerfil" />
    </template>
  </Dialog>
</template>
