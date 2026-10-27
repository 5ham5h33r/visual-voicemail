"""Run the local GitHub CLI with Git available on PATH."""
import os
import pathlib
import shutil
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent
git = shutil.which('git')
if not git:
    git = os.environ.get('GIT_EXECUTABLE')
if not git:
    raise SystemExit('Install Git or set GIT_EXECUTABLE to its full path.')
gh = shutil.which('gh')
if not gh:
    gh = next((ROOT / 'tools/gh').rglob('gh.exe'), None)
if not gh:
    raise SystemExit('Install GitHub CLI before using this helper.')
env = dict(os.environ)
env['PATH'] = str(pathlib.Path(git).parent) + os.pathsep + env.get('PATH', '')
raise SystemExit(subprocess.run([str(gh)] + sys.argv[1:], env=env).returncode)
