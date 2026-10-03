// Fails when the three locale files drift apart or the code uses a translation
// key that does not exist - the "no missing-translation fallbacks" rule (#27),
// checked before anyone has to open a screen in Arabic. No dependencies.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const LANGUAGES = ['en', 'de', 'ar']
const SRC = fileURLToPath(new URL('../src/', import.meta.url))

function flatten(object, prefix = '') {
  const out = {}
  for (const [key, value] of Object.entries(object)) {
    if (value !== null && typeof value === 'object') Object.assign(out, flatten(value, `${prefix}${key}.`))
    else out[`${prefix}${key}`] = value
  }
  return out
}

function sourceFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return name === 'locales' ? [] : sourceFiles(path)
    return /\.tsx?$/.test(name) ? [path] : []
  })
}

const placeholders = (text) => [...text.matchAll(/\{\{(\w+)\}\}/g)].map((match) => match[1]).sort().join(',')

const locales = Object.fromEntries(
  LANGUAGES.map((language) => [
    language,
    flatten(JSON.parse(readFileSync(new URL(`../src/i18n/locales/${language}.json`, import.meta.url), 'utf8'))),
  ]),
)
const reference = locales.en
const errors = []
const warnings = []

for (const language of LANGUAGES.filter((l) => l !== 'en')) {
  const keys = locales[language]
  for (const key of Object.keys(reference)) {
    if (!(key in keys)) errors.push(`[${language}] missing key: ${key}`)
    else if (placeholders(keys[key]) !== placeholders(reference[key])) {
      errors.push(`[${language}] placeholders differ from en: ${key}`)
    }
  }
  for (const key of Object.keys(keys)) if (!(key in reference)) errors.push(`[${language}] key not in en: ${key}`)
}
for (const language of LANGUAGES) {
  for (const [key, value] of Object.entries(locales[language])) {
    if (typeof value !== 'string' || value.trim() === '') errors.push(`[${language}] empty value: ${key}`)
  }
}

// Keys used by the code: t('a.b'), a labelKey/...Key: 'a.b' constant, or a template t(`a.${x}.b`).
const usedLiterals = new Set()
const templates = new Set()
const quoted = new Set()
for (const file of sourceFiles(SRC)) {
  const text = readFileSync(file, 'utf8')
  for (const match of text.matchAll(/(?<![\w.])(?:t|i18n\.t)\(\s*(['"`])([^'"`]+)\1/g)) {
    if (match[2].includes('${')) templates.add(match[2])
    else usedLiterals.add(match[2])
  }
  for (const match of text.matchAll(/\b\w*[Kk]ey\s*:\s*'([a-z]+(?:\.\w+)+)'/g)) usedLiterals.add(match[1])
  for (const match of text.matchAll(/'([A-Za-z]+(?:\.[A-Za-z_]+)+)'/g)) quoted.add(match[1])
}

for (const key of usedLiterals) if (!(key in reference)) errors.push(`code uses a key that does not exist: ${key}`)
for (const template of templates) {
  const prefix = template.split('${')[0]
  if (!Object.keys(reference).some((key) => key.startsWith(prefix))) {
    errors.push(`code uses a key template with no matching keys: ${template}`)
  }
}
const templatePrefixes = [...templates].map((template) => template.split('${')[0])
for (const key of Object.keys(reference)) {
  const used = usedLiterals.has(key) || quoted.has(key) || templatePrefixes.some((prefix) => key.startsWith(prefix))
  if (!used) warnings.push(`possibly unused key: ${key}`)
}

warnings.forEach((line) => console.warn(`warn  ${line}`))
errors.forEach((line) => console.error(`error ${line}`))
console.log(
  `i18n check: ${Object.keys(reference).length} keys x ${LANGUAGES.length} languages, ` +
    `${usedLiterals.size} keys used in code, ${errors.length} errors, ${warnings.length} warnings`,
)
process.exit(errors.length > 0 ? 1 : 0)
