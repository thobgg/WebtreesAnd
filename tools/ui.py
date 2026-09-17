#!/usr/bin/env python3
"""Kleine adb-Fernbedienung fuer UI-Tests: Elemente ueber ihren Text finden statt ueber feste Koordinaten.

  ui.py tap "Anmelden"        tippt auf das erste Element, dessen Text/Beschreibung passt
  ui.py field 0 "text"        tippt ins n-te Eingabefeld und gibt Text ein
  ui.py wait "Person suchen"  wartet, bis der Text erscheint
  ui.py texts                 listet sichtbare Texte
  ui.py shot name             Bildschirmfoto nach /tmp/claude-1000/shots/name-s.png
"""
import os, re, subprocess, sys, time
import xml.etree.ElementTree as ET

# adb aus dem Android-SDK (ANDROID_HOME/ANDROID_SDK_ROOT) oder aus dem PATH
ADB = next((os.path.join(root, 'platform-tools', 'adb') for root in (os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT'), os.path.expanduser('~/Android/Sdk')) if root and os.path.exists(os.path.join(root, 'platform-tools', 'adb'))), 'adb')
SHOTS = '/tmp/claude-1000/shots'

def adb(*args, binary=False):
    out = subprocess.run([ADB, *args], capture_output=True).stdout
    return out if binary else out.decode('utf-8', 'replace')

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
    xml = adb('shell', 'cat', '/sdcard/ui.xml')
    try:
        return list(ET.fromstring(xml).iter('node'))
    except ET.ParseError:
        return []

def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    return (x1 + x2) // 2, (y1 + y2) // 2

def label(node):
    return node.get('text') or node.get('content-desc') or ''

def find(text, exact=False):
    for node in nodes():
        l = label(node)
        if (l == text) if exact else (text.lower() in l.lower()):
            return node
    return None

def wait(text, timeout=15):
    end = time.time() + timeout
    while time.time() < end:
        if find(text) is not None:
            return True
        time.sleep(0.5)
    return False

def main():
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == 'tap':
        node = find(args[0], exact=len(args) > 1 and args[1] == 'exact')
        if node is None:
            sys.exit(f'nicht gefunden: {args[0]}')
        x, y = center(node); adb('shell', 'input', 'tap', str(x), str(y)); print(f'tap {args[0]} @ {x},{y}')
    elif cmd == 'field':
        fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText']
        x, y = center(fields[int(args[0])]); adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(0.4)
        adb('shell', 'input', 'text', args[1].replace(' ', '%s'))
    elif cmd == 'wait':
        print('ok' if wait(args[0]) else f'TIMEOUT: {args[0]}')
    elif cmd == 'texts':
        print(' | '.join(l for l in map(label, nodes()) if l))
    elif cmd == 'back':
        adb('shell', 'input', 'keyevent', '4')
    elif cmd == 'shot':
        import os; os.makedirs(SHOTS, exist_ok=True)
        open(f'{SHOTS}/{args[0]}.png', 'wb').write(adb('exec-out', 'screencap', '-p', binary=True))
        from PIL import Image
        im = Image.open(f'{SHOTS}/{args[0]}.png'); im.thumbnail((1400, 1400)); im.save(f'{SHOTS}/{args[0]}-s.png')

if __name__ == '__main__':
    main()
