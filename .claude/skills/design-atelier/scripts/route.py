#!/usr/bin/env python3
"""Suggest local references. Offline lexical routing, never a semantic classifier."""
import argparse
import json
from pathlib import Path
import re
import unicodedata

ROOT = Path(__file__).resolve().parents[1]


def normalize(value):
    value = unicodedata.normalize('NFKC', value).lower()
    value = value.translate(str.maketrans({'ي': 'ی', 'ك': 'ک', '\u200c': ' '}))
    return ' '.join(re.findall(r'[^\W_]+', value, flags=re.UNICODE))


def route(query, limit=4, catalog=None):
    if not 1 <= limit <= 20:
        raise ValueError('max-modules must be between 1 and 20')
    if catalog is None:
        catalog = json.loads((ROOT / 'references/catalog.json').read_text(encoding='utf-8'))
    records = {row['id']: row for row in catalog}
    if len(records) != len(catalog):
        raise ValueError('duplicate module id')
    for row in catalog:
        if not set(row['requires']) <= records.keys():
            raise ValueError('unknown dependency')
    normalized = ' ' + normalize(query) + ' '
    ranked = []
    for row in catalog:
        matched = sorted({term for term in row['terms']
                          if ' ' + normalize(term) + ' ' in normalized})
        score = sum(4 if ' ' in normalize(term) else 2 for term in matched)
        if score:
            ranked.append((score, row['id'], matched))
    ranked.sort(key=lambda item: (-item[0], item[1]))
    selected = ranked[:limit]
    output, visiting, added = [], set(), set()

    def add(module_id, reason, kind):
        if module_id in visiting:
            raise ValueError('dependency cycle')
        if module_id in added:
            return
        visiting.add(module_id)
        row = records[module_id]
        for dependency in row['requires']:
            add(dependency, 'Prerequisite of ' + module_id, 'prerequisite')
        visiting.remove(module_id)
        output.append({'id': module_id, 'path': row['path'], 'reason': reason, 'kind': kind})
        added.add(module_id)

    for score, module_id, matched in selected:
        add(module_id, 'Matched: ' + ', '.join(matched), 'direct')
    return {'schema_version': 1, 'query': query,
            'method': 'curated lexical aliases; no semantic confidence estimate',
            'abstained': not bool(output),
            'note': ('No matching aliases. Inspect the SKILL.md reference map manually.' if not output
                     else 'Suggestions only. Dependencies may exceed max-modules; read for actual task fit.'),
            'modules': output}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('query')
    parser.add_argument('--max-modules', type=int, default=4)
    args = parser.parse_args()
    try:
        result = route(args.query, args.max_modules)
    except (ValueError, KeyError, OSError, json.JSONDecodeError) as error:
        parser.exit(2, str(error) + '\n')
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
