import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import parseSpdxExpression from 'spdx-expression-parse';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const webappDirectory = path.resolve(scriptDirectory, '..');
const nodeModulesDirectory = path.join(webappDirectory, 'node_modules', '.pnpm');
const allowedLicensesPath = path.join(webappDirectory, 'allowed-licenses.txt');

function readAllowedLicenses()
    {//allowlist validation once read
    const licenses = fs.readFileSync(allowedLicensesPath, 'utf8')
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter((line) => line && !line.startsWith('#'));

    const invalid = licenses.filter((license) => {
        try {
            parseSpdxExpression(license);
            return false;
        }
        catch {
            return true;
        }
    });
    //throws if allowlist entry invalid
    if (invalid.length) {
        throw new Error(
            `Invalid SPDX identifiers in allowed-licenses.txt: ${invalid.join(', ')}`
        );
    }

    return new Set(licenses);
}

export function packageLicense(packageJson)
{
    if (typeof packageJson.license === 'string' && packageJson.license.trim()) {
        return packageJson.license.trim();
    }

    if (packageJson.license && typeof packageJson.license === 'object' &&
        typeof packageJson.license.type === 'string' && packageJson.license.type.trim()) {
        return packageJson.license.type.trim();
    }

    if (Array.isArray(packageJson.licenses)) {
        const licenses = packageJson.licenses
            .map((license) => typeof license === 'string' ? license : license?.type)
            .filter(Boolean)
            .map((license) => license.trim())
            .filter(Boolean);
        if (licenses.length > 0) {
            return licenses.join(' OR ');
        }
    }

    return undefined;
}

function isParsedLicenseAllowed(expression, allowedLicenses)
{
    if (expression.license) {
        const license = expression.exception
            ? `${expression.license} WITH ${expression.exception}`
            : expression.license;
        return allowedLicenses.has(license);
    }

    if (expression.conjunction === 'or') {
        return isParsedLicenseAllowed(expression.left, allowedLicenses) ||
            isParsedLicenseAllowed(expression.right, allowedLicenses);
    }

    if (expression.conjunction === 'and') {
        return isParsedLicenseAllowed(expression.left, allowedLicenses) &&
            isParsedLicenseAllowed(expression.right, allowedLicenses);
    }

    return false;
}

export function isLicenseAllowed(license, allowedLicenses)
{
    try {
        return isParsedLicenseAllowed(parseSpdxExpression(license), allowedLicenses);
    }
    catch {
        return false;
    }
}
// Read package manifests because pnpm-lock.yaml does not record licenses.
function packageJsonFiles()
{
    if (!fs.existsSync(nodeModulesDirectory)) {
        throw new Error('node_modules/.pnpm was not found. Run pnpm install before checking licenses.');
    }

    return fs.readdirSync(nodeModulesDirectory, {withFileTypes: true})
        .filter((entry) => entry.isDirectory())
        .flatMap((entry) => {
            const nestedNodeModules = path.join(nodeModulesDirectory, entry.name, 'node_modules');
            if (!fs.existsSync(nestedNodeModules)) {
                return [];
            }

            return fs.readdirSync(nestedNodeModules, {withFileTypes: true})
                .filter((packageEntry) => packageEntry.isDirectory() && !packageEntry.name.startsWith('.'))
                .flatMap((packageEntry) => {
                    const packagePath = path.join(nestedNodeModules, packageEntry.name);
                    if (packageEntry.name.startsWith('@')) {
                        return fs.readdirSync(packagePath, {withFileTypes: true})
                            .filter((scopedPackageEntry) => scopedPackageEntry.isDirectory())
                            .map((scopedPackageEntry) => path.join(packagePath, scopedPackageEntry.name, 'package.json'));
                    }
                    return [path.join(packagePath, 'package.json')];
                });
        })
        .filter((packageJsonPath) => fs.existsSync(packageJsonPath));
}

function checkLicenses()
{
    const allowedLicenses = readAllowedLicenses();
    const packages = new Map();

    for (const packageJsonPath of packageJsonFiles()) {
        const packageJson = JSON.parse(fs.readFileSync(packageJsonPath, 'utf8'));
        const packageName = `${packageJson.name}@${packageJson.version}`;
        packages.set(packageName, packageLicense(packageJson));
    }

    const missingLicenses = [];
    const disallowedLicenses = [];

    for (const [packageName, license] of [...packages.entries()].sort()) {
        if (!license) {
            missingLicenses.push(packageName);
        }
        else if (!isLicenseAllowed(license, allowedLicenses)) {
            disallowedLicenses.push(`${packageName}: ${license}`);
        }
    }

    if (missingLicenses.length || disallowedLicenses.length) {
        if (missingLicenses.length) {
            console.error('Packages without license metadata:');
            console.error(missingLicenses.join('\n'));
        }
        if (disallowedLicenses.length) {
            console.error('Packages with licenses not listed in allowed-licenses.txt:');
            console.error(disallowedLicenses.join('\n'));
        }
        process.exit(1);
    }

    console.log(`Verified licenses for ${packages.size} Web UI dependencies.`);
}

const scriptPath = fileURLToPath(import.meta.url);

if (process.argv[1] && fs.realpathSync(process.argv[1]) === scriptPath) {
    checkLicenses();
}