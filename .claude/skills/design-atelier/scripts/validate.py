#!/usr/bin/env python3
"""Offline package checks. No host configuration changes or external requests."""
import ast
import json
from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlparse

ROOT = Path(__file__).resolve().parents[1]


def main():
    errors = []
    skill = (ROOT / 'SKILL.md').read_text(encoding='utf-8')
    parts = skill.split('---', 2)
    if len(parts) != 3 or parts[0].strip():
        errors.append('SKILL.md requires frontmatter')
    else:
        # This package deliberately uses simple one-line YAML fields. This is
        # not a general YAML validator; release verification also uses PyYAML.
        if not re.search(r'^name: design-atelier$', parts[1], re.M):
            errors.append('unexpected skill name')
        if not re.search(r'^description: ".+"$', parts[1], re.M):
            errors.append('description must be quoted one-line text')
    for path in ROOT.rglob('*.md'):
        content = path.read_text(encoding='utf-8')
        for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', content):
            target = target.strip('<>')
            if urlparse(target).scheme or target.startswith('#'):
                continue
            relative = unquote(target.split('#', 1)[0])
            resolved = (path.parent / relative).resolve()
            if not resolved.is_relative_to(ROOT) or not resolved.exists():
                errors.append(f'{path.relative_to(ROOT)}: broken/outside link: {target}')
    for path in ROOT.rglob('*.json'):
        try:
            json.loads(path.read_text(encoding='utf-8'))
        except (ValueError, OSError) as error:
            errors.append(f'{path.relative_to(ROOT)}: {error}')
    for path in ROOT.rglob('*.py'):
        try:
            ast.parse(path.read_text(encoding='utf-8'), filename=str(path))
        except SyntaxError as error:
            errors.append(str(error))
    catalog = json.loads((ROOT / 'references/catalog.json').read_text(encoding='utf-8'))
    sys.path.insert(0, str(ROOT / 'scripts'))
    from route import route
    try:
        for row in catalog:
            route(row['terms'][0], catalog=catalog)
            if not (ROOT / row['path']).is_file():
                errors.append('missing module: ' + row['id'])
    except (KeyError, ValueError) as error:
        errors.append('catalog: ' + str(error))
    print(json.dumps({'valid': not errors, 'errors': errors,
                      'modules': len(catalog), 'entry_words': len(skill.split())}, indent=2))
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
