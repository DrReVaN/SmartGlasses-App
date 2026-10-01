package com.test;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;

/** Grants the installer read access to one downloaded APK, never other app files. */
public final class AppApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException {
        List<String> path=uri.getPathSegments();
        if(!"content".equals(uri.getScheme()) || getContext()==null || !(getContext().getPackageName()+".appupdates").equals(uri.getAuthority())
            || uri.getQuery()!=null || uri.getFragment()!=null || path.size()!=2 || !"apk".equals(path.get(0)) || !path.get(1).matches("[0-9a-f]{64}\\.apk"))
            throw new FileNotFoundException("APK URI");
        File apk=new File(new File(getContext().getCacheDir(),"app-updates"),path.get(1));
        if(!apk.isFile()) throw new FileNotFoundException("APK not available");return apk;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode)) throw new FileNotFoundException("Read-only APK");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        try {
            File apk=file(uri);String[] columns=projection==null ? new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE} : projection;
            MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++) values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? "Smartglasses-App-Update.apk" : OpenableColumns.SIZE.equals(columns[i]) ? apk.length() : null;
            cursor.addRow(values);return cursor;
        } catch(FileNotFoundException e) { return null; }
    }
    @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException("Read-only APK"); }
    @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException("Read-only APK"); }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException("Read-only APK"); }
}
