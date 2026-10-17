'use client'

import { createContext, use } from 'react'
import type { Dictionary } from '@/i18n/get-dictionary'

const DictionaryContext = createContext<Dictionary | null>(null)

export function DictionaryProvider({
  dictionary,
  children,
}: {
  dictionary: Dictionary
  children: React.ReactNode
}) {
  return <DictionaryContext.Provider value={dictionary}>{children}</DictionaryContext.Provider>
}

export function useDictionary(): Dictionary {
  const context = use(DictionaryContext)
  if (!context) {
    throw new Error('useDictionary must be used within a DictionaryProvider')
  }
  return context
}

export function useOptionalDictionary(): Dictionary | null {
  return use(DictionaryContext)
}
