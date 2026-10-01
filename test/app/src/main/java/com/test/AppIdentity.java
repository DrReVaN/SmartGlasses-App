package com.test;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.*;
import android.os.Build;
import java.io.File;

/** Android's installed identity is the authority for replacement compatibility. */
public final class AppIdentity {
    public final FirmwareVersion version;
    public final long code;
    public final String signer;
    AppIdentity(String version,long code,String signer) { this.version=FirmwareVersion.parse(version);this.code=code;this.signer=signer; }
    static int flags() { return Build.VERSION.SDK_INT>=28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES; }
    @SuppressLint("PackageManagerGetSignatures")
    static String certificate(PackageInfo info) {
        Signature[] signatures=Build.VERSION.SDK_INT>=28 && info.signingInfo!=null ? info.signingInfo.getApkContentsSigners() : info.signatures;
        if(signatures==null || signatures.length!=1) throw new IllegalArgumentException("Signing identity");
        return OtaImage.hash(signatures[0].toByteArray());
    }
    static long code(PackageInfo info) { return Build.VERSION.SDK_INT>=28 ? info.getLongVersionCode() : info.versionCode; }
    static AppIdentity installed(Context context) {
        try { PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),flags());return new AppIdentity(p.versionName,code(p),certificate(p)); }
        catch(PackageManager.NameNotFoundException e) { throw new IllegalArgumentException("Installed app",e); }
    }
    static void verify(Context context,File file,AppRelease release) {
        PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags());
        verifyArchive(archive,release,installed(context));
    }
    static void verifyArchive(PackageInfo archive,AppRelease release,AppIdentity installed) {
        if(archive==null || !"com.test".equals(archive.packageName) || !release.version.toString().equals(archive.versionName)
            || code(archive)!=release.code || !release.signer.equals(certificate(archive)) || !release.newerThan(installed,Build.VERSION.SDK_INT)
            || (Build.VERSION.SDK_INT>=24 && (archive.applicationInfo==null || archive.applicationInfo.minSdkVersion!=release.minSdk)))
            throw new IllegalArgumentException("APK is not a compatible update");
    }
}
