import { readFile } from 'node:fs/promises';
import { Ajv2020 } from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

// CLI boundary: reject invalid normalized artifacts before aggregation/publication.
// Arguments are result JSON and schema JSON paths supplied by the shared runner.
const ajv = new Ajv2020({ allErrors: true });
addFormats(ajv);
const result: unknown = JSON.parse(await readFile(process.argv[2], 'utf8'));
const schema: object = JSON.parse(await readFile(process.argv[3], 'utf8'));
if (!ajv.validate(schema, result)) throw new Error(ajv.errorsText());
