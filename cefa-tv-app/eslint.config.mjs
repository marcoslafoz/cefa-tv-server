import { defineConfig, globalIgnores } from 'eslint/config'
import nextVitals from 'eslint-config-next/core-web-vitals'
import nextTs from 'eslint-config-next/typescript'
import prettier from 'eslint-config-prettier'

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  prettier,
  {
    rules: {
      '@next/next/no-img-element': 'off',
      'react-hooks/incompatible-library': 'off',
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: 'lucide-react',
              message: 'Usa @tabler/icons-react: es la librería de iconos del proyecto.',
            },
            {
              name: 'sonner',
              importNames: ['toast'],
              message: 'Usa el toast del proyecto (@/lib/toast).',
            },
          ],
          patterns: [
            {
              group: ['./*', '../*'],
              message: 'Usa el alias @/ en lugar de rutas relativas (regla nextjs.md).',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['app/**/*.tsx', 'components/**/*.tsx'],
    ignores: ['components/shadcn-ui/**'],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXOpeningElement[name.name='details']",
          message: 'Usa Collapsible (AdvancedOptions) en lugar de <details>.',
        },
        {
          selector: "JSXOpeningElement[name.name='summary']",
          message: 'Usa CollapsibleTrigger en lugar de <summary>.',
        },
        {
          selector: "JSXOpeningElement[name.name='select']",
          message: 'Usa el Select de shadcn en lugar de <select>.',
        },
        {
          selector: "JSXOpeningElement[name.name='textarea']",
          message: 'Usa el Textarea de shadcn en lugar de <textarea>.',
        },
        {
          selector: "JSXOpeningElement[name.name='progress']",
          message: 'Usa el Progress de shadcn en lugar de <progress>.',
        },
        {
          selector: "JSXOpeningElement[name.name='hr']",
          message: 'Usa el Separator de shadcn en lugar de <hr>.',
        },
        {
          selector:
            "JSXOpeningElement[name.name='input'] > JSXAttribute[name.name='type'][value.value=/^(number|checkbox|radio|range|date|time|datetime-local|file)$/]",
          message:
            'Usa NumberField, Checkbox, RadioGroup, Slider, DatePicker, TimePicker o la zona de subida en lugar de <input> nativo.',
        },
        {
          selector: "JSXOpeningElement[name.name=/^[a-z]+$/] > JSXAttribute[name.name='title']",
          message: 'Usa Tooltip en lugar del atributo nativo title.',
        },
        {
          selector:
            "CallExpression[callee.object.name='window'][callee.property.name=/^(confirm|alert|prompt)$/]",
          message: 'Usa AlertDialog o toast en lugar de window.confirm/alert/prompt.',
        },
      ],
    },
  },
  {
    files: ['components/shadcn-ui/sonner.tsx', 'lib/toast.ts'],
    rules: { 'no-restricted-imports': 'off' },
  },
  globalIgnores(['.next/**', 'out/**', 'build/**', 'next-env.d.ts']),
])

export default eslintConfig
