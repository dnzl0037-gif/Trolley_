---
name: Imported app compatibility
description: Non-obvious workspace compatibility decisions for the imported Trolley project.
---

Keep the imported app on its original React major and isolate its React type
resolution from other workspace applications.

**Why:** Shared dependency resolution can select React 19 types for icon
declarations even when Trolley itself uses React 18, causing incompatible JSX
types without any source-code defect.

**How to apply:** Check local and transitive React type resolution before
rewriting components or upgrading React to resolve type errors.

Use consistent modern targets for Vite source transforms, dependency
optimization, and production builds.

**Why:** The workspace's secured esbuild version cannot perform some lowering
requested by Vite 6's older default browser targets. Fixing only the build target
leaves dependency optimization broken during development.

**How to apply:** Keep all three targets aligned when changing Vite settings;
do not downgrade the secured workspace bundler.
