#!/usr/bin/env python3
"""Check shared syntax and source agreement without a third-party tool dependency."""
from pathlib import Path
import json, re
root=Path(__file__).resolve().parents[1]
for path in (root/'shared').rglob('*.json'):
    json.loads(path.read_text())
tokens=json.loads((root/'shared/design-tokens/cinema.json').read_text())
android=(root/'apps/android/app/src/main/java/com/iptv/app/DesignSystem.kt').read_text().lower()
ios='\n'.join(p.read_text() for d in ['App','Features'] for p in (root/'apps/ios'/d).glob('*.swift')).lower()
for role in ['background','card','accent','cyan','accentText']:
    value=tokens['color']['dark'][role].removeprefix('#').lower()
    assert value in android, f'Android token missing: {role}'
    # iOS semantic UIColor components represent backgrounds; accent hex values are direct.
    if role in ['accent','cyan']: assert value in ios, f'iOS token missing: {role}'
schema=json.loads((root/'shared/api-contracts/appearance.schema.json').read_text())
example=json.loads((root/'shared/api-contracts/examples/appearance.json').read_text())
assert set(example) == set(schema['required']) == set(schema['properties'])
assert not {'username', 'password', 'baseURL'} & set(example)
assert schema['properties']['hiddenRows']['maxItems'] == 6
assert schema['properties']['fontScale']['minimum'] == 0.85
assert schema['properties']['fontScale']['maximum'] == 1.4
print('Shared JSON, appearance example and design token agreement passed.')
