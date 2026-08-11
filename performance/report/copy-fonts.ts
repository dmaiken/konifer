import { copyFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const performanceDirectory = fileURLToPath(new URL('../', import.meta.url));
const destination = path.join(performanceDirectory, 'dist/fonts');
const fonts = [
    ['@fontsource/geist-sans/files/geist-sans-latin-400-normal.woff2', 'geist-sans-400.woff2'],
    ['@fontsource/geist-sans/files/geist-sans-latin-600-normal.woff2', 'geist-sans-600.woff2'],
    ['@fontsource/geist-sans/files/geist-sans-latin-700-normal.woff2', 'geist-sans-700.woff2'],
    ['@fontsource/geist-mono/files/geist-mono-latin-400-normal.woff2', 'geist-mono-400.woff2'],
    ['@fontsource/geist-mono/files/geist-mono-latin-500-normal.woff2', 'geist-mono-500.woff2'],
] as const;

await mkdir(destination, { recursive: true });
await Promise.all(fonts.map(async ([modulePath, filename]) => {
    const source = fileURLToPath(import.meta.resolve(modulePath));
    await copyFile(source, path.join(destination, filename));
}));
