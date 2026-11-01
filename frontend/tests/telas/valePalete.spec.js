import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import { flushPromises, logar, montar, sair, simularApi } from '../montar'
import ValePaleteView from '../../src/views/ValePaleteView.vue'
import ValesPaleteView from '../../src/views/ValesPaleteView.vue'

const TIPOS = [{ codigo: 'PBR', nome: 'PBR', ativo: true, ordem: 1 }]
const PNG = 'data:image/png;base64,QUJD'
const VALE = { id: 9, codigo: 'VP-9', data: '2026-11-01', fornecedor: 'Bebidas Vale', placa: 'ABC1D23', motorista: 'João Motorista', status: 'PENDENTE', entregues: { PBR: 10 }, devolvidos: {}, saldo: { PBR: 10 }, totalEntregues: 10, totalSaldo: 10, assinaturaMotorista: null, assinaturaConferente: null }

beforeAll(() => {
  HTMLCanvasElement.prototype.getContext = () => ({ beginPath() {}, moveTo() {}, lineTo() {}, stroke() {}, clearRect() {} })
  HTMLCanvasElement.prototype.toDataURL = () => PNG
})

async function desenhar(canvas) {
  await canvas.trigger('mousedown', { clientX: 5, clientY: 5 })
  await canvas.trigger('mousemove', { clientX: 30, clientY: 20 })
  await canvas.trigger('mouseup')
}

describe('vale-palete: assinaturas como no Gestão CD', () => {
  afterEach(sair)

  it('emite o vale já com as assinaturas do motorista e do conferente', async () => {
    logar('ADMIN')
    const api = simularApi({
      'GET /tipos-palete': TIPOS,
      'GET /vales-palete/configuracao': { exigirDuasAssinaturas: true },
      'POST /vales-palete': VALE,
      'PUT /vales-palete/9/assinaturas/MOTORISTA': { ...VALE, assinaturaMotorista: { nome: 'João Motorista', imagem: PNG, em: '2026-11-01T20:00:00Z' } },
      'PUT /vales-palete/9/assinaturas/CONFERENTE': { ...VALE, status: 'ASSINADO' }
    })
    const { w, toast } = await montar(ValePaleteView, { rota: '/vales-palete/novo', rotas: [{ path: '/vales-palete/novo', name: 'vale-novo' }, '/:p(.*)*'].map((r) => (typeof r === 'string' ? { path: r, component: { render: () => null } } : { ...r, component: { render: () => null } })) })
    expect(w.text()).toContain('Assinatura do motorista')
    expect(w.text()).toContain('Assinatura do conferente')
    expect(w.text()).toContain('obrigatória')
    await w.find('#vfo').setValue('Bebidas Vale')
    await w.find('#vmo').setValue('João Motorista')
    const tela = w.findComponent(ValePaleteView)
    tela.vm.$.setupState.form.quantidades.PBR = 10
    const canvases = w.findAll('canvas')
    await desenhar(canvases[0])
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(toast()).toContain('Colete as duas assinaturas')
    await desenhar(canvases[1])
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(toast()).toContain('Informe o nome do conferente')
    await w.find('#an-CONFERENTE').setValue('Carla Conferente')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(api.ultima('PUT /vales-palete/9/assinaturas/MOTORISTA').corpo).toEqual({ nome: 'João Motorista', imagem: PNG })
    expect(api.ultima('PUT /vales-palete/9/assinaturas/CONFERENTE').corpo).toEqual({ nome: 'Carla Conferente', imagem: PNG })
    expect(toast()).toContain('Vale VP-9 emitido e assinado')
  })

  it('usuário comum não vê os quadros de assinatura', async () => {
    logar('USUARIO')
    simularApi({ 'GET /tipos-palete': TIPOS, 'GET /vales-palete/9': { ...VALE, assinaturaMotorista: { nome: 'João', imagem: PNG, em: '2026-11-01T20:00:00Z' } } })
    const { w } = await montar(ValePaleteView, { rota: '/vales-palete/9', rotas: [{ path: '/vales-palete/:id(\\d+)', name: 'vale', component: { render: () => null } }] })
    expect(w.find('img[alt="Assinatura Motorista"]').exists()).toBe(true)
    expect(w.findAll('button').some((b) => b.text() === 'Assinar')).toBe(false)
  })

  it('lista mostra o selo de assinaturas pendentes', async () => {
    logar('ADMIN')
    simularApi({ 'GET /vales-palete': [VALE, { ...VALE, id: 10, codigo: 'VP-10', assinaturaMotorista: {}, assinaturaConferente: {} }], 'GET /tipos-palete': TIPOS })
    const { w } = await montar(ValesPaleteView)
    expect(w.text()).toContain('Pendente: Motorista e Conferente')
    expect(w.text()).toContain('Assinada (2/2)')
  })
})
