import { readFile } from 'node:fs/promises';
import type { Plugin } from 'vite';

// Download-only development helper. It never connects to or executes SQL.
export function databaseSetupDownload(): Plugin {
  let basePath = '/';
  return {
    name: 'trolley-database-setup-download',
    apply: 'serve',
    configResolved(config) {
      basePath = config.base.endsWith('/') ? config.base : `${config.base}/`;
    },
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const path = req.url?.split('?')[0];
        if (path !== `${basePath}__setup/trolley-database.sql`) return next();
        if (req.method !== 'GET' && req.method !== 'HEAD') {
          res.writeHead(405, { Allow: 'GET, HEAD' });
          res.end();
          return;
        }
        try {
          const [initial, hardening, privacy, editor, seed] = await Promise.all([
            readFile(new URL('../supabase/migrations/202611190001_initial_schema.sql', import.meta.url), 'utf8'),
            readFile(new URL('../supabase/migrations/202611190002_security_hardening.sql', import.meta.url), 'utf8'),
            readFile(new URL('../supabase/migrations/202611190003_message_and_verification_privacy.sql', import.meta.url), 'utf8'),
            readFile(new URL('../supabase/migrations/202611190004_provider_draft_editor.sql', import.meta.url), 'utf8'),
            readFile(new URL('../supabase/seed/001_categories.sql', import.meta.url), 'utf8'),
          ]);
          // Use one outer transaction for all migrations and the seed. Fail
          // explicitly if the seed's transaction layout changes.
          if ((seed.match(/^begin;\r?$/gm) ?? []).length !== 1 ||
              (seed.match(/^commit;\r?$/gm) ?? []).length !== 1) {
            throw new Error('Unexpected category seed transaction layout.');
          }
          const seedBody = seed.replace(/^begin;\r?$/m, '').replace(/^commit;\r?$/m, '');
          const sql = [
            '-- Trolley database setup: run once in the confirmed Supabase project.',
            '-- Creates schema, security policies, and 51 families / 1,020 subcategories.',
            '-- Do not rerun against an existing or partially installed Trolley schema.',
            '-- If any statement fails, the whole transaction must be rolled back.',
            'begin;',
            initial,
            hardening,
            privacy,
            editor,
            seedBody,
            'commit;',
          ].join('\n\n');
          res.writeHead(200, {
            'Content-Type': 'application/sql; charset=utf-8',
            'Content-Disposition': 'attachment; filename="trolley-database.sql"',
            'Cache-Control': 'no-store',
            'X-Content-Type-Options': 'nosniff',
          });
          res.end(req.method === 'HEAD' ? undefined : sql);
        } catch (error) {
          server.config.logger.error(`Unable to prepare Trolley database setup: ${String(error)}`);
          res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
          res.end('Unable to prepare the database setup file. Check the development server logs.');
        }
      });
    },
  };
}
