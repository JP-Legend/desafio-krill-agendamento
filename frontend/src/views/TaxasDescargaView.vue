<script setup>
import CadastroCrud from '../components/CadastroCrud.vue'
import { fmtMoeda } from '../utils'

const campos = [
  { campo: 'modal', rotulo: 'Modal', tipo: 'texto', obrigatorio: true, max: 20 },
  { campo: 'nome', rotulo: 'Nome', tipo: 'texto', obrigatorio: true, max: 40 },
  { campo: 'valorPorPalete', rotulo: 'Taxa por palete', tipo: 'numero', obrigatorio: true, min: 0, maxNum: 99999, decimais: 2, formatar: (r) => fmtMoeda(r.valorPorPalete) },
  { campo: 'ativo', rotulo: 'Situação', tipo: 'ativo' }
]
const novo = () => ({ modal: '', nome: '', valorPorPalete: 0, ativo: true })
const validar = (f) => (/^[A-Za-z0-9_ ]{2,20}$/.test((f.modal || '').trim()) ? null : { modal: 'Use de 2 a 20 letras, números ou _' })
</script>

<template>
  <CadastroCrud titulo="Taxa de descarga por modal" recurso="/taxas-descarga" :campos="campos" :novo-padrao="novo" :validar-extra="validar" rotulo-novo="Novo modal">
  </CadastroCrud>
</template>
