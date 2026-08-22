import babelParser from '@babel/eslint-parser';
import eslint from '@eslint/js';
import globals from 'globals';

// TypeScript 7 is newer than the compiler range supported by typescript-eslint.
// Babel supplies ESLint's syntax tree; `npm run typecheck` remains responsible
// for type-aware validation.
const sourceFiles = ['**/*.{js,ts,tsx}'];
const qualityRules = {
    'no-duplicate-imports': 'error',
    'no-var': 'error',
    'object-shorthand': 'error',
    'prefer-const': 'error',
    'prefer-template': 'error',
};

export default [
    {
        ignores: [
            'dist/**',
            'node_modules/**',
            'results/**',
        ],
        linterOptions: {
            reportUnusedDisableDirectives: 'error',
        },
    },
    {
        ...eslint.configs.recommended,
        files: sourceFiles,
        languageOptions: {
            ecmaVersion: 'latest',
            sourceType: 'module',
        },
        rules: qualityRules,
    },
    {
        files: ['**/*.{ts,tsx}'],
        languageOptions: {
            parser: babelParser,
            parserOptions: {
                babelOptions: {
                    presets: ['@babel/preset-typescript'],
                },
                requireConfigFile: false,
            },
        },
    },
    {
        files: ['eslint.config.js', 'report/{aggregate,aggregate.test,catalog-validation.test,chart,chart.test,generate,history-lint,history-lint.test,lint-history,load,load-generate,load.test,model,model.test,publication,publication.test,render,render.test,types}.ts'],
        languageOptions: {
            globals: globals.node,
        },
    },
    {
        files: ['report/report.ts'],
        languageOptions: {
            globals: globals.browser,
        },
    },
    {
        files: ['k6/**/*.{js,ts}'],
        languageOptions: {
            globals: {
                __ENV: 'readonly',
                console: 'readonly',
                open: 'readonly',
            },
        },
    },
];
