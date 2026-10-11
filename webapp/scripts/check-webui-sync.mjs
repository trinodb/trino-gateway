#!/usr/bin/env node
// Report how the dependencies shared with the Trino web UI compare to it.
//
// The web UI of Trino Gateway is planned to embed the Trino query editor,
// which shares its React stack with the Trino web UI at runtime. Keeping the
// same versions here makes that step small. This script fetches the web UI
// manifest from the Trino default branch and compares the shared packages
// against the local package.json.
//
// Nothing is embedded yet, so drift is advisory and never fails the check.
// A failed manifest fetch or an unsupported version range does fail it with
// exit code 2, since nothing was compared. Set STRICT_RUNTIME to true once the
// query editor is embedded, so that drift in a runtime library fails the check,
// as it does in trino-query-ui.
//
// Run locally with `pnpm run sync:check`. CI runs it on pull requests that
// change the dependencies and on a weekly schedule.

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const WEBUI_MANIFEST_URL =
  'https://raw.githubusercontent.com/trinodb/trino/master/core/trino-web-ui/src/main/resources/webapp/package.json'

const STRICT_RUNTIME = false

// Libraries shared with the web UI at runtime.
const RUNTIME = ['react', 'react-dom', 'react-router-dom', 'zustand', 'sass']

// Build toolchain shared with the web UI. Vite is left out, since the web UI
// uses the rolldown-vite package under the same name.
const TOOLCHAIN = [
  '@types/react',
  '@types/react-dom',
  '@eslint/js',
  'eslint',
  'eslint-plugin-react-hooks',
  'eslint-plugin-react-refresh',
  '@typescript-eslint/eslint-plugin',
  '@typescript-eslint/parser',
  'globals',
  'typescript',
  '@vitejs/plugin-react',
]

const here = dirname(fileURLToPath(import.meta.url))
const local = JSON.parse(readFileSync(join(here, '..', 'package.json'), 'utf8'))

const localVersion = (name) =>
  local.dependencies?.[name] ?? local.devDependencies?.[name]

// Reduce a version range to a comparable base version. Only exact versions and
// caret (`^`) or tilde (`~`) ranges are supported, since those are what both
// projects use. Any other range syntax cannot be reduced to a single version
// without guessing, so it is rejected rather than silently turned into a false
// match. Also unwraps an npm alias such as `npm:rolldown-vite@^7.3.1`.
const SUPPORTED_RANGE = /^[\^~]?(\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?)$/
const baseVersion = (source, name, spec) => {
  if (!spec) return undefined
  const aliased = spec.startsWith('npm:') ? spec.split('@').pop() : spec
  const match = aliased.match(SUPPORTED_RANGE)
  if (!match) {
    console.error(
      `Unsupported version range for ${name} in the ${source} manifest: "${spec}". ` +
        'Only exact versions and caret (^) or tilde (~) ranges are supported.'
    )
    process.exit(2)
  }
  return match[1]
}

const response = await fetch(WEBUI_MANIFEST_URL)
if (!response.ok) {
  console.error(
    `Failed to fetch the Trino web UI manifest: ${response.status} ${response.statusText}`
  )
  process.exit(2)
}
const webui = await response.json()
const webuiVersion = (name) =>
  webui.dependencies?.[name] ?? webui.devDependencies?.[name]

const compare = (names) => {
  const rows = []
  for (const name of names) {
    const ours = baseVersion('local', name, localVersion(name))
    const theirs = baseVersion('web UI', name, webuiVersion(name))
    if (!theirs) continue // the web UI does not use this package
    rows.push({ name, ours, theirs, drift: ours !== theirs })
  }
  return rows
}

const report = (title, rows) => {
  console.log(`\n${title}`)
  for (const { name, ours, theirs, drift } of rows) {
    const mark = drift ? 'DRIFT' : 'ok'
    console.log(
      `  [${mark}] ${name}: local ${ours ?? 'missing'} vs web UI ${theirs}`
    )
  }
}

const runtime = compare(RUNTIME)
const toolchain = compare(TOOLCHAIN)

report('Runtime libraries', runtime)
report('Build toolchain', toolchain)

const runtimeDrift = runtime.filter((r) => r.drift)
const toolchainDrift = toolchain.filter((r) => r.drift)
const drift = runtimeDrift.length + toolchainDrift.length

if (drift === 0) {
  console.log('\nShared dependencies are in sync with the Trino web UI.')
} else {
  console.log(
    `\nDrift on ${drift} package(s). Align package.json with the Trino web UI where possible.`
  )
}

if (STRICT_RUNTIME && runtimeDrift.length > 0) {
  console.error(
    `Runtime drift on ${runtimeDrift.length} package(s) fails the check.`
  )
  process.exit(1)
}
