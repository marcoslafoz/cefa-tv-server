import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const { formatMessage } = await import(new URL('../lib/format.ts', import.meta.url).href)

const LOCALES = ['es', 'en', 'pl']
const PLURAL_VARIANTS = {
  es: ['one', 'other'],
  en: ['one', 'other'],
  pl: ['one', 'few', 'many', 'other'],
}

const dictionaries = Object.fromEntries(
  LOCALES.map((locale) => [
    locale,
    JSON.parse(
      readFileSync(new URL(`../i18n/dictionaries/${locale}.json`, import.meta.url), 'utf8')
    ),
  ])
)

function flatten(node, prefix = '', out = {}) {
  if (Array.isArray(node)) {
    node.forEach((value, index) => flatten(value, `${prefix}.${index}`, out))
  } else if (node && typeof node === 'object') {
    for (const [key, value] of Object.entries(node))
      flatten(value, prefix ? `${prefix}.${key}` : key, out)
  } else {
    out[prefix] = node
  }
  return out
}

function placeholders(text) {
  const names = new Set()
  for (const match of text.matchAll(/\{(\w+)(?:\}|,\s*plural,)/g)) names.add(match[1])
  return [...names].sort().join(',')
}

const flat = Object.fromEntries(LOCALES.map((locale) => [locale, flatten(dictionaries[locale])]))

test('los tres diccionarios tienen exactamente las mismas claves', () => {
  const reference = Object.keys(flat.es).sort()
  for (const locale of LOCALES.slice(1)) {
    const keys = Object.keys(flat[locale]).sort()
    assert.deepEqual(
      reference.filter((key) => !keys.includes(key)),
      [],
      `faltan claves en ${locale}`
    )
    assert.deepEqual(
      keys.filter((key) => !reference.includes(key)),
      [],
      `sobran claves en ${locale}`
    )
  }
})

test('ninguna traduccion esta vacia', () => {
  for (const locale of LOCALES) {
    for (const [key, value] of Object.entries(flat[locale])) {
      assert.ok(typeof value === 'string' && value !== '', `${locale}:${key}`)
    }
  }
})

test('los marcadores de cada clave coinciden entre idiomas', () => {
  for (const key of Object.keys(flat.es)) {
    const expected = placeholders(flat.es[key])
    for (const locale of LOCALES.slice(1)) {
      assert.equal(placeholders(flat[locale][key]), expected, `${locale}:${key}`)
    }
  }
})

test('los plurales declaran las categorias de cada idioma', () => {
  for (const locale of LOCALES) {
    for (const [key, value] of Object.entries(flat[locale])) {
      if (!/plural,/.test(value)) continue
      for (const category of PLURAL_VARIANTS[locale]) {
        assert.match(value, new RegExp(`\\b${category}\\s*\\{`), `${locale}:${key} sin ${category}`)
      }
    }
  }
})

test('el plural polaco usa la forma correcta', () => {
  const template = flat.pl['playlists.itemCount']
  const render = (count) => formatMessage(template, { count }, 'pl')
  assert.equal(render(1), '1 element')
  assert.equal(render(2), '2 elementy')
  assert.equal(render(5), '5 elementów')
  assert.equal(render(12), '12 elementów')
  assert.equal(render(22), '22 elementy')
  assert.equal(render(25), '25 elementów')
})

test('el plural castellano e ingles distingue uno de varios', () => {
  assert.equal(formatMessage(flat.es['playlists.itemCount'], { count: 1 }, 'es'), '1 elemento')
  assert.equal(formatMessage(flat.es['playlists.itemCount'], { count: 3 }, 'es'), '3 elementos')
  assert.equal(formatMessage(flat.en['playlists.itemCount'], { count: 1 }, 'en'), '1 item')
})
