package com.test;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Checks metadata automatically; downloads APKs only following user approval. */
final class AppUpdateRepository {
    interface Verifier { void verify(File apk,AppRelease release); }
    private final File directory;
    private final FirmwareRepository.Transport transport;
    private final Verifier verifier;
    AppUpdateRepository(File directory,FirmwareRepository.Transport transport,Verifier verifier) {
        this.directory=directory;this.transport=transport;this.verifier=verifier;
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IllegalArgumentException("Update cache");
    }
    AppRelease refresh(AppIdentity installed,int sdk) throws IOException,JSONException {
        JSONArray all=new JSONArray();String api="https://api.github.com/repos/"+AppRelease.REPOSITORY+"/releases";
        for(int page=1;page<=3;page++) {
            JSONArray batch=new JSONArray(new String(transport.get(api+"?per_page=100&page="+page,2000000),StandardCharsets.UTF_8));
            for(int i=0;i<batch.length() && all.length()<300;i++) all.put(batch.get(i));if(batch.length()<100) break;
        }
        int checked=0;
        for(AppRelease release:AppRelease.parse(all)) {
            if(release.version.compareTo(installed.version)<=0 || ++checked>32) continue;
            byte[] manifest=transport.get(release.manifestUrl,16384);
            release.manifest(manifest);
            if(!release.newerThan(installed,sdk)) continue;
            JSONObject cache=new JSONObject().put("releases",all).put("manifest_bytes",android.util.Base64.encodeToString(manifest,android.util.Base64.NO_WRAP));
            write(new File(directory,"offer.json"),cache.toString().getBytes(StandardCharsets.UTF_8));return release;
        }
        new File(directory,"offer.json").delete();return null;
    }
    AppRelease cached(AppIdentity installed,int sdk) {
        try {
            JSONObject data=new JSONObject(new String(read(new File(directory,"offer.json"),2200000),StandardCharsets.UTF_8));
            // Keep original manifest bytes: digest binding must survive JSON formatting.
            byte[] manifest=android.util.Base64.decode(data.getString("manifest_bytes"),android.util.Base64.NO_WRAP);
            for(AppRelease r:AppRelease.parse(data.getJSONArray("releases"))) try { r.manifest(manifest);if(r.newerThan(installed,sdk)) return r; }
                catch(JSONException | IllegalArgumentException ignored) { /* Not the cached offer. */ }
        } catch(IOException | JSONException | IllegalArgumentException ignored) { /* Empty or invalid cache. */ }
        return null;
    }
    File download(AppRelease release) throws IOException {
        File apk=new File(directory,release.apkDigest+".apk");
        byte[] bytes=null;
        if(apk.isFile()) try { bytes=read(apk,AppRelease.MAX_APK);check(release,bytes); }
            catch(IOException | IllegalArgumentException e) { bytes=null; }
        if(bytes==null) { bytes=transport.get(release.apkUrl,AppRelease.MAX_APK);check(release,bytes);write(apk,bytes); }
        verifier.verify(apk,release);return apk;
    }
    static void check(AppRelease release,byte[] bytes) {
        if(bytes.length!=release.apkSize || !OtaImage.hash(bytes).equals(release.apkDigest)) throw new IllegalArgumentException("APK checksum");
    }
    static byte[] read(File file,int limit) throws IOException {
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1) { if(out.size()+n>limit) throw new IOException("Size");out.write(block,0,n); }return out.toByteArray();
        }
    }
    private static void write(File file,byte[] bytes) throws IOException {
        android.util.AtomicFile atomic=new android.util.AtomicFile(file);FileOutputStream out=atomic.startWrite();
        try { out.write(bytes);atomic.finishWrite(out); }catch(IOException e) { atomic.failWrite(out);throw e; }
    }
}
