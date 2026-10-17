const PLURAL_BLOCK = /\{(\w+),\s*plural,((?:\s*\w+\s*\{[^{}]*\})+)\s*\}/g
const PLURAL_VARIANT = /(\w+)\s*\{([^{}]*)\}/g

export type FormatValues = Record<string, string | number>

export function formatMessage(template: string, values: FormatValues, locale: string): string {
  const rules = new Intl.PluralRules(locale)
  const withPlurals = template.replace(PLURAL_BLOCK, (_, name: string, variants: string) => {
    const amount = Number(values[name])
    const forms = new Map<string, string>()
    for (const match of variants.matchAll(PLURAL_VARIANT)) forms.set(match[1], match[2])
    const form = forms.get(rules.select(amount)) ?? forms.get('other') ?? ''
    return form.replace(/#/g, String(amount))
  })
  return withPlurals.replace(/\{(\w+)\}/g, (placeholder, name: string) =>
    name in values ? String(values[name]) : placeholder
  )
}
