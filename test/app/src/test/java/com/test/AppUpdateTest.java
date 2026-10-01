package com.test;
import android.content.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class AppUpdateTest {
    private byte[] apk,manifest;
    private JSONObject entry;
    private String cert;
    private AppIdentity installed;
    private final Map<String,byte[]> network=new HashMap<>();
    private final List<String> requests=new ArrayList<>();
    private File cache;
    @Before public void setup() throws Exception {
        apk=new byte[]{0x50,0x4b,3,4,5};cert=OtaImage.hash(new Signature("1234").toByteArray());
        installed=new AppIdentity("1.2.1",6,cert);cache=Files.createTempDirectory("app-update-test").toFile();
        JSONObject meta=new JSONObject().put("format",1).put("application_id","com.test").put("version","1.3.0")
            .put("version_code",7).put("min_sdk",21).put("size",apk.length).put("sha256",OtaImage.hash(apk))
            .put("certificate_sha256",cert).put("source_commit",String.join("",Collections.nCopies(40,"a")));
        manifest=(meta.toString()+"\n").getBytes(StandardCharsets.UTF_8);
        entry=new JSONObject().put("tag_name","app-v1.3.0").put("draft",false).put("prerelease",true)
            .put("assets",new JSONArray().put(asset("apk",apk)).put(asset("json",manifest)));
        network.put("https://api.github.com/repos/"+AppRelease.REPOSITORY+"/releases?per_page=100&page=1",new JSONArray().put(entry).toString().getBytes(StandardCharsets.UTF_8));
        network.put(url("apk"),apk);network.put(url("json"),manifest);
    }
    private String url(String extension) { return "https://github.com/"+AppRelease.REPOSITORY+"/releases/download/app-v1.3.0/Smartglasses-App-1.3.0."+extension; }
    private JSONObject asset(String extension,byte[] data) throws Exception {
        return new JSONObject().put("name","Smartglasses-App-1.3.0."+extension).put("state","uploaded")
            .put("size",data.length).put("digest","sha256:"+OtaImage.hash(data)).put("browser_download_url",url(extension));
    }
    private AppUpdateRepository repository(AppUpdateRepository.Verifier verify) {
        return new AppUpdateRepository(cache,(url,limit)->{requests.add(url);byte[] data=network.get(url);if(data==null) throw new IOException("Offline");assertTrue(data.length<=limit);return data;},verify);
    }
    private AppRelease candidate() throws Exception { AppRelease r=AppRelease.parse(new JSONArray().put(entry)).get(0);r.manifest(manifest);return r; }
    @Test public void automaticCheckOnlyDownloadsMetadataAndRequiresNewerCode() throws Exception {
        AppUpdateRepository repository=repository((file,release)->fail("No APK before consent"));
        AppRelease next=repository.refresh(installed,35);assertEquals(7,next.code);
        assertFalse(requests.contains(url("apk")));assertNotNull(repository.cached(installed,35));
        assertFalse(next.newerThan(new AppIdentity("1.3.0",7,cert),35));
        assertFalse(next.newerThan(new AppIdentity("1.2.1",8,cert),35));
    }
    @Test public void approvedDownloadVerifiesBytesAndArchiveAndRejectsCorruptCache() throws Exception {
        int[] verified={0};AppUpdateRepository repository=repository((file,release)->verified[0]++);
        AppRelease next=repository.refresh(installed,35);File file=repository.download(next);assertEquals(1,verified[0]);
        network.remove(url("apk"));repository.download(next);assertEquals(2,verified[0]);
        Files.write(file.toPath(),new byte[]{0});
        try { repository.download(next);fail(); }catch(IOException expected) { }
        assertEquals(2,verified[0]);
    }
    @Test public void alteredBytesAndManifestVersionNeverReachInstaller() throws Exception {
        AppUpdateRepository repository=repository((file,release)->fail());AppRelease next=candidate();network.put(url("apk"),new byte[]{1,2,3,4,5});
        try { repository.download(next);fail(); }catch(IllegalArgumentException expected) { }
        try { next.manifest(new byte[]{1});fail(); }catch(IllegalArgumentException expected) { }
    }
    @Test public void incompleteDraftAndForeignAssetsAreIgnored() throws Exception {
        entry.put("draft",true);assertTrue(AppRelease.parse(new JSONArray().put(entry)).isEmpty());entry.put("draft",false);
        entry.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://github.com/other/repo/test.apk");
        assertTrue(AppRelease.parse(new JSONArray().put(entry)).isEmpty());
        entry.getJSONArray("assets").getJSONObject(0).put("browser_download_url",url("apk")).remove("digest");
        assertTrue(AppRelease.parse(new JSONArray().put(entry)).isEmpty());
    }
    @Test public void wrongSignerAndUnsupportedSdkCannotBeOffered() throws Exception {
        AppRelease next=candidate();assertFalse(next.newerThan(new AppIdentity("1.2.1",6,"different"),35));
        next.minSdk=36;assertFalse(next.newerThan(installed,35));
    }
    @Test public void archiveMustMatchInstalledCertificateNameCodeAndPackage() throws Exception {
        AppRelease next=candidate();PackageInfo p=new PackageInfo();p.packageName="com.test";p.versionName="1.3.0";p.versionCode=7;
        p.signatures=new Signature[]{new Signature("1234")};p.applicationInfo=new ApplicationInfo();p.applicationInfo.minSdkVersion=21;
        AppIdentity.verifyArchive(p,next,installed);
        p.packageName="other";reject(p,next);p.packageName="com.test";
        p.versionCode=8;reject(p,next);p.versionCode=7;
        p.versionName="1.4.0";reject(p,next);p.versionName="1.3.0";
        p.signatures=new Signature[]{new Signature("abcd")};reject(p,next);
    }
    private void reject(PackageInfo p,AppRelease release) { try { AppIdentity.verifyArchive(p,release,installed);fail(); }catch(IllegalArgumentException expected) { } }
    @Test public void apkProviderIsReadOnlyAndCannotReachPrivateFiles() throws Exception {
        Context context=RuntimeEnvironment.getApplication();String hash=OtaImage.hash(apk);File folder=new File(context.getCacheDir(),"app-updates");assertTrue(folder.isDirectory() || folder.mkdirs());
        File apkFile=new File(folder,hash+".apk");Files.write(apkFile.toPath(),apk);
        AppApkProvider provider=Robolectric.buildContentProvider(AppApkProvider.class).create().get();
        Uri uri=Uri.parse("content://com.test.appupdates/apk/"+hash+".apk");
        try(ParcelFileDescriptor ignored=provider.openFile(uri,"r")) { assertNotNull(ignored); }
        try { provider.openFile(uri,"rw");fail(); }catch(FileNotFoundException expected) { }
        try { provider.openFile(Uri.parse("content://com.test.appupdates/apk/../../shared_prefs/glasses.xml"),"r");fail(); }catch(FileNotFoundException expected) { }
        try { provider.openFile(Uri.parse("content://foreign/apk/"+hash+".apk"),"r");fail(); }catch(FileNotFoundException expected) { }
    }
    @Test public void appRepositoryWhitelistRejectsOtherRepositoriesAndHttp() throws Exception {
        assertTrue(FirmwareRepository.HttpsTransport.allowed(new URL(url("apk")),true,AppRelease.REPOSITORY));
        assertFalse(FirmwareRepository.HttpsTransport.allowed(new URL(url("apk")),true,FirmwareRelease.REPOSITORY));
        assertFalse(FirmwareRepository.HttpsTransport.allowed(new URL(url("apk").replace("https:","http:")),true,AppRelease.REPOSITORY));
    }
    private PackageInfo archive() {
        PackageInfo p=new PackageInfo();p.packageName="com.test";p.versionName="1.3.0";p.versionCode=7;
        p.signatures=new Signature[]{new Signature("1234")};p.signingInfo=new SigningInfo();Shadows.shadowOf(p.signingInfo).setSignatures(p.signatures);
        p.applicationInfo=new ApplicationInfo();p.applicationInfo.minSdkVersion=21;return p;
    }
    private AppUpdates uiUpdates() throws Exception {
        Context context=RuntimeEnvironment.getApplication();
        PackageInfo existing=Shadows.shadowOf(context.getPackageManager()).getInternalMutablePackageInfo("com.test");
        existing.versionName="1.2.1";existing.versionCode=6;existing.signatures=new Signature[]{new Signature("1234")};
        existing.signingInfo=new SigningInfo();Shadows.shadowOf(existing.signingInfo).setSignatures(existing.signatures);
        AppUpdates updates=AppUpdates.get(context);ReflectionHelpers.setField(updates,"installed",installed);
        AppUpdateRepository repo=new AppUpdateRepository(new File(context.getCacheDir(),"app-updates"),
            (url,limit)->{ requests.add(url);return network.get(url); },(file,release)->Shadows.shadowOf(context.getPackageManager()).setPackageArchiveInfo(file.getAbsolutePath(),archive()));
        ReflectionHelpers.setField(updates,"repository",repo);updates.offer=candidate();
        context.getSharedPreferences("app_updates",0).edit().putLong("checked",System.currentTimeMillis()).apply();return updates;
    }
    private void finishWork(AppUpdates updates) throws Exception {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        ((ExecutorService)ReflectionHelpers.getField(updates,"worker")).submit(()->{}).get(20,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @After public void stopWorker() {
        AppUpdates.installationPending=false;
        ((ExecutorService)ReflectionHelpers.getField(AppUpdates.get(RuntimeEnvironment.getApplication()),"worker")).shutdownNow();
    }
    @Test public void laterDoesNotDownloadAndSuppressesOnlyThatVersion() throws Exception {
        AppUpdates updates=uiUpdates();
        try(ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()) {
            AppUpdateUi ui=new AppUpdateUi(controller.get(),Ui.page(controller.get()));ui.start();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue(requests.isEmpty());assertFalse(updates.shouldPrompt());ui.stop();
            updates.prompted=0;assertFalse(updates.shouldPrompt());updates.check(true);finishWork(updates);
            assertTrue(updates.shouldPrompt());assertFalse(requests.contains(url("apk")));
        }
    }
    @Test public void approvalDownloadsVerifiedApkAndOpensReadOnlyAndroidInstaller() throws Exception {
        AppUpdates updates=uiUpdates();Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).setCanRequestPackageInstalls(true);
        try(ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()) {
            AppUpdateUi ui=new AppUpdateUi(controller.get(),Ui.page(controller.get()));ui.start();
            assertTrue(requests.isEmpty());assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();finishWork(updates);
            assertTrue(requests.contains(url("apk")));Intent intent=Shadows.shadowOf(controller.get()).getNextStartedActivity();assertNotNull(intent);
            assertEquals(Intent.ACTION_VIEW,intent.getAction());assertEquals("application/vnd.android.package-archive",intent.getType());
            assertEquals("content",intent.getData().getScheme());assertEquals("com.test.appupdates",intent.getData().getAuthority());
            assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);assertEquals(0,intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            assertNotNull(intent.getClipData());assertTrue(AppUpdates.installationPending);
            ui.result(80);assertFalse(AppUpdates.installationPending);ui.stop();
        }
    }
    @Test public void unknownSourcePermissionRequiresExplicitSettingsAndInstallRetry() throws Exception {
        AppUpdates updates=uiUpdates();Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).setCanRequestPackageInstalls(false);
        try(ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup()) {
            AppUpdateUi ui=new AppUpdateUi(controller.get(),Ui.page(controller.get()));ui.start();
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();finishWork(updates);
            assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());assertFalse(AppUpdates.installationPending);
            ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Intent settings=Shadows.shadowOf(controller.get()).getNextStartedActivity();assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,settings.getAction());
            assertEquals("package:com.test",settings.getData().toString());ui.stop();
        }
    }
}
