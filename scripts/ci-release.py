"""Release checks and packaging shared by local builds and GitHub Actions."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def run(*args):
    return subprocess.check_output(args, text=True, encoding="utf-8").strip()


def metadata():
    gradle = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
    version = re.search(r'versionName "([^"]+)"', gradle)[1]
    code = int(re.search(r'versionCode (\d+)', gradle)[1])
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?', version):
        raise ValueError('Invalid versionName')
    if os.environ.get('GITHUB_REF_TYPE') == 'tag':
        if os.environ['GITHUB_REF_NAME'] != 'v' + version:
            raise ValueError('Tag must equal v + versionName')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as file:
            file.write(f'version={version}\ncode={code}\n')
    return version, code


def android(apksigner, aapt):
    version, code = metadata()
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    certificate = run(apksigner, 'verify', '--print-certs', str(apk))
    fingerprints = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)', certificate)
    expected = (ROOT / 'config/android-signing.sha256').read_text().strip()
    if fingerprints != [expected]:
        raise ValueError('APK signing certificate does not match the trusted preview certificate')
    badging = run(aapt, 'dump', 'badging', str(apk))
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package.groups() != ('com.github.ifeel.keysjoy.usb', str(code), version):
        raise ValueError('APK package/version differs from source')
    destination = ROOT / 'dist/android'
    destination.mkdir(parents=True, exist_ok=False)
    output = destination / f'MambaPad-{version}.apk'
    shutil.copyfile(apk, output)
    with zipfile.ZipFile(destination / f'MambaPad-Android-{version}.zip', 'x', zipfile.ZIP_DEFLATED) as archive:
        archive.write(output, output.name)
        for name in ('LICENSE', 'NOTICE.txt'):
            archive.write(ROOT / name, name)
        archive.writestr('README.txt', 'MambaPad Android ' + version + '\nAndroid 9+. Install the APK to update compatible previews.\nUses the existing debug certificate; Windows files are unsigned.\nInstructions: https://github.com/FFraankk/MambaPad#readme\n')
    print('APK identity, version and signing certificate verified')


def assets(version):
    return [f'MambaPad-{version}.apk', f'MambaPad-Android-{version}.zip',
            f'MambaPad-Windows-x64-{version}-Portable.zip', f'MambaPad-Windows-x64-{version}-Setup.exe']


def digest(file):
    return hashlib.sha256(file.read_bytes()).hexdigest()


def manifest(directory):
    version, _ = metadata()
    expected = set(assets(version))
    if {p.name for p in directory.iterdir()} != expected:
        raise ValueError('Release must contain exactly the four expected packages')
    for name in expected:
        file = directory / name
        if not file.stat().st_size:
            raise ValueError('Empty package: ' + name)
        if file.suffix == '.zip':
            with zipfile.ZipFile(file) as archive:
                if archive.testzip():
                    raise ValueError('Invalid ZIP: ' + name)
                names = archive.namelist()
                for required in ('LICENSE', 'NOTICE.txt'):
                    if not any(n == required or n.endswith('/' + required) for n in names):
                        raise ValueError('Missing license: ' + name)
                if any(n.lower().endswith(('.keystore', '.jks', '.p12', '/adbkey', '/adbkey.pub')) for n in names):
                    raise ValueError('Private material in ZIP')
    (directory / 'SHA256SUMS.txt').write_text(''.join(f'{digest(directory / n)}  {n}\n' for n in sorted(expected)), encoding='ascii')
    print('Four release packages and SHA256 manifest verified')


def find_release(repo, tag):
    # The tag endpoint may return 404 for drafts; list includes accessible drafts.
    pages = json.loads(run('gh', 'api', '--paginate', '--slurp',
                           f'repos/{repo}/releases?per_page=100'))
    matches = [release for page in pages for release in page if release['tag_name'] == tag]
    if len(matches) > 1:
        raise ValueError('Multiple releases use the requested tag')
    return matches[0] if matches else None


def publish(directory):
    version, _ = metadata()
    tag = 'v' + version
    repo = os.environ['GITHUB_REPOSITORY']
    sha = os.environ['GITHUB_SHA']
    if os.environ.get('GITHUB_EVENT_NAME') != 'push' or os.environ.get('GITHUB_REF') != 'refs/tags/' + tag:
        raise ValueError('Publishing requires an explicit version tag push')
    # An existing release (including a draft) is never overwritten.
    if find_release(repo, tag) is not None:
        raise ValueError('Release already exists; refusing to overwrite it')
    if json.loads(run('gh', 'api', f'repos/{repo}/commits/{tag}'))['sha'] != sha:
        raise ValueError('Remote tag differs from the built commit')
    names = assets(version) + ['SHA256SUMS.txt']
    if {p.name for p in directory.iterdir()} != set(names):
        raise ValueError('Unexpected release assets')
    sums = ''.join(f'{digest(directory / n)}  {n}\n' for n in sorted(assets(version)))
    if (directory / 'SHA256SUMS.txt').read_text() != sums:
        raise ValueError('Release hashes changed after packaging')
    preview = '-' in version
    notes = ('Automated build of commit ' + sha + '.\n\n'
             'Android APK uses the existing preview debug signing certificate; Windows EXE and installer are unsigned. '
             'CI validates Android build/JVM tests, APK identity/certificate, Windows Go tests/vet and package hashes. '
             'CI does not perform physical-device, haptic, tray or installation tests. '
             'Existing Android lint errors remain; lint is not part of this workflow.\n\n'
             'Download APK or Android ZIP; choose Windows Portable ZIP or Setup EXE. Verify SHA256SUMS.txt.\n')
    command = ['gh', 'release', 'create', tag, '--repo', repo, '--verify-tag', '--target', sha,
               '--draft', '--title', 'MambaPad ' + tag, '--notes', notes, '--generate-notes']
    if preview:
        command.append('--prerelease')
    run(*command)
    run('gh', 'release', 'upload', tag, '--repo', repo, *(str(directory / n) for n in names))
    release = find_release(repo, tag)
    if release is None:
        raise ValueError('Created draft could not be found; inspect releases before retrying')
    if len(release['assets']) != len(names):
        raise ValueError('Incorrect remote asset count; draft retained')
    for asset in release['assets']:
        file = directory / asset['name']
        if asset['size'] != file.stat().st_size or asset.get('digest') != 'sha256:' + digest(file):
            raise ValueError('Remote asset digest differs; draft retained')
    run('gh', 'release', 'edit', tag, '--repo', repo, '--draft=false',
        '--prerelease=' + str(preview).lower(), '--latest=' + str(not preview).lower())
    release = json.loads(run('gh', 'api', f'repos/{repo}/releases/{release["id"]}'))
    if release['draft'] or release['prerelease'] != preview:
        raise ValueError('Unexpected public release state')
    print(release['html_url'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['metadata', 'android', 'manifest', 'publish'])
    parser.add_argument('--apksigner')
    parser.add_argument('--aapt')
    parser.add_argument('--directory', type=Path, default=ROOT / 'dist/release')
    args = parser.parse_args()
    if args.command == 'metadata':
        print(metadata())
    elif args.command == 'android':
        android(args.apksigner, args.aapt)
    elif args.command == 'manifest':
        manifest(args.directory)
    else:
        publish(args.directory)
