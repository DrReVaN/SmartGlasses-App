package com.test;

import org.json.*;
import java.net.URI;
import java.util.*;

/** Complete, immutable APK/manifest pairs from the app repository. */
public final class AppRelease {
    public static final String REPOSITORY="DrReVaN/SmartGlasses-App";
    public static final int MAX_APK=20*1024*1024;
    public final FirmwareVersion version;
    public final String apkUrl, manifestUrl, apkDigest, manifestDigest;
    public final boolean development;
    public final long apkSize;
    public long code;
    public int minSdk;
    public String signer, source;
    private AppRelease(FirmwareVersion v,String apk,String json,String hash,String manifestHash,long size,boolean dev) {
        version=v;apkUrl=apk;manifestUrl=json;apkDigest=hash;manifestDigest=manifestHash;apkSize=size;development=dev;
    }
    public static List<AppRelease> parse(JSONArray entries) {
        List<AppRelease> releases=new ArrayList<>();Set<FirmwareVersion> seen=new HashSet<>();
        for(int i=0;i<entries.length() && i<300;i++) try {
            JSONObject entry=entries.getJSONObject(i);
            if(entry.getBoolean("draft")) continue;
            String tag=entry.getString("tag_name");if(!tag.startsWith("app-v")) continue;
            FirmwareVersion version=FirmwareVersion.parse(tag.substring(5));
            String name="Smartglasses-App-"+version;
            JSONObject apk=null,json=null;JSONArray assets=entry.getJSONArray("assets");
            for(int j=0;j<assets.length();j++) {
                JSONObject asset=assets.getJSONObject(j);if(!"uploaded".equals(asset.getString("state"))) continue;
                if((name+".apk").equals(asset.getString("name"))) { if(apk!=null) throw new JSONException("Duplicate");apk=asset; }
                if((name+".json").equals(asset.getString("name"))) { if(json!=null) throw new JSONException("Duplicate");json=asset; }
            }
            if(apk==null || json==null || apk.getLong("size")<=0 || apk.getLong("size")>MAX_APK || json.getLong("size")<=0 || json.getLong("size")>16384) continue;
            String apkUrl=apk.getString("browser_download_url"),jsonUrl=json.getString("browser_download_url");
            if(!url(apkUrl,tag,name+".apk") || !url(jsonUrl,tag,name+".json")) continue;
            String hash=digest(apk),manifestHash=digest(json);if(!seen.add(version)) continue;
            releases.add(new AppRelease(version,apkUrl,jsonUrl,hash,manifestHash,apk.getLong("size"),entry.getBoolean("prerelease")));
        } catch(JSONException | IllegalArgumentException ignored) { /* Never offer partial or unrelated releases. */ }
        Collections.sort(releases,(a,b)->b.version.compareTo(a.version));return Collections.unmodifiableList(releases);
    }
    private static String digest(JSONObject asset) throws JSONException {
        String digest=asset.getString("digest");if(!digest.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("Digest");return digest.substring(7);
    }
    private static boolean url(String value,String tag,String file) {
        URI uri=URI.create(value);
        return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost()) && uri.getPort()==-1 && uri.getUserInfo()==null
            && uri.getRawQuery()==null && uri.getRawFragment()==null && ("/"+REPOSITORY+"/releases/download/"+tag+"/"+file).equals(uri.getRawPath());
    }
    public void manifest(byte[] bytes) throws JSONException {
        if(!OtaImage.hash(bytes).equals(manifestDigest)) throw new IllegalArgumentException("Manifest checksum");
        JSONObject m=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        if(m.getInt("format")!=1 || !"com.test".equals(m.getString("application_id")) || !version.toString().equals(m.getString("version"))
            || !apkDigest.equals(m.getString("sha256")) || m.getLong("size")!=apkSize) throw new IllegalArgumentException("Manifest identity");
        long next=m.getLong("version_code");int sdk=m.getInt("min_sdk");String cert=m.getString("certificate_sha256"),commit=m.getString("source_commit");
        if(next<=0 || next>2100000000L || !m.get("version_code").toString().equals(Long.toString(next)) || sdk<21 || sdk>1000
            || !cert.matches("[0-9a-f]{64}") || !commit.matches("[0-9a-f]{40}")) throw new IllegalArgumentException("Manifest values");
        code=next;minSdk=sdk;signer=cert;source=commit;
    }
    public boolean newerThan(AppIdentity installed,int sdk) {
        return code>installed.code && version.compareTo(installed.version)>0 && minSdk<=sdk && Objects.equals(signer,installed.signer);
    }
}
