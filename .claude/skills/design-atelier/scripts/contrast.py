#!/usr/bin/env python3
"""WCAG 2.x contrast for hex colors and CSS theme tokens. Offline, standard library only."""
import argparse
import json
import math
from pathlib import Path
import re
import sys

HEX = re.compile(r'^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$')


def parse_color(value):
    value = value.strip()
    if not HEX.match(value):
        raise ValueError(f'unsupported color {value!r}: use #rgb or #rrggbb and composite any transparency first')
    digits = value[1:]
    if len(digits) == 3:
        digits = ''.join(char * 2 for char in digits)
    return tuple(int(digits[i:i + 2], 16) for i in (0, 2, 4))


def luminance(rgb):
    def channel(value):
        value /= 255
        return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4
    r, g, b = (channel(v) for v in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(foreground, background):
    first, second = luminance(parse_color(foreground)), luminance(parse_color(background))
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)


def shown(value):
    # WCAG forbids rounding up across a threshold, so truncate for display.
    return math.floor(value * 100) / 100


def verdict(value):
    return {'normal_text_aa': value >= 4.5, 'large_text_or_ui_aa': value >= 3.0, 'normal_text_aaa': value >= 7.0}


def tokens_from_css(text):
    """Return (light, dark) token tables from the theme-token pattern. Hex values and var() chains only."""
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    light, overrides = {}, {}
    for selector, body in re.findall(r'([^{}]+)\{([^{}]*)\}', text):
        selector = selector.strip()
        if ':not([data-theme="light"])' in selector or '[data-theme="dark"]' in selector:
            target = overrides
        elif selector == ':root' or selector == ':root[data-theme="light"]':
            target = light
        else:
            continue
        for name, value in re.findall(r'--([\w-]+)\s*:\s*([^;}]+)', body):
            target[name] = value.strip()
    return light, {**light, **overrides}


def resolve(name, table, depth=0):
    if name not in table:
        raise ValueError(f'token --{name} is not defined')
    if depth > 10:
        raise ValueError(f'token --{name} has a var() cycle')
    value = table[name]
    chained = re.fullmatch(r'var\(--([\w-]+)\)', value)
    return resolve(chained.group(1), table, depth + 1) if chained else value


def check_pairs(css_text, pairs):
    light, dark = tokens_from_css(css_text)
    rows = []
    for theme, table in (('light', light), ('dark', dark)):
        for pair in pairs:
            parts = pair.split(':')
            if len(parts) not in (2, 3):
                raise ValueError(f'pair {pair!r} must be foreground:background or foreground:background:minimum')
            minimum = float(parts[2]) if len(parts) == 3 else 4.5
            fg, bg = resolve(parts[0], table), resolve(parts[1], table)
            value = ratio(fg, bg)
            rows.append({'theme': theme, 'pair': f'{parts[0]} on {parts[1]}', 'foreground': fg, 'background': bg,
                         'ratio': shown(value), 'minimum': minimum, 'pass': value >= minimum})
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('colors', nargs='*', help='FOREGROUND BACKGROUND as #rgb or #rrggbb')
    parser.add_argument('--css', type=Path, help='CSS file using :root plus dark overrides')
    parser.add_argument('--pair', action='append', default=[],
                        help='token pair foreground:background[:minimum], repeatable; default minimum 4.5')
    args = parser.parse_args()
    try:
        if args.css:
            if not args.pair:
                parser.error('--css needs at least one --pair')
            rows = check_pairs(args.css.read_text(encoding='utf-8'), args.pair)
            ok = all(row['pass'] for row in rows)
            print(json.dumps({'ok': ok, 'results': rows,
                              'limit': 'Solid hex colors per theme only; not images, gradients, overlays or full accessibility.'},
                             indent=2))
            return 0 if ok else 1
        if len(args.colors) != 2:
            parser.error('give FOREGROUND and BACKGROUND, or use --css with --pair')
        value = ratio(*args.colors)
        print(json.dumps({'foreground': args.colors[0], 'background': args.colors[1],
                          'ratio': shown(value), **verdict(value)}, indent=2))
        return 0
    except (ValueError, OSError) as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
