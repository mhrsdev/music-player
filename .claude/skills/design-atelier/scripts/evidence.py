#!/usr/bin/env python3
"""Validate evidence declarations and local artifact integrity, not claim truth."""
import argparse
import hashlib
import json
from pathlib import Path
import re


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def validate(document, root):
    errors = []
    root = Path(root).resolve(strict=True)
    if not root.is_dir():
        return ['root must be a directory']
    if not isinstance(document, dict):
        return ['document must be an object']
    if set(document) != {'schema_version', 'scope', 'checks'}:
        errors.append('top-level keys must be schema_version, scope and checks')
    if type(document.get('schema_version')) is not int or document['schema_version'] != 1:
        errors.append('unsupported schema_version')
    if not isinstance(document.get('scope'), str) or not document['scope'].strip():
        errors.append('scope must be non-empty text')
    checks = document.get('checks')
    if not isinstance(checks, list) or not checks:
        return errors + ['checks must be a non-empty list']
    seen = set()
    for index, check in enumerate(checks):
        label = f'check[{index}]'
        if not isinstance(check, dict):
            errors.append(label + ': must be an object')
            continue
        allowed = {'id', 'status', 'method', 'reason', 'artifacts', 'command', 'exit_code'}
        if set(check) - allowed:
            errors.append(label + ': unknown fields')
        identifier = check.get('id')
        if not isinstance(identifier, str) or not re.fullmatch(r'[a-z0-9][a-z0-9-]*', identifier):
            errors.append(label + ': invalid id')
        elif identifier in seen:
            errors.append(label + ': duplicate id')
        else:
            seen.add(identifier)
        status = check.get('status')
        if status not in ('pass', 'fail', 'not_run', 'not_applicable'):
            errors.append(label + ': invalid status')
        if not isinstance(check.get('method'), str) or not check['method'].strip():
            errors.append(label + ': missing method')
        if status in ('not_run', 'not_applicable'):
            if not isinstance(check.get('reason'), str) or not check['reason'].strip():
                errors.append(label + ': missing reason')
            if 'exit_code' in check:
                errors.append(label + ': unrun check cannot declare exit_code')
        if 'command' in check:
            if not isinstance(check['command'], list) or not check['command'] or not all(
                    isinstance(arg, str) and arg for arg in check['command']):
                errors.append(label + ': command must be a non-empty argv list')
            if status in ('pass', 'fail') and type(check.get('exit_code')) is not int:
                errors.append(label + ': executed command needs integer exit_code')
            if status == 'pass' and check.get('exit_code') != 0:
                errors.append(label + ': passing command must have exit_code zero')
        elif 'exit_code' in check:
            errors.append(label + ': exit_code needs command')
        artifacts = check.get('artifacts')
        if not isinstance(artifacts, list):
            errors.append(label + ': artifacts must be a list')
            continue
        if status in ('pass', 'fail') and not artifacts:
            errors.append(label + ': inspected result needs an artifact')
        for artifact in artifacts:
            if not isinstance(artifact, dict) or set(artifact) != {'path', 'sha256'}:
                errors.append(label + ': artifact needs exactly path and sha256')
                continue
            relative, expected = artifact['path'], artifact['sha256']
            if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
                errors.append(label + ': artifact path must be relative')
                continue
            if not isinstance(expected, str) or not re.fullmatch(r'[0-9a-f]{64}', expected):
                errors.append(label + ': invalid sha256')
                continue
            try:
                actual = (root / relative).resolve(strict=True)
                actual.relative_to(root)
                if not actual.is_file():
                    raise ValueError('not a file')
                if sha256(actual) != expected:
                    errors.append(label + ': artifact hash mismatch: ' + relative)
            except (OSError, ValueError, RuntimeError):
                errors.append(label + ': missing or out-of-root artifact: ' + relative)
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--root', type=Path, required=True)
    args = parser.parse_args()
    try:
        document = json.loads(args.manifest.read_text(encoding='utf-8'))
        errors = validate(document, args.root)
    except (OSError, ValueError, RuntimeError) as error:
        errors = [str(error)]
    print(json.dumps({'valid': not errors, 'errors': errors,
                      'limit': 'Metadata and local hashes only; does not prove execution, usability or beauty.'}, indent=2))
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
