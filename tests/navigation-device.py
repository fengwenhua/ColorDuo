"""Exercise real settings-bar touch input on an attached, Chinese-language device.

Build/install the debug APK first. The test changes only the two navigation UI
preferences and restores their initial values through the UI in a finally block.
Use --root-input on devices where shell input injection requires root.
"""

import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'io.github.fengwenhua.colorduo'
STYLES = ['HyperOS 底栏', 'HyperOS 悬浮底栏', '液态玻璃底栏']
LABELS = ['图标和文字', '仅图标', '仅文字']


class Device:
    def __init__(self, args):
        self.args = args
        self.prefix = [str(args.adb)] + (['-s', args.serial] if args.serial else [])
        self.count = 0
        args.output.mkdir(parents=True, exist_ok=True)

    def adb(self, *args):
        result = subprocess.run(self.prefix + list(args), capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout.decode('utf-8', errors='replace')

    def input(self, *args):
        command = 'input touchscreen ' + ' '.join(str(int(a)) if isinstance(a, (int, float)) else a for a in args)
        if self.args.root_input:
            command = "su -c '" + command + "'"
        result = self.adb('shell', command)
        if 'Exception' in result or 'Error' in result:
            raise RuntimeError(result)

    def ui(self):
        for _ in range(4):
            result = self.adb('shell', 'uiautomator dump /sdcard/colorduo-navigation-test.xml')
            if 'dumped to' in result:
                xml = self.adb('shell', 'cat /sdcard/colorduo-navigation-test.xml')
                root = ET.fromstring(xml)
                if not any(n.get('package') == PACKAGE for n in root.iter('node')):
                    raise AssertionError('The foreground UI is outside ColorDuo')
                self.count += 1
                (self.args.output / f'{self.count:03d}.xml').write_text(xml, encoding='utf-8')
                return root
            time.sleep(0.5)
        raise AssertionError('No UI hierarchy returned')

    @staticmethod
    def bounds(node):
        return tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))

    @staticmethod
    def find(root, label):
        matches = [n for n in root.iter('node')
                   if n.get('text') == label or n.get('content-desc') == label]
        return matches[-1] if matches else None

    def center(self, node):
        x1, y1, x2, y2 = self.bounds(node)
        return (x1 + x2) // 2, (y1 + y2) // 2

    def tabs(self, root):
        result = []
        for label in ['首页', '关于']:
            node = self.find(root, label)
            if node is None:
                raise AssertionError(f'Missing tab: {label}')
            result.append(self.center(node))
        return result

    def assert_page(self, root, index):
        marker = '快捷编辑' if index == 0 else '系统版本'
        if self.find(root, marker) is None:
            raise AssertionError(f'Expected page {index}, missing {marker}')
        title = 'ColorDuo' if index == 0 else '关于'
        titles = [n for n in root.iter('node') if n.get('text') == title]
        if not any(self.bounds(n)[1] < 600 for n in titles):
            raise AssertionError(f'Missing page title: {title}')

    def tap_tab(self, index):
        self.input('tap', *self.tabs(self.ui())[index])
        root = self.ui()
        self.assert_page(root, index)
        return root

    def prefs(self):
        root = ET.fromstring(self.adb('shell', f'run-as {PACKAGE} cat shared_prefs/colorduo_settings.xml'))
        values = {n.get('name'): n.get('value') for n in root}
        return int(values.get('nav_style', 1)), int(values.get('nav_labels', 0))

    def choose(self, title, option):
        for _ in range(6):
            root = self.ui()
            node = self.find(root, title)
            if node is not None:
                self.input('tap', *self.center(node))
                popup = self.ui()
                target = self.find(popup, option)
                if target is None:
                    raise AssertionError(f'Missing dropdown option: {option}')
                self.input('tap', *self.center(target))
                return
            # Scroll only the page body, above the navigation capsule.
            x1, y1, x2, y2 = self.bounds(root.find('node'))
            self.input('swipe', (x1 + x2) // 2, int(y2 * .76), (x1 + x2) // 2, int(y2 * .32), 350)
        raise AssertionError(f'Missing preference: {title}')

    def configure(self, style, labels):
        current_style, current_labels = self.prefs()
        if (style, labels) == (current_style, current_labels):
            return
        self.tap_tab(0)
        self.tap_tab(1)
        if style != current_style:
            self.choose('底栏样式', STYLES[style])
        if labels != current_labels:
            self.choose('底栏标签', LABELS[labels])
        if self.prefs() != (style, labels):
            raise AssertionError('UI preference selection did not persist')

    def check(self, style, labels):
        self.configure(style, labels)
        self.tap_tab(0)
        root = self.tap_tab(1)
        # Each switch checks real destination content, not just tab highlighting.
        for target in [0, 1, 0, 1]:
            centers = self.tabs(root)
            start, end = centers[1 - target], centers[target]
            self.input('swipe', *start, *end, 650)
            root = self.ui()
            self.assert_page(root, target)
        root = self.tap_tab(0)
        left, right = self.tabs(root)
        self.input('swipe', *left, left[0] + 4, left[1], 250)
        self.assert_page(self.ui(), 0)
        self.input('swipe', *left, left[0], left[1] - 100, 350)
        self.assert_page(self.ui(), 0)
        # A drag that crosses slop then returns must settle on the original page.
        self.input('motionevent', 'DOWN', *left)
        try:
            self.input('motionevent', 'MOVE', *right)
            self.input('motionevent', 'MOVE', *left)
        finally:
            self.input('motionevent', 'UP', *left)
        self.assert_page(self.ui(), 0)
        self.tap_tab(1)
        self.tap_tab(0)
        print(f'PASS style={style} labels={labels}: bidirectional drag, jitter, cancellation, reversal, taps', flush=True)

    def check_transitions(self):
        self.tap_tab(0)
        self.tap_tab(1)
        self.tap_tab(0)
        # Deliver taps within the 280ms animation, without an adb round trip or
        # uiautomator's idle wait between them. Exercise both final destinations.
        centers = self.tabs(self.ui())
        for targets in ([1, 0, 1], [0, 1, 0]):
            commands = []
            for target in targets:
                x, y = centers[target]
                commands.extend([f'input touchscreen tap {x} {y}', 'sleep 0.06'])
            command = '\n'.join(commands)
            self.adb('shell', "su -c '" + command + "'" if self.args.root_input else command)
            root = self.ui()
            self.assert_page(root, targets[-1])
            stale_marker = '系统版本' if targets[-1] == 0 else '快捷编辑'
            if self.find(root, stale_marker) is not None:
                raise AssertionError('Outgoing page remained after the transition')
        self.tap_tab(1)
        command = 'input keyevent KEYCODE_BACK'
        self.adb('shell', "su -c '" + command + "'" if self.args.root_input else command)
        self.assert_page(self.ui(), 0)
        print('PASS transitions: both directions, rapid reversals, outgoing-page removal, system back', flush=True)


def main():
    project = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', type=Path, default=project / '.tools/sdk/platform-tools/adb.exe')
    parser.add_argument('--serial')
    parser.add_argument('--root-input', action='store_true')
    parser.add_argument('--transitions-only', action='store_true',
                        help='Check page transitions using the current navigation preferences')
    parser.add_argument('--output', type=Path, default=project / '.tmp/navigation-device')
    args = parser.parse_args()
    device = Device(args)
    original = device.prefs()
    device.adb('shell', f'am start -n {PACKAGE}/.ui.MainActivity')
    time.sleep(1)
    if args.transitions_only:
        device.check_transitions()
        return
    results = []
    try:
        for style, labels in [(0, 0), (1, 0), (1, 1), (1, 2), (2, 0), (2, 1), (2, 2)]:
            device.check(style, labels)
            results.append({'style': style, 'labels': labels, 'passed': True})
    finally:
        device.configure(*original)
        (args.output / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    print(f'PASS {len(results)} configurations; restored navigation preferences {original}', flush=True)


if __name__ == '__main__':
    main()
