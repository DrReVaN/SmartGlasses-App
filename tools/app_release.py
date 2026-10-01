"""Publish locally signed, CI-verified APKs without putting private keys on GitHub."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]
REPO='DrReVaN/SmartGlasses-App'
CERTIFICATE='baa61692f831dec85c3d53b26046a783724f98260fedb57cdab8b09f9f983b1c'

def run(*args):
    if args[0]=='git': args=('git','-c','safe.directory='+ROOT.as_posix(),*args[1:])
    return subprocess.check_output(args,cwd=ROOT,text=True).strip()

def current():
    text=(ROOT/'test/app/build.gradle').read_text(encoding='utf-8')
    version=re.search(r'versionName "([0-9]+\.[0-9]+\.[0-9]+)"',text).group(1)
    code=int(re.search(r'versionCode ([0-9]+)',text).group(1))
    if not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)',version) or any(int(part)>65535 for part in version.split('.')) or not 0<code<=2100000000:
        raise ValueError('Version definition')
    return version,code

def folder(): return ROOT/'releases/android'/current()[0]
def name(): return 'Smartglasses-App-'+current()[0]

def validate_source(source):
    if not re.fullmatch('[0-9a-f]{40}',source): raise ValueError('Source commit')
    # The package commit adds only signed artifacts. Publisher-only repairs may
    # also preserve a release's already assigned source identity.
    changed=run('git','diff','--name-only',source,'HEAD').splitlines()
    if any(not p.startswith('releases/android/') and p not in {'tools/app_release.py','tests/test_app_release.py'} for p in changed):
        raise ValueError('APK source differs from current application/build inputs')
    for path in run('git','diff','--name-only','HEAD','--','test').splitlines():
        # Linux CI makes the checked-in wrapper executable before building.
        # Permit that mode-only change, never modified wrapper/source contents.
        if path=='test/gradlew' and run('git','diff','--numstat','HEAD','--',path)=='0\t0\ttest/gradlew': continue
        raise ValueError('Uncommitted app inputs')

def verified(sdk=None,reference=None):
    directory=folder()
    if not directory.exists(): return None
    apk=directory/(name()+'.apk');meta=directory/(name()+'.json')
    info=json.loads(meta.read_text(encoding='utf-8'));data=apk.read_bytes();version,code=current()
    if info.get('format')!=1 or info.get('application_id')!='com.test' or info.get('version')!=version or info.get('version_code')!=code:
        raise ValueError('Package/version identity')
    if info.get('size')!=len(data) or not 0<len(data)<=20*1024*1024 or info.get('sha256')!=hashlib.sha256(data).hexdigest():
        raise ValueError('APK digest/size')
    if info.get('certificate_sha256')!=CERTIFICATE or info.get('min_sdk')!=21: raise ValueError('Compatibility/signing identity')
    validate_source(info['source_commit'])
    if sdk:
        tools=Path(sdk)/'build-tools/35.0.0';windows=os.name=='nt'
        signer=tools/('apksigner.bat' if windows else 'apksigner')
        aapt=tools/('aapt.exe' if windows else 'aapt')
        result=run(str(signer),'verify','--verbose','--print-certs',str(apk))
        certs=re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})',result)
        if certs!=[CERTIFICATE]: raise ValueError('Actual APK certificate differs')
        badging=run(str(aapt),'dump','badging',str(apk))
        if not re.search(r"package: name='com.test' versionCode='"+str(code)+r"' versionName='"+re.escape(version)+"'",badging) or "sdkVersion:'21'" not in badging:
            raise ValueError('Actual APK identity differs')
    if reference: compare_builds(apk,Path(reference))
    return info

def compare_builds(signed,reference):
    def content(path):
        with zipfile.ZipFile(path) as archive:
            return {p:archive.read(p) for p in archive.namelist() if not p.startswith('META-INF/') and not p.endswith('/') and p!='stamp-cert-sha256'}
    actual,expected=content(signed),content(reference)
    different=sorted(p for p in set(actual)|set(expected) if actual.get(p)!=expected.get(p))
    if different: raise ValueError('Signed APK differs from CI build: '+', '.join(different))

def prepare(apk,sdk):
    version,code=current();source=run('git','rev-parse','HEAD');validate_source(source)
    directory=folder()
    if directory.exists(): raise ValueError('Version already packaged; increment before creating another APK')
    directory.mkdir(parents=True)
    target=directory/(name()+'.apk');shutil.copy2(apk,target);data=target.read_bytes()
    info={'format':1,'application_id':'com.test','version':version,'version_code':code,'min_sdk':21,
          'size':len(data),'sha256':hashlib.sha256(data).hexdigest(),'certificate_sha256':CERTIFICATE,'source_commit':source}
    (directory/(name()+'.json')).write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
    verified(sdk)
    print('Prepared compatible signed APK '+version+' from '+source)

def request(method,path,payload=None,file=None):
    args=['gh','api',path,'--method',method]
    if file: return json.loads(run(*args,'-H','Content-Type: application/octet-stream','--input',str(file)))
    if payload is None: return json.loads(run(*args))
    with tempfile.TemporaryDirectory() as temporary:
        p=Path(temporary)/'request.json';p.write_text(json.dumps(payload),encoding='utf-8')
        return json.loads(run(*args,'--input',str(p)))

def publish():
    if os.environ.get('GITHUB_REPOSITORY')!=REPO: raise ValueError('Wrong repository')
    info=verified()
    if info is None: print('No signed APK prepared for current version; nothing published.');return
    version=info['version'];tag='app-v'+version;source=info['source_commit'];directory=folder()
    refs=run('git','ls-remote','--tags','origin','refs/tags/'+tag,'refs/tags/'+tag+'^{}').splitlines()
    if refs:
        resolved=next((line.split()[0] for line in refs if line.endswith('^{}')),refs[0].split()[0])
        if resolved!=source: raise ValueError('Existing version tag belongs to another source')
    else:
        run('git','tag',tag,source);subprocess.run(['git','push','origin','refs/tags/'+tag],cwd=ROOT,check=True)
    base='repos/'+REPO+'/releases'
    releases=request('GET',base+'?per_page=100')
    release=next((r for r in releases if r['tag_name']==tag),None)
    if release is None:
        # Keep the creation response and use the numeric release ID. Drafts
        # are not immediately visible through GitHub's by-tag/list indexes.
        release=request('POST',base,{'tag_name':tag,'target_commitish':source,'name':'Smartglasses Android '+version,
            'body':'Android-App '+version+'. Installation über die vorhandene App mit gleicher Signatur.\n\n'+
            (ROOT/'docs/APP_UPDATES.md').read_text(encoding='utf-8'),'draft':True,'prerelease':True})
    assets=[directory/(name()+'.apk'),directory/(name()+'.json')]
    existing={a['name']:a for a in release['assets']}
    for asset in assets:
        digest='sha256:'+hashlib.sha256(asset.read_bytes()).hexdigest()
        if asset.name in existing:
            if existing[asset.name].get('digest')!=digest: raise ValueError('Published asset differs; never overwrite versions')
        elif release['draft']:
            uploaded=request('POST','https://uploads.github.com/'+base+'/'+str(release['id'])+'/assets?name='+asset.name,file=asset)
            if uploaded['name']!=asset.name or uploaded.get('digest')!=digest: raise ValueError('Upload digest differs')
        else: raise ValueError('Published release incomplete')
    complete=request('GET',base+'/'+str(release['id']))
    actual={a['name']:a for a in complete['assets']}
    if set(actual)!={a.name for a in assets} or any(actual[a.name].get('digest')!='sha256:'+hashlib.sha256(a.read_bytes()).hexdigest() for a in assets):
        raise ValueError('Release verification failed')
    if complete['draft']: request('PATCH',base+'/'+str(release['id']),{'draft':False})
    print('Published verified signed APK '+tag)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--prepare',type=Path);parser.add_argument('--sdk',type=Path)
    parser.add_argument('--reference',type=Path);parser.add_argument('--publish',action='store_true');args=parser.parse_args()
    if args.prepare:
        if not args.sdk: parser.error('--sdk is required for preparing a signed APK')
        prepare(args.prepare,args.sdk)
    elif args.publish: publish()
    else:
        info=verified(args.sdk,args.reference);print('Verified '+info['version'] if info else 'No signed APK for current version yet.')
