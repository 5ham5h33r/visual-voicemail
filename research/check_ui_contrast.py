"""Check text contrast in light, dark, and AMOLED inbox palettes."""
import pathlib
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent


def luminance(hex_color):
    channels = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in channels]
    return sum(v * weight for v, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def main():
    failures = []
    pairs = [(text, surface) for text in ('primary_text', 'secondary_text', 'error')
             for surface in ('background', 'surface')]
    pairs += [('accent', 'background'), ('accent', 'accent_surface')]
    palettes = {}
    for variant in ('values', 'values-night'):
        palettes[variant] = {node.attrib['name']: node.text.strip() for node in
                             ET.parse(ROOT / 'app/res' / variant / 'colors.xml').getroot()}
    palettes['amoled'] = dict(palettes['values-night'], background=palettes['values']['amoled_background'])
    if palettes['amoled']['background'].upper() != '#000000':
        raise SystemExit('AMOLED background must be true black (#000000).')
    for variant, colors in palettes.items():
        for foreground, background in pairs:
            bright, dark = sorted((luminance(colors[foreground]), luminance(colors[background])), reverse=True)
            ratio = (bright + 0.05) / (dark + 0.05)
            print(f'{variant}: {foreground} on {background}: {ratio:.2f}:1')
            if ratio < 4.5:
                failures.append(f'{variant}: {foreground}/{background}')
    if failures:
        raise SystemExit('Text contrast below 4.5:1: ' + ', '.join(failures))


if __name__ == '__main__':
    main()
