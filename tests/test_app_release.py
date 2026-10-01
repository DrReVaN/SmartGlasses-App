import importlib.util
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec=importlib.util.spec_from_file_location('app_release',Path(__file__).resolve().parents[1]/'tools/app_release.py')
release=importlib.util.module_from_spec(spec);spec.loader.exec_module(release)

class AppReleaseTests(unittest.TestCase):
    def test_ci_comparison_ignores_signatures_but_checks_every_packaged_input(self):
        with tempfile.TemporaryDirectory() as temporary:
            a,b=[Path(temporary)/name for name in ['signed.apk','ci.apk']]
            def apk(path,signature,code):
                with zipfile.ZipFile(path,'w') as z:
                    z.writestr('classes.dex',code);z.writestr('AndroidManifest.xml','manifest');z.writestr('resources.arsc','resources')
                    z.writestr('META-INF/CERT.RSA',signature)
            apk(a,'owner','same code');apk(b,'CI','same code');release.compare_builds(a,b)
            apk(b,'CI','different code')
            with self.assertRaises(ValueError):release.compare_builds(a,b)

    def test_publication_uses_draft_id_and_never_overwrites_an_existing_apk(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);directory=root/'releases/android/1.3.0';directory.mkdir(parents=True)
            docs=root/'docs';docs.mkdir();(docs/'APP_UPDATES.md').write_text('Instructions')
            apk=directory/'Smartglasses-App-1.3.0.apk';apk.write_bytes(b'APK')
            metadata=directory/'Smartglasses-App-1.3.0.json';metadata.write_text('{}')
            state={'release':None,'tag':None};mutations=[]
            info={'version':'1.3.0','source_commit':'1'*40}
            def run(*args):
                if args[1]=='ls-remote':return (state['tag']+'\trefs/tags/app-v1.3.0') if state['tag'] else ''
                return ''
            def push(*args,**kwargs):state['tag']='1'*40
            def request(method,path,payload=None,file=None):
                self.assertNotIn('/tags/',path)
                if method=='GET' and path.endswith('?per_page=100'):return [state['release']] if state['release'] else []
                if method=='POST' and file is None:
                    mutations.append('create');state['release']={'id':11,'draft':True,'assets':[],'tag_name':'app-v1.3.0'};return state['release']
                if method=='POST':
                    self.assertIn('/11/assets?name=',path);self.assertTrue(state['release']['draft']);mutations.append('upload')
                    asset={'name':file.name,'digest':'sha256:'+hashlib.sha256(file.read_bytes()).hexdigest()};state['release']['assets'].append(asset);return asset
                if method=='PATCH':
                    self.assertEqual(len(state['release']['assets']),2);mutations.append('publish');state['release']['draft']=False
                return state['release']
            with patch.object(release,'ROOT',root),patch.object(release,'current',lambda:('1.3.0',7)),patch.object(release,'verified',lambda:info),patch.object(release,'run',run),patch.object(release,'request',request),patch.object(release.subprocess,'run',push),patch.dict(os.environ,{'GITHUB_REPOSITORY':release.REPO}):
                release.publish();self.assertFalse(state['release']['draft']);before=list(mutations)
                release.publish();self.assertEqual(before,mutations)
                apk.write_bytes(b'Changed')
                with self.assertRaises(ValueError):release.publish()
                self.assertEqual(before,mutations)

    def test_unprepared_version_does_not_publish(self):
        with patch.object(release,'verified',lambda:None),patch.object(release,'request') as request,patch.dict(os.environ,{'GITHUB_REPOSITORY':release.REPO}):
            release.publish();request.assert_not_called()

if __name__=='__main__':unittest.main()
