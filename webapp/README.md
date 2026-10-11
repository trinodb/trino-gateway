
# Framework document

1. Semi UI: https://semi.design/en-US
   - If you want to customize the theme, you can view https://semi.design/dsm/landing
2. Vite: https://vitejs.dev/
3. React: https://react.dev/
4. TypeScript: https://www.typescriptlang.org/
5. Echarts: https://echarts.apache.org/

# Development

```
pnpm install
pnpm run dev
pnpm run check
pnpm run build
```

The `check` script runs the type check and lint. The Maven build runs it as
well, so a failure there fails the build.

# Test

Functional tests run with [Playwright](https://playwright.dev/) against the
production bundle. Every backend call is mocked in `e2e/fixtures.ts`, so no
running Trino Gateway is needed.

```
pnpm exec playwright install chromium
pnpm run test
```

Each test also fails on any uncaught exception or console error in the browser.
Run the tests before and after a dependency update.

# Dependencies

Dependencies are pinned to exact versions. The versions of the packages shared
with the [Trino web UI](https://github.com/trinodb/trino/tree/master/core/trino-web-ui)
follow that project, since the web UI here is planned to embed the Trino query
editor, which shares its React stack with the Trino web UI. Report the
differences with:

```
pnpm run sync:check
```

Drift is advisory. Major version updates of the shared packages land here
only after the Trino web UI adopted them.

# Package

The folder is also the `webapp` Maven module. The Maven build installs Node.js
and pnpm into `target/node`, builds the production bundle into
`target/classes/static`, and packages it as a JAR that the `gateway-ha` module
depends on.

```
# Build everything, from the root of the repository
./mvnw clean install

# Build and check only the web UI, from the root of the repository
./mvnw install -pl webapp
```

To use the Node.js and pnpm versions that Maven installed with the commands in
this file, add them to the path:

```
export PATH="$PWD/target/node:$PATH"
```

The [development documentation](../docs/development.md#web-ui) has more
details.

# Guidelines

The UI follows the [Material Design guidelines](https://m3.material.io/). The following items are specific call outs:

* Use sentence case in labels, buttons, and other UI elements
* Avoid "please" and other filler words.
* Write for a global user base
* Avoid abbreviations.
* Use second person "You ...".

More information is available with the [content design](https://m3.material.io/foundations/content-design/overview)
and other guides.
