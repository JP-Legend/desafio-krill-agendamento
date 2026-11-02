import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, logar, montar, sair, simularApi } from '../montar'
import ValePaleteImpressaoView from '../../src/views/ValePaleteImpressaoView.vue'
import ValesPaleteView from '../../src/views/ValesPaleteView.vue'
import { code128Svg } from '../../src/codigosBarras'

afterEach(() => { sair(); vi.restoreAllMocks(); document.body.innerHTML = '' })

const vale = { id: 5, codigo: 'VP-20260820-0007', codigoLeitura: 'KRILL-VP-20260820-0007', status: 'PENDENTE', fornecedor: 'Bebidas Vale', data: '2026-08-20', criadoEm: '2026-08-20T10:00:00-03:00', criadoPor: 'admin', entregues: { PBR: 10 }, devolvidos: { PBR: 2 }, saldo: { PBR: 8 } }

describe('impressão do vale-palete (igual ao Gestão CD)', () => {
  it('código de barras e QR com empresa+vale, sem nome do sistema, e abre a impressão', async () => {
    logar('USUARIO')
    const print = vi.fn()
    vi.stubGlobal('print', print)
    simularApi({ 'GET /vales-palete/5': vale, 'GET /cds': [] })
    const { w } = await montar(ValePaleteImpressaoView, { rota: '/vales-palete/5/imprimir?auto=1', rotas: [{ path: '/vales-palete/:id/imprimir', component: { render: () => null } }, { path: '/:p(.*)*', component: { render: () => null } }] })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 400))
    const html = w.html()
    expect(html).toContain('KRILL-VP-20260820-0007')
    expect(w.findAll('svg').length).toBeGreaterThanOrEqual(2)
    expect(w.text()).toContain('Bebidas Vale')
    expect(w.text()).not.toContain('Agendamento Rede Krill')
    expect(w.text()).not.toContain('http')
    expect(document.head.innerHTML).toContain('margin: 0')
    expect(print).toHaveBeenCalled()
  })

  it('Code128 gera barras para o código do vale', () => {
    expect(code128Svg('KRILL-VP-20260820-0007')).toContain('<rect')
  })
})

describe('leitura do vale na lista', () => {
  it('bipar o código abre o vale direto na devolução', async () => {
    logar('USUARIO')
    const api = simularApi({ 'GET /vales-palete': [], 'GET /vales-palete/leitura': { id: 5 } })
    const { w, router } = await montar(ValesPaleteView)
    await w.find('.campo-leitura').setValue('KRILL-VP-20260820-0007')
    await w.find('form.busca-codigo').trigger('submit')
    await flushPromises()
    expect(api.ultima('GET /vales-palete/leitura').params.codigo).toBe('KRILL-VP-20260820-0007')
    expect(router.currentRoute.value.fullPath).toBe('/vales-palete/5?devolver=1')
  })
})

import EscolhaHorario from '../../src/components/EscolhaHorario.vue'
describe('dias fechados do calendário', () => {
  it('a grade marca o dia fechado com o motivo e não deixa escolher', async () => {
    const dias = [{ data: '2026-11-02', capacidade: 480, paletesOcupados: 0, faixas: [{ faixaHorariaId: 1, descricao: '08:00 - 10:00', capacidade: 120, paletesOcupados: 0 }] }]
    const { w } = await montar(EscolhaHorario, { props: { dias, paletes: 10, fechados: { '2026-11-02': 'Feriado: Finados' } } })
    expect(w.text()).toContain('Não recebe · Feriado: Finados')
  })
})
