package com.test;
import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
/** Normalize notification styles without assuming String extras. */
public final class NotificationText {
    private NotificationText() {}
    public static String read(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return null;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body = null;
        if (Build.VERSION.SDK_INT >= 30) {
            java.util.List<Notification.MessagingStyle.Message> messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(extras.getParcelableArray(Notification.EXTRA_MESSAGES));
            if (!messages.isEmpty()) {
                Notification.MessagingStyle.Message latest = messages.get(messages.size()-1);
                body = latest.getText();
                CharSequence sender = latest.getSenderPerson() == null ? null : latest.getSenderPerson().getName();
                if (!TextUtils.isEmpty(sender)) title = sender;
            }
        } else if (Build.VERSION.SDK_INT >= 24) {
            // AOSP's MessagingStyle bundles include text and the legacy sender name.
            // The framework bundle decoder only became public in API 30.
            android.os.Parcelable[] messages=extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages!=null) for (int i=messages.length-1;i>=0;i--) {
                if (!(messages[i] instanceof Bundle)) continue;
                Bundle message=(Bundle)messages[i];
                CharSequence text=message.getCharSequence("text");
                if (TextUtils.isEmpty(text)) continue;
                body=text;
                CharSequence sender=message.getCharSequence("sender");
                if (!TextUtils.isEmpty(sender)) title=sender;
                break;
            }
        }
        if (TextUtils.isEmpty(body)) body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (TextUtils.isEmpty(body)) body = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(body)) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null && lines.length > 0) body = lines[lines.length-1];
        }
        if (TextUtils.isEmpty(body)) return null;
        return TextUtils.isEmpty(title) ? body.toString() : title + ": " + body;
    }
}
