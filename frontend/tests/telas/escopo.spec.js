import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, logar, montar, sair, simularApi } from '../montar'
import SeletorDestino from '../../src/components/SeletorDestino.vue'
import EscolhaHorario from '../../src/components/EscolhaHorario.vue'

afterEach(() => { sair(); document.body.innerHTML = '' })
const CDS = [{ id: 1, codigo: '806', nome: 'CD Rede Krill', ativo: true }, { id: 2, codigo: '807', nome: 'CD Interior', ativo: true }]
const LOJAS = [{ id: 1, nome: 'Loja 14 - KRILL BERTIOGA', ativo: true }]

describe('seletor de CD/loja dentro da tela', () => {
  it('usuário com vários destinos vê só os dele', async () => {
    logar('USUARIO', { destinos: { todos: false, cds: [1], lojas: ['Loja 14 - KRILL BERTIOGA'] } })
    simularApi({ 'GET /cds': CDS, 'GET /lojas': LOJAS })
    const { tela, w } = await montar(SeletorDestino, { props: { inputId: 'x' } })
    await flushPromises()
    expect(tela.vm.opcoes.map((o) => o.rotulo)).toEqual(['CD 806 · CD Rede Krill', 'Loja 14 - KRILL BERTIOGA'])
    expect(w.find('label[for="x"]').exists()).toBe(true)
  })

  it('usuário com um CD só não vê seletor e já fica no CD dele', async () => {
    logar('USUARIO', { destinos: { todos: false, cds: [2], lojas: [] } })
    simularApi({ 'GET /cds': CDS, 'GET /lojas': LOJAS })
    let modelo = null
    const { w } = await montar(SeletorDestino, { props: { inputId: 'x', 'onUpdate:modelValue': (v) => { modelo = v } } })
    await flushPromises()
    expect(w.find('label[for="x"]').exists()).toBe(false)
    expect(modelo).toEqual({ cdId: 2, loja: null })
  })

  it('usuário de loja fica na loja dele', async () => {
    logar('USUARIO', { destinos: { todos: false, cds: [], lojas: ['Loja 14 - KRILL BERTIOGA'] } })
    simularApi({ 'GET /cds': CDS, 'GET /lojas': LOJAS })
    let modelo = null
    await montar(SeletorDestino, { props: { 'onUpdate:modelValue': (v) => { modelo = v } } })
    await flushPromises()
    expect(modelo).toEqual({ cdId: null, loja: 'Loja 14 - KRILL BERTIOGA' })
  })
})

describe('remarcar em loja sem limite', () => {
  it('capacidade 0 = sem limite: faixa livre para o fornecedor', async () => {
    const dias = [{ data: '2026-11-03', capacidade: 0, paletesOcupados: 30, faixas: [{ faixaHorariaId: 1, descricao: '08:00 - 10:00', capacidade: 120, paletesOcupados: 30 }] },
      { data: '2026-11-02', capacidade: 480, paletesOcupados: 0, faixas: [{ faixaHorariaId: 1, descricao: '08:00 - 10:00', capacidade: 120, paletesOcupados: 0 }] }]
    const { w } = await montar(EscolhaHorario, { props: { dias, paletes: 10, somenteComVaga: true, fechados: { '2026-11-02': 'Feriado: Finados' } } })
    expect(w.text()).toContain('livre')
    expect(w.text()).toContain('Não recebe · Feriado: Finados')
    expect(w.findAll('button.faixa').length).toBe(1)
    expect(w.find('button.faixa').attributes('disabled')).toBeUndefined()
  })
})
