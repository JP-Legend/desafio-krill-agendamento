import { beforeEach, describe, expect, it } from 'vitest'
import { abas, abrirAba, chaveCache, fecharAba, limparAbas, moverAba } from '../src/abas'

const r = (path, titulo, extra = {}) => ({ path, fullPath: path, meta: { titulo }, params: {}, ...extra })

describe('abas MDI', () => {
  beforeEach(limparAbas)

  it('abre uma aba por tela, sem repetir, e ignora Início e telas públicas', () => {
    abrirAba(r('/inicio', 'Início'))
    abrirAba(r('/agendamentos', 'Agendamentos'))
    abrirAba({ ...r('/agendamentos', 'Agendamentos'), fullPath: '/agendamentos?status=AGENDADO' })
    abrirAba(r('/agendar/tk', 'Agendar', { meta: { publica: true } }))
    abrirAba(r('/agendamentos/7', 'Agendamento', { params: { id: '7' } }))
    expect(abas.lista.map((a) => a.rotulo)).toEqual(['Agendamentos', 'Agendamento 7'])
    expect(abas.lista[0].caminho).toBe('/agendamentos?status=AGENDADO')
  })

  it('fecha indo para a vizinha, reordena e reinicia o cache da tela fechada', () => {
    ;['/a', '/b', '/c'].forEach((p) => abrirAba(r(p, p)))
    moverAba(2, 0)
    expect(abas.lista.map((a) => a.chave)).toEqual(['/c', '/a', '/b'])
    const antes = chaveCache({ path: '/a' })
    expect(fecharAba('/a')).toBe('/b')
    expect(chaveCache({ path: '/a' })).not.toBe(antes)
    expect(fecharAba('/b')).toBe('/c')
    expect(fecharAba('/c')).toBe('/inicio')
  })
})
