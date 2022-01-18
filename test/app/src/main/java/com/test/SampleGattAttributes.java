package com.test;

import java.util.HashMap;
import java.util.UUID;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "Heart Rate Service");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        attributes.put("00001128-0000-1000-8000-00805f9b34fb", "Common_ISDN_Access");
        attributes.put("00001201-0000-1000-8000-00805f9b34fb", "GenericNetworking");
        attributes.put("00001200-0000-1000-8000-00805f9b34fb", "PnPInformation");
        attributes.put("00001303-0000-1000-8000-00805f9b34fb", "VideoSource");
        attributes.put("00001122-0000-1000-8000-00805f9b34fb", "BasicPrinting");
        attributes.put("00001123-0000-1000-8000-00805f9b34fb", "PrintingStatus");
        attributes.put("00001120-0000-1000-8000-00805f9b34fb", "DirectPrintingReferenceObjectsService");
        attributes.put("00001121-0000-1000-8000-00805f9b34fb", "ReflectedUI");
        attributes.put("00001108-0000-1000-8000-00805f9b34fb", "Headset");
        attributes.put("00001109-0000-1000-8000-00805f9b34fb", "CordlessTelephony");
        attributes.put("00001124-0000-1000-8000-00805f9b34fb", "HumanInterfaceDeviceService");
        attributes.put("00001125-0000-1000-8000-00805f9b34fb", "HardcopyCableReplacement");
        attributes.put("00001104-0000-1000-8000-00805f9b34fb", "IrMCSync");
        attributes.put("00001105-0000-1000-8000-00805f9b34fb", "OBEXObjectPush");
        attributes.put("00001106-0000-1000-8000-00805f9b34fb", "OBEXFileTransfer");
        attributes.put("00001107-0000-1000-8000-00805f9b34fb", "IrMCSyncCommand");
        attributes.put("00001101-0000-1000-8000-00805f9b34fb", "SerialPort");
        attributes.put("00001102-0000-1000-8000-00805f9b34fb", "LANAccessUsingPPP");
        attributes.put("00001103-0000-1000-8000-00805f9b34fb", "DialupNetworking");
        attributes.put("00001129-0000-1000-8000-00805f9b34fb", "VideoConferencingGW");
        attributes.put("0000111c-0000-1000-8000-00805f9b34fb", "ImagingAutomaticArchive");
        attributes.put("0000111b-0000-1000-8000-00805f9b34fb", "ImagingResponder");
        attributes.put("0000111a-0000-1000-8000-00805f9b34fb", "Imaging");
        attributes.put("00001305-0000-1000-8000-00805f9b34fb", "VideoDistribution");
        attributes.put("00001302-0000-1000-8000-00805f9b34fb", "ESDP_UPNP_L2CAP");
        attributes.put("0000111f-0000-1000-8000-00805f9b34fb", "HandsfreeAudioGateway");
        attributes.put("0000111e-0000-1000-8000-00805f9b34fb", "Handsfree");
        attributes.put("0000111d-0000-1000-8000-00805f9b34fb", "ImagingReferencedObjects");
        attributes.put("0000112f-0000-1000-8000-00805f9b34fb", "Phonebook Access - PSE");
        attributes.put("00001301-0000-1000-8000-00805f9b34fb", "ESDP_UPNP_IP_LAP");
        attributes.put("00001203-0000-1000-8000-00805f9b34fb", "GenericAudio");
        attributes.put("00001202-0000-1000-8000-00805f9b34fb", "GenericFileTransfer");
        attributes.put("00001300-0000-1000-8000-00805f9b34fb", "ESDP_UPNP_IP_PAN");
        attributes.put("00001130-0000-1000-8000-00805f9b34fb", "Phonebook Access");
        attributes.put("00001119-0000-1000-8000-00805f9b34fb", "ReferencePrinting");
        attributes.put("00001118-0000-1000-8000-00805f9b34fb", "DirectPrinting");
        attributes.put("00001206-0000-1000-8000-00805f9b34fb", "UPNP_IP_Service");
        attributes.put("00001113-0000-1000-8000-00805f9b34fb", "WAP");
        attributes.put("00001112-0000-1000-8000-00805f9b34fb", "HeadsetAudioGateway");
        attributes.put("00001111-0000-1000-8000-00805f9b34fb", "Fax");
        attributes.put("00001110-0000-1000-8000-00805f9b34fb", "Intercom");
        attributes.put("00001117-0000-1000-8000-00805f9b34fb", "GN");
        attributes.put("00001116-0000-1000-8000-00805f9b34fb", "NAP");
        attributes.put("00001115-0000-1000-8000-00805f9b34fb", "PANU");
        attributes.put("00001114-0000-1000-8000-00805f9b34fb", "WAP_CLIENT");
        attributes.put("0000112b-0000-1000-8000-00805f9b34fb", "UDI_TA");
        attributes.put("0000112c-0000-1000-8000-00805f9b34fb", "Audio/Video");
        attributes.put("0000112a-0000-1000-8000-00805f9b34fb", "UDI_MT");
        attributes.put("00001001-0000-1000-8000-00805f9b34fb", "BrowseGroupDescriptorServiceClassID");
        attributes.put("00001000-0000-1000-8000-00805f9b34fb", "ServiceDiscoveryServerServiceClassID");
        attributes.put("0000112d-0000-1000-8000-00805f9b34fb", "SIM_Access");
        attributes.put("00001002-0000-1000-8000-00805f9b34fb", "PublicBrowseGroup");
        attributes.put("0000110d-0000-1000-8000-00805f9b34fb", "AdvancedAudioDistribution");
        attributes.put("0000110e-0000-1000-8000-00805f9b34fb", "A/V_RemoteControl");
        attributes.put("0000110f-0000-1000-8000-00805f9b34fb", "VideoConferencing");
        attributes.put("0000112e-0000-1000-8000-00805f9b34fb", "Phonebook Access - PCE");
        attributes.put("0000110a-0000-1000-8000-00805f9b34fb", "AudioSource");
        attributes.put("0000110b-0000-1000-8000-00805f9b34fb", "AudioSink");
        attributes.put("0000110c-0000-1000-8000-00805f9b34fb", "A/V_RemoteControlTarget");
        attributes.put("00001205-0000-1000-8000-00805f9b34fb", "UPNP_Service");
        attributes.put("00001204-0000-1000-8000-00805f9b34fb", "GenericTelephony");
        attributes.put("00001126-0000-1000-8000-00805f9b34fb", "HCR_Print");
        attributes.put("00001304-0000-1000-8000-00805f9b34fb", "VideoSink");
        attributes.put("00001127-0000-1000-8000-00805f9b34fb", "HCR_Scan");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
