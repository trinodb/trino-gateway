
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
pnpm run build
```

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

# Package

```
# If you want the web to start with the project, you can execute 
./mvnw clean install

# If you want to run the web project alone, you can execute 
cd ./webapp
pnpm install
pnpm run build
```

# Guidelines

The UI follows the [Material Design guidelines](https://m3.material.io/). The following items are specific call outs:

* Use sentence case in labels, buttons, and other UI elements
* Avoid "please" and other filler words.
* Write for a global user base
* Avoid abbreviations.
* Use second person "You ...".

More information is available with the [content design](https://m3.material.io/foundations/content-design/overview)
and other guides.
