import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CampoHora from '../src/components/CampoHora.vue'

const InputText = { props: ['modelValue'], template: '<input :value="modelValue" />' }

describe('CampoHora', () => {
  it('formata em 24h enquanto digita e completa ao sair', async () => {
    const w = mount(CampoHora, { props: { modelValue: '07:00:00' }, global: { stubs: { InputText } } })
    const i = w.find('input')
    expect(i.element.value).toBe('07:00')
    i.element.value = '1830'
    await i.trigger('input')
    expect(w.emitted('update:modelValue').at(-1)).toEqual(['18:30'])
    i.element.value = '9'
    await i.trigger('input')
    await i.trigger('blur')
    expect(w.emitted('update:modelValue').at(-1)).toEqual(['09:00'])
  })
})
