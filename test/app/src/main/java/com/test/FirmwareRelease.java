package com.test;

import org.json.*;
import java.net.URI;
import java.util.*;

/** Only complete CPU1 OTA release pairs from the project's repository are listed. */
public final class FirmwareRelease {
    public static final String REPOSITORY="DrReVaN/Glasses_V0.1_BLE";
    public final FirmwareVersion version;
    public final String binaryUrl, manifestUrl, binaryDigest, manifestDigest;
    public final boolean development;
    private FirmwareRelease(FirmwareVersion version,String binary,String manifest,String binHash,String jsonHash,boolean development) {
        this.version=version; binaryUrl=binary; manifestUrl=manifest;
        binaryDigest=binHash; manifestDigest=jsonHash; this.development=development;
    }
    public static List<FirmwareRelease> parse(JSONArray source) {
        List<FirmwareRelease> releases=new ArrayList<>(); Set<FirmwareVersion> seen=new HashSet<>();
        for(int i=0;i<source.length() && releases.size()<300;i++) try {
            JSONObject entry=source.getJSONObject(i);
            if(entry.getBoolean("draft")) continue;
            String tag=entry.getString("tag_name");
            if(!tag.startsWith("v")) continue;
            FirmwareVersion v=FirmwareVersion.parse(tag.substring(1));
            if(v.compareTo(FirmwareVersion.parse("0.2.0"))<0) continue;
            String prefix="Smartglasses-"+v+"-OTA";
            JSONObject binary=null, manifest=null; JSONArray assets=entry.getJSONArray("assets");
            for(int j=0;j<assets.length();j++) {
                JSONObject asset=assets.getJSONObject(j);
                if(!"uploaded".equals(asset.getString("state"))) continue;
                if((prefix+".bin").equals(asset.getString("name"))) { if(binary!=null) throw new JSONException("Duplicate"); binary=asset; }
                if((prefix+".json").equals(asset.getString("name"))) { if(manifest!=null) throw new JSONException("Duplicate"); manifest=asset; }
            }
            if(binary==null || manifest==null || binary.getLong("size")<0x140 || binary.getLong("size")>OtaImage.MAX_SIZE
                    || manifest.getLong("size")<=0 || manifest.getLong("size")>16384) continue;
            String binUrl=binary.getString("browser_download_url"), jsonUrl=manifest.getString("browser_download_url");
            if(!assetUrl(binUrl,tag,prefix+".bin") || !assetUrl(jsonUrl,tag,prefix+".json") || !seen.add(v)) continue;
            String binHash=digest(binary), jsonHash=digest(manifest);
            releases.add(new FirmwareRelease(v,binUrl,jsonUrl,binHash,jsonHash,entry.getBoolean("prerelease")));
        } catch(JSONException | IllegalArgumentException ignored) { /* Incomplete/unrelated release: never offer it for flashing. */ }
        Collections.sort(releases,(a,b) -> b.version.compareTo(a.version));
        return Collections.unmodifiableList(releases);
    }
    private static boolean assetUrl(String value,String tag,String name) {
        try {
            URI uri=URI.create(value);
            return "https".equals(uri.getScheme()) && "github.com".equals(uri.getHost()) && uri.getPort()==-1
                && uri.getUserInfo()==null && uri.getRawQuery()==null && uri.getRawFragment()==null
                && ("/"+REPOSITORY+"/releases/download/"+tag+"/"+name).equals(uri.getRawPath());
        } catch(IllegalArgumentException e) { return false; }
    }
    private static String digest(JSONObject asset) {
        String hash=asset.optString("digest","");
        if(hash.isEmpty()) return "";
        if(!hash.matches("sha256:[0-9a-fA-F]{64}")) throw new IllegalArgumentException("Digest");
        return hash.substring(7);
    }
    public void verifyManifest(JSONObject manifest) throws JSONException {
        if(!version.toString().equals(manifest.getString("version"))) throw new IllegalArgumentException("Release und Paketversion stimmen nicht überein.");
    }
    public static FirmwareRelease newest(List<FirmwareRelease> catalog,FirmwareVersion installed) {
        if(catalog.isEmpty()) return null;
        // No trustworthy identity on original builds: present a migration choice.
        FirmwareVersion baseline=installed==null ? FirmwareVersion.parse("0.2.0") : installed;
        for(FirmwareRelease release:catalog) if(release.version.compareTo(baseline)>0) return release;
        return null;
    }
}
