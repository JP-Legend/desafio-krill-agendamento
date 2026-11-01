<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import CadastroCrud from '../components/CadastroCrud.vue'

const cds = ref([])
const campos = computed(() => [
  { campo: 'nome', rotulo: 'Loja', tipo: 'texto', obrigatorio: true, max: 80 },
  { campo: 'cdId', rotulo: 'CD que abastece', tipo: 'select', opcoes: cds.value.map((c) => ({ label: `CD ${c.codigo} · ${c.nome}`, value: c.id })), formatar: (l) => { const c = cds.value.find((x) => x.id === l.cdId); return c ? `CD ${c.codigo}` : '—' } },
  { campo: 'ativo', rotulo: 'Situação', tipo: 'ativo' }
])
const novo = () => ({ nome: '', cdId: null, ativo: true })

onMounted(async () => {
  cds.value = await api('/cds').catch(() => [])
})
</script>

<template>
  <CadastroCrud titulo="Lojas" recurso="/lojas" :campos="campos" :novo-padrao="novo" rotulo-novo="Nova loja" />
</template>
