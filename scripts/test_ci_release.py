"""Regression checks for release lookup, including unpublished drafts."""
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('ci_release', Path(__file__).with_name('ci-release.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseLookupTests(unittest.TestCase):
    def test_draft_on_later_page_is_found(self):
        draft = {'id': 42, 'tag_name': 'v1.3.0', 'draft': True}
        with patch.object(release, 'run', return_value=json.dumps([[{'tag_name': 'v1.2.0'}], [draft]])) as run:
            self.assertEqual(release.find_release('owner/repo', 'v1.3.0'), draft)
            run.assert_called_once_with('gh', 'api', '--paginate', '--slurp',
                                        'repos/owner/repo/releases?per_page=100')

    def test_absent_release(self):
        with patch.object(release, 'run', return_value='[[]]'):
            self.assertIsNone(release.find_release('owner/repo', 'v1.3.0'))

    def test_duplicate_tag_is_rejected(self):
        with patch.object(release, 'run', return_value=json.dumps([[{'tag_name': 'v1.3.0'}] * 2])):
            with self.assertRaisesRegex(ValueError, 'Multiple releases'):
                release.find_release('owner/repo', 'v1.3.0')

    def test_api_error_is_not_treated_as_missing(self):
        with patch.object(release, 'run', side_effect=subprocess.CalledProcessError(1, 'gh')):
            with self.assertRaises(subprocess.CalledProcessError):
                release.find_release('owner/repo', 'v1.3.0')


if __name__ == '__main__':
    unittest.main()
