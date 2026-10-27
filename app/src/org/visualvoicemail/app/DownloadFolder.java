package org.visualvoicemail.app;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/** User-selected saved-copy destination. Playback cache and carrier data are untouched. */
final class DownloadFolder {
    private static final String KEY="download_folder";
    private static final int ACCESS=Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    static Uri selected(Context context) {
        String saved=context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString(KEY,"");
        if(saved.isEmpty()) return null;
        try { Uri uri=Uri.parse(saved); return DocumentsContract.isTreeUri(uri)?uri:null; }
        catch(Exception ignored) { return null; }
    }
    static boolean accessible(Context context,Uri tree) {
        if(tree==null) return false;
        for(UriPermission grant:context.getContentResolver().getPersistedUriPermissions())
            if(grant.getUri().equals(tree)&&grant.isReadPermission()&&grant.isWritePermission()) return true;
        return false;
    }
    static String describe(Context context) {
        Uri tree=selected(context);
        if(tree==null) return "Choose a folder when first saving";
        String label;
        try {
            String id=DocumentsContract.getTreeDocumentId(tree);
            if("com.android.externalstorage.documents".equals(tree.getAuthority())) {
                int colon=id.indexOf(':'); String volume=colon<0?id:id.substring(0,colon);
                String path=colon<0?"":id.substring(colon+1);
                label=("primary".equals(volume)?"Internal storage":"Storage "+volume)+(path.isEmpty()?"":" / "+path);
            } else {
                android.content.pm.ProviderInfo provider=context.getPackageManager().resolveContentProvider(tree.getAuthority(),0);
                String name=provider==null?"Selected provider":provider.loadLabel(context.getPackageManager()).toString();
                label=name+" / "+id;
            }
        } catch(Exception ignored) { label="Selected folder"; }
        return accessible(context,tree)?label:label+" · Access unavailable; choose again";
    }
    static Intent picker(Context context) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(ACCESS
            |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        Uri selected=selected(context);
        if(selected!=null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,selected);
        return intent;
    }
    static void remember(Context context,Intent result) throws IOException {
        Uri tree=result==null?null:result.getData();
        if(tree==null||!"content".equals(tree.getScheme())||tree.getAuthority()==null||!DocumentsContract.isTreeUri(tree)||(result.getFlags()&ACCESS)!=ACCESS)
            throw new IOException("Choose a folder that allows saving files.");
        Uri previous=selected(context);
        try { context.getContentResolver().takePersistableUriPermission(tree,ACCESS); }
        catch(SecurityException denied) { throw new IOException("Folder access couldn't be retained. Choose another folder."); }
        if(!accessible(context,tree)) throw new IOException("Folder access couldn't be retained. Choose again.");
        boolean saved=context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().putString(KEY,tree.toString()).commit();
        if(!saved) {
            if(!tree.equals(previous)) release(context,tree);
            throw new IOException("The download location couldn't be saved. Try again.");
        }
        if(previous!=null&&!previous.equals(tree)) release(context,previous);
    }
    static boolean forget(Context context) {
        Uri previous=selected(context);
        if(!context.getSharedPreferences("settings",Context.MODE_PRIVATE).edit().remove(KEY).commit()) return false;
        if(previous!=null) release(context,previous);
        return true;
    }
    private static void release(Context context,Uri uri) {
        try { context.getContentResolver().releasePersistableUriPermission(uri,ACCESS); } catch(SecurityException ignored) { }
    }
    static Uri save(Context context,Uri tree,File source,String date) throws Exception {
        if(!accessible(context,tree)) throw new IOException("Folder access is unavailable. Choose Download location again in Settings.");
        ExportAudioFormat format=ExportAudioFormat.detect(source);
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        Uri created=DocumentsContract.createDocument(context.getContentResolver(),parent,format.mime,format.filename(date));
        if(created==null) throw new IOException("The selected folder couldn't create an audio file.");
        try {
            try(FileInputStream input=new FileInputStream(source); OutputStream output=context.getContentResolver().openOutputStream(created,"w")) {
                if(output==null) throw new IOException("The new audio file couldn't be opened.");
                byte[] buffer=new byte[8192]; int length; long copied=0;
                while((length=input.read(buffer))!=-1) {
                    copied+=length;
                    if(copied>25L*1024*1024) throw new IOException("This recording exceeds the saved-copy size limit.");
                    output.write(buffer,0,length);
                }
                if(copied==0) throw new IOException("The recording is empty.");
                output.flush();
            }
            return created;
        } catch(Exception failure) {
            // Only remove the new document created by this operation, never an existing file.
            try { DocumentsContract.deleteDocument(context.getContentResolver(),created); } catch(Exception ignored) { }
            throw failure;
        }
    }
}
