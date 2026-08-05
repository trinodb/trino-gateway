import assert from 'node:assert/strict';
import test from 'node:test';

import {isLicenseAllowed, packageLicense} from './check-licenses.js';

const allowedLicenses = new Set(['Apache-2.0', 'CC0-1.0', 'ISC', 'MIT']);

test('reads current and historic package license formats', () => {
    assert.equal(packageLicense({license: 'MIT'}), 'MIT');
    assert.equal(packageLicense({license: {type: 'MIT', url: 'https://example.com'}}), 'MIT');
    assert.equal(packageLicense({licenses: [{type: 'MIT'}, 'ISC']}), 'MIT OR ISC');
    assert.equal(packageLicense({}), undefined);
});

test('allows valid SPDX expressions composed of allowed licenses', () => {
    assert.equal(isLicenseAllowed('MIT OR ISC', allowedLicenses), true);
    assert.equal(isLicenseAllowed('Apache-2.0 AND MIT', allowedLicenses), true);
    assert.equal(isLicenseAllowed('(MIT OR CC0-1.0)', allowedLicenses), true);
});

test('rejects expressions containing required disallowed licenses', () => {
    assert.equal(isLicenseAllowed('MIT AND Zlib', allowedLicenses), false);
    assert.equal(isLicenseAllowed('Zlib OR ISC', allowedLicenses), true);
    assert.equal(isLicenseAllowed('not an SPDX expression', allowedLicenses), false);
});
