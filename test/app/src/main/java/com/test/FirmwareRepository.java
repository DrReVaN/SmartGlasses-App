package com.test;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

/** Anonymous HTTPS downloads, bounded sizes and verified local packages for rollback. */
public final class FirmwareRepository {
    public interface Transport { byte[] get(String url,int limit) throws IOException; }
    public static final String API="https://api.github.com/repos/"+FirmwareRelease.REPOSITORY+"/releases";
    private final File directory;
    private final Transport transport;
    public FirmwareRepository(File directory,Transport transport) {
        this.directory=directory; this.transport=transport;
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IllegalArgumentException("Cache");
    }
    public List<FirmwareRelease> cachedCatalog() {
        try { return FirmwareRelease.parse(new JSONArray(new String(read(new File(directory,"catalog.json"),2000000),StandardCharsets.UTF_8))); }
        catch(IOException | JSONException e) { return Collections.emptyList(); }
    }
    public List<FirmwareRelease> refresh() throws IOException, JSONException {
        JSONArray combined=new JSONArray();
        for(int page=1;page<=3;page++) {
            JSONArray batch=new JSONArray(new String(transport.get(API+"?per_page=100&page="+page,2000000),StandardCharsets.UTF_8));
            for(int i=0;i<batch.length() && combined.length()<300;i++) combined.put(batch.get(i));
            if(batch.length()<100) break;
        }
        byte[] raw=combined.toString().getBytes(StandardCharsets.UTF_8);
        if(raw.length>2000000) throw new IOException("Catalog size");
        List<FirmwareRelease> catalog=FirmwareRelease.parse(combined);
        write(new File(directory,"catalog.json"),raw);
        return catalog;
    }
    public OtaImage load(FirmwareRelease release) throws IOException, JSONException {
        File binary=new File(directory,release.version+".bin"), manifest=new File(directory,release.version+".json");
        if(binary.isFile() && manifest.isFile()) try {
            return validate(release,read(binary,OtaImage.MAX_SIZE),read(manifest,16384));
        } catch(IOException | JSONException | IllegalArgumentException ignored) { /* Reject corrupt cache; fetch a complete pair. */ }
        byte[] json=transport.get(release.manifestUrl,16384);
        byte[] bin=transport.get(release.binaryUrl,OtaImage.MAX_SIZE);
        OtaImage image=validate(release,bin,json);
        write(binary,bin); write(manifest,json);
        return image;
    }
    private static OtaImage validate(FirmwareRelease release,byte[] bin,byte[] json) throws JSONException {
        verifyDigest(bin,release.binaryDigest); verifyDigest(json,release.manifestDigest);
        JSONObject metadata=new JSONObject(new String(json,StandardCharsets.UTF_8)); release.verifyManifest(metadata);
        return OtaImage.fromManifest(bin,metadata);
    }
    private static void verifyDigest(byte[] data,String digest) {
        if(!digest.isEmpty() && !OtaImage.hash(data).equalsIgnoreCase(digest)) throw new IllegalArgumentException("GitHub-Prüfsumme stimmt nicht überein.");
    }
    private static byte[] read(File file,int limit) throws IOException {
        try(InputStream in=new FileInputStream(file)) { return bytes(in,limit); }
    }
    private static byte[] bytes(InputStream in,int limit) throws IOException {
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] block=new byte[4096]; int n;
            while((n=in.read(block))!=-1) { if(out.size()+n>limit) throw new IOException("Size"); out.write(block,0,n); }
            return out.toByteArray();
        }
    }
    private static void write(File file,byte[] data) throws IOException {
        android.util.AtomicFile atomic=new android.util.AtomicFile(file);
        FileOutputStream out=atomic.startWrite();
        try { out.write(data); atomic.finishWrite(out); }
        catch(IOException e) { atomic.failWrite(out); throw e; }
    }
    public static final class HttpsTransport implements Transport {
        private final String repository;
        public HttpsTransport() { this(FirmwareRelease.REPOSITORY); }
        public HttpsTransport(String repository) {
            if(!FirmwareRelease.REPOSITORY.equals(repository) && !"DrReVaN/SmartGlasses-App".equals(repository)) throw new IllegalArgumentException("Repository");
            this.repository=repository;
        }
        @Override public byte[] get(String address,int limit) throws IOException {
            URL url=new URL(address);
            for(int redirects=0;redirects<=5;redirects++) {
                if(!allowed(url,redirects==0,repository)) throw new IOException("Untrusted download address");
                HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();
                connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent","Smartglasses-App");
                connection.setRequestProperty("Accept","api.github.com".equals(url.getHost()) ? "application/vnd.github+json" : "application/octet-stream");
                if("api.github.com".equals(url.getHost())) connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
                try {
                    int code=connection.getResponseCode();
                    if(code==301 || code==302 || code==303 || code==307 || code==308) {
                        String location=connection.getHeaderField("Location"); if(location==null) throw new IOException("Redirect");
                        url=new URL(url,location); continue;
                    }
                    if(code!=200) throw new IOException("GitHub HTTP "+code);
                    if(connection.getContentLength()>limit) throw new IOException("Size");
                    try(InputStream in=connection.getInputStream()) { return bytes(in,limit); }
                } finally { connection.disconnect(); }
            }
            throw new IOException("Too many redirects");
        }
        static boolean allowed(URL url,boolean initial) {
            return allowed(url,initial,FirmwareRelease.REPOSITORY);
        }
        static boolean allowed(URL url,boolean initial,String repository) {
            if(!"https".equals(url.getProtocol()) || url.getUserInfo()!=null || url.getRef()!=null || (url.getPort()!=-1 && url.getPort()!=443)) return false;
            String host=url.getHost();
            if("api.github.com".equals(host)) return url.getPath().equals("/repos/"+repository+"/releases");
            if("github.com".equals(host)) return url.getPath().startsWith("/"+repository+"/releases/download/");
            return !initial && ("release-assets.githubusercontent.com".equals(host) || "objects.githubusercontent.com".equals(host));
        }
    }
}
