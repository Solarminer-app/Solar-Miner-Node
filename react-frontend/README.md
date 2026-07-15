# Solarminer React frontend

The UI was originally created with the Next.js App Router. Its pages and components are still
located below `app`, but the production frontend embedded into Spring Boot is built as a static
single-page application with Vite.

This split is intentional: a regular Next.js production build requires a separate Node server,
whereas the Vite build can be packaged into the Spring Boot JAR and served together with the API
on port 8080. Compatibility adapters in `spa` allow the existing Next-oriented components to be
reused during the migration.

## Commands

```bash
npm run dev        # Vite development server on port 3000, proxies /api to port 8080
npm run build      # Production SPA for the Spring Boot JAR
npm run start      # Preview of the Vite production build on port 3000
npm run dev:next   # Legacy Next.js development mode
npm run build:next # Legacy Next.js build, not embedded into Spring Boot
```

Running the Gradle `bootJar` or `processResources` task installs the locked npm dependencies,
executes `npm run build`, and copies the generated files into Spring Boot's `static` resources.
Gradle downloads its own pinned Node.js distribution, so a global Node.js or npm installation is
not required.
