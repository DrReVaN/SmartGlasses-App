package com.test;

import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.util.ReflectionHelpers;
import android.os.Looper;
import java.io.*;
import java.net.URL;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.CRC32;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35})
public class FirmwareUpdatesTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private static byte[] binary(String version) {
        byte[] image=new byte[512]; ByteBuffer b=ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(0x20007800).putInt(0x0801014d);
        if(!version.equals("0.2.0")) {
            FirmwareVersion v=FirmwareVersion.parse(version); b.position(0x140);
            b.putInt(0x31564753).putShort((short)v.major).putShort((short)v.minor).putShort((short)v.patch).put((byte)1).put((byte)0);
        }
        return image;
    }
    private static JSONObject manifest(String version,byte[] bytes) throws JSONException {
        CRC32 crc=new CRC32(); crc.update(bytes);
        return new JSONObject().put("format",1).put("target","STM32WB35CE").put("profile","application")
            .put("address","0x08010000").put("size",bytes.length).put("version",version).put("crc32",Long.toHexString(crc.getValue()))
            .put("sha256",OtaImage.hash(bytes)).put("protocol",1).put("min_bootloader","0.2.0");
    }
    private static JSONObject release(String version) throws JSONException {
        String prefix="Smartglasses-"+version+"-OTA", base="https://github.com/"+FirmwareRelease.REPOSITORY+"/releases/download/v"+version+"/";
        JSONArray assets=new JSONArray().put(new JSONObject().put("name",prefix+".bin").put("state","uploaded").put("size",512).put("browser_download_url",base+prefix+".bin"))
            .put(new JSONObject().put("name",prefix+".json").put("state","uploaded").put("size",500).put("browser_download_url",base+prefix+".json"));
        return new JSONObject().put("tag_name","v"+version).put("draft",false).put("prerelease",true).put("assets",assets);
    }
    private static class Fake implements FirmwareRepository.Transport {
        final Map<String,byte[]> replies=new HashMap<>(); int gets; boolean offline;
        @Override public byte[] get(String url,int limit) throws IOException {
            gets++; if(offline || !replies.containsKey(url)) throw new IOException("Offline");
            byte[] bytes=replies.get(url); if(bytes.length>limit) throw new IOException("Limit"); return bytes.clone();
        }
        void packageFor(FirmwareRelease r,String version) throws JSONException {
            byte[] data=binary(version); replies.put(r.binaryUrl,data);
            replies.put(r.manifestUrl,manifest(version,data).toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    @Test public void numericVersionsDoNotSortLexically() {
        assertTrue(FirmwareVersion.parse("0.10.0").compareTo(FirmwareVersion.parse("0.9.12"))>0);
        assertTrue(FirmwareVersion.parse("1.0.0").compareTo(FirmwareVersion.parse("0.65535.65535"))>0);
        for(String bad:new String[]{"1.0","v1.0.0","01.0.0","1.-1.0","1.0.0-beta","65536.0.0"})
            assertThrows(IllegalArgumentException.class,() -> FirmwareVersion.parse(bad));
    }
    @Test public void releaseTagAndFilenameAndRepositoryMustAgree() throws Exception {
        JSONArray source=new JSONArray().put(release("0.9.12")).put(release("0.10.0")).put(release("0.3.0"));
        assertEquals("0.10.0",FirmwareRelease.parse(source).get(0).version.toString());
        JSONObject evil=release("0.3.0"); evil.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://example.com/firmware.bin");
        assertTrue(FirmwareRelease.parse(new JSONArray().put(evil)).isEmpty());
        JSONObject draft=release("0.3.0").put("draft",true); assertTrue(FirmwareRelease.parse(new JSONArray().put(draft)).isEmpty());
    }
    @Test public void partialReleaseNeverAppearsAndOlderReleaseCanBeSelected() throws Exception {
        JSONObject incomplete=release("0.3.0"); incomplete.getJSONArray("assets").remove(1);
        assertTrue(FirmwareRelease.parse(new JSONArray().put(incomplete)).isEmpty());
        List<FirmwareRelease> list=FirmwareRelease.parse(new JSONArray().put(release("0.2.0")).put(release("0.3.0")));
        assertEquals("0.2.0",list.get(1).version.toString());
        assertNull(FirmwareRelease.newest(list,FirmwareVersion.parse("0.3.0")));
        assertEquals("0.3.0",FirmwareRelease.newest(list,null).version.toString());
    }
    @Test public void manifestCannotRenameAnEmbeddedFirmwareVersion() throws Exception {
        byte[] data=binary("0.3.0"); assertEquals("0.3.0",OtaImage.fromManifest(data,manifest("0.3.0",data)).version);
        assertThrows(IllegalArgumentException.class,() -> OtaImage.fromManifest(data,manifest("0.3.1",data)));
        assertThrows(IllegalArgumentException.class,() -> OtaImage.fromManifest(data,manifest("0.2.0",data)));
        assertThrows(IllegalArgumentException.class,() -> OtaImage.fromManifest(data,manifest("0.3.0",data).put("protocol",2)));
        assertThrows(IllegalArgumentException.class,() -> OtaImage.fromManifest(data,manifest("0.3.0",data).put("min_bootloader","0.3.0")));
    }
    @Test public void startupConfirmationChecksVersionSizeAndCRC() throws Exception {
        byte[] image=binary("0.3.0"); OtaImage ota=OtaImage.fromManifest(image,manifest("0.3.0",image));
        byte[] identity=new byte[20]; ByteBuffer b=ByteBuffer.wrap(identity).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{0,2,0,0}).putShort((short)0).putShort((short)3).putShort((short)0).put((byte)1).put((byte)0).putInt(ota.size()).putInt((int)ota.crc32);
        assertTrue(GlassesProfile.supportedVersion(identity)); assertTrue(FirmwareIdentity.read(identity).confirms(ota));
        identity[8]=1; assertFalse(FirmwareIdentity.read(identity).confirms(ota)); identity[8]=0;
        identity[16]^=1; assertFalse(FirmwareIdentity.read(identity).confirms(ota)); identity[16]^=1;
        identity[12]^=1; assertFalse(FirmwareIdentity.read(identity).confirms(ota));
        assertFalse(FirmwareIdentity.read(new byte[]{0,2,0,0}).confirms(ota));
        assertFalse(GlassesProfile.supportedVersion(new byte[]{0,2,0,2}));
    }
    @Test public void verifiedPackagesRemainAvailableOfflineAfterRestart() throws Exception {
        Fake network=new Fake(); JSONArray raw=new JSONArray().put(release("0.2.0")).put(release("0.3.0"));
        network.replies.put(FirmwareRepository.API+"?per_page=100&page=1",raw.toString().getBytes(StandardCharsets.UTF_8));
        FirmwareRepository repo=new FirmwareRepository(temp.getRoot(),network); List<FirmwareRelease> catalog=repo.refresh();
        for(FirmwareRelease r:catalog) { network.packageFor(r,r.version.toString()); assertEquals(r.version.toString(),repo.load(r).version); }
        network.offline=true; int before=network.gets;
        FirmwareRepository restarted=new FirmwareRepository(temp.getRoot(),network);
        for(FirmwareRelease r:restarted.cachedCatalog()) assertEquals(r.version.toString(),restarted.load(r).version);
        assertEquals(before,network.gets);
        assertThrows(IOException.class,restarted::refresh); assertEquals(2,restarted.cachedCatalog().size());
    }
    @Test public void changedOrWrongDownloadIsRejectedWithoutCaching() throws Exception {
        Fake network=new Fake(); FirmwareRelease r=FirmwareRelease.parse(new JSONArray().put(release("0.3.0"))).get(0);
        network.packageFor(r,"0.3.1"); FirmwareRepository repo=new FirmwareRepository(temp.getRoot(),network);
        assertThrows(IllegalArgumentException.class,() -> repo.load(r)); assertFalse(new File(temp.getRoot(),"0.3.0.bin").exists());
        network.packageFor(r,"0.3.0"); network.replies.get(r.binaryUrl)[100]^=1;
        assertThrows(IllegalArgumentException.class,() -> repo.load(r)); assertFalse(new File(temp.getRoot(),"0.3.0.json").exists());
    }
    @Test public void redirectsCannotLeaveGitHubOrUseCleartext() throws Exception {
        assertTrue(FirmwareRepository.HttpsTransport.allowed(new URL(FirmwareRepository.API),true));
        assertFalse(FirmwareRepository.HttpsTransport.allowed(new URL("http://github.com/a"),true));
        assertFalse(FirmwareRepository.HttpsTransport.allowed(new URL("https://user@github.com/a"),true));
        assertFalse(FirmwareRepository.HttpsTransport.allowed(new URL("https://github.com.evil.example/a"),false));
        assertFalse(FirmwareRepository.HttpsTransport.allowed(new URL("https://raw.githubusercontent.com/a"),false));
        assertTrue(FirmwareRepository.HttpsTransport.allowed(new URL("https://release-assets.githubusercontent.com/a"),false));
    }
    private static void finishFiles(BluetoothLeService service) throws Exception {
        ExecutorService executor=ReflectionHelpers.getField(service,"files"); executor.submit(() -> {}).get(5,TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void checkingAndDownloadingNeverStartAnOTAAndLaterIsRemembered() throws Exception {
        ServiceController<BluetoothLeService> control=Robolectric.buildService(BluetoothLeService.class).create();
        BluetoothLeService service=control.get();
        try {
            finishFiles(service); Fake network=new Fake();
            network.replies.put(FirmwareRepository.API+"?per_page=100&page=1",new JSONArray().put(release("0.3.0")).toString().getBytes(StandardCharsets.UTF_8));
            ReflectionHelpers.setField(service,"repository",new FirmwareRepository(temp.getRoot(),network));
            ReflectionHelpers.setField(service,"state",BluetoothLeService.State.READY);
            service.getSharedPreferences("glasses",0).edit().putLong("firmware_check",0).apply();
            service.checkFirmware(false); finishFiles(service); assertNotNull(service.offeredRelease()); assertFalse(service.updating()); assertNull(service.image());
            int count=network.gets; service.checkFirmware(false); finishFiles(service); assertEquals(count,network.gets);
            FirmwareRelease latest=service.offeredRelease(); service.deferFirmware(); assertNull(service.offeredRelease());
            network.packageFor(latest,"0.3.0"); service.loadRelease(latest); finishFiles(service);
            assertNotNull(service.image()); assertFalse(service.updating());
            service.checkFirmware(true); finishFiles(service); assertNotNull(service.offeredRelease()); assertFalse(service.updating());
        } finally { control.destroy(); }
    }
}
