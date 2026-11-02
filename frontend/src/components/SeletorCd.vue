<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'

/** Seletor de centro de distribuição (multi-CD). Fica oculto quando só existe o CD padrão.
 *  A opção que junta os CDs mostra os códigos, como o "801-806" do Gestão CD. */
const modelo = defineModel({ type: Number, default: null })
const props = defineProps({ inputId: { type: String, default: 'cd' }, todos: { type: Boolean, default: true } })
const cds = ref([])
const juntos = computed(() => cds.value.map((c) => c.codigo).join('-'))
onMounted(async () => {
  try {
    cds.value = (await api('/cds')).filter((c) => c.ativo)
  } catch {
    cds.value = []
  }
})
</script>

<template>
  <div v-if="cds.length > 1" class="campo">
    <label :for="props.inputId">CD</label>
    <Select v-model="modelo" style="min-width: 210px" :input-id="props.inputId" :options="[...(props.todos ? [{ id: null, codigo: '', nome: juntos }] : []), ...cds]"
            :option-label="(c) => (c.codigo ? `${c.codigo} · ${c.nome}` : c.nome)" option-value="id" :placeholder="props.todos ? juntos : 'CD padrão'" />
  </div>
</template>
