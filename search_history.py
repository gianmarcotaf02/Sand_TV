import os
import re

history_dir = os.path.join(os.environ['USERPROFILE'], 'Desktop', 'Progetti', 'Antigravity', 'SandTV_nuovo', '.idea', 'LocalHistory')
print(f"Scanning {history_dir}...")

if not os.path.exists(history_dir):
    print("Local history directory not found.")
    
# Or look in system local history
system_history = os.path.join(os.environ['LOCALAPPDATA'], 'Google', 'AndroidStudio2024.2', 'LocalHistory')
print(f"Or checking system level: {system_history}")

def scan_dir(d):
    try:
        if not os.path.exists(d): return
        for root, _, files in os.walk(d):
            for f in files:
                path = os.path.join(root, f)
                try:
                    with open(path, 'r', encoding='utf-8', errors='ignore') as file:
                        content = file.read()
                        if 'versionCode' in content and 'applicationId' in content:
                            print(f"\n--- FOUND POTENTIAL BACKUP IN {path} ---")
                            print(content[:500])
                            print("...")
                except Exception as e:
                    pass
    except Exception as e:
        print(f"Error scanning {d}: {e}")

scan_dir(history_dir)
scan_dir(system_history)
# Also check standard android studio locations
for year in range(2022, 2026):
    for minor in range(1,4):
         scan_dir(os.path.join(os.environ['LOCALAPPDATA'], 'Google', f'AndroidStudio{year}.{minor}', 'LocalHistory'))
