"""Replay only real UI setup and audit-fault checks after the backend matrix passed."""
import os, runpy
from pathlib import Path
os.environ['SUMMER_COMPOSED_SCOPES_UI_ONLY']='1'
runpy.run_path(str(Path(__file__).with_name('run.py')),run_name='__main__')
