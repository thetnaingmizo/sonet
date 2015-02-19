package com.piusvelte.sonet.social;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.piusvelte.sonet.BuildConfig;
import com.piusvelte.sonet.PhotoUploadService;
import com.piusvelte.sonet.R;
import com.piusvelte.sonet.Sonet;
import com.piusvelte.sonet.SonetCrypto;
import com.piusvelte.sonet.SonetHttpClient;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static com.piusvelte.sonet.Sonet.FACEBOOK_BASE_URL;
import static com.piusvelte.sonet.Sonet.FACEBOOK_COMMENTS;
import static com.piusvelte.sonet.Sonet.FACEBOOK_HOME;
import static com.piusvelte.sonet.Sonet.FACEBOOK_LIKES;
import static com.piusvelte.sonet.Sonet.FACEBOOK_PICTURE;
import static com.piusvelte.sonet.Sonet.FACEBOOK_POST;
import static com.piusvelte.sonet.Sonet.FACEBOOK_SEARCH;
import static com.piusvelte.sonet.Sonet.Saccess_token;
import static com.piusvelte.sonet.Sonet.Scomments;
import static com.piusvelte.sonet.Sonet.Screated_time;
import static com.piusvelte.sonet.Sonet.Sdata;
import static com.piusvelte.sonet.Sonet.Sfrom;
import static com.piusvelte.sonet.Sonet.Sid;
import static com.piusvelte.sonet.Sonet.Slink;
import static com.piusvelte.sonet.Sonet.Smessage;
import static com.piusvelte.sonet.Sonet.Sname;
import static com.piusvelte.sonet.Sonet.Sphoto;
import static com.piusvelte.sonet.Sonet.Spicture;
import static com.piusvelte.sonet.Sonet.Splace;
import static com.piusvelte.sonet.Sonet.Ssource;
import static com.piusvelte.sonet.Sonet.Sstory;
import static com.piusvelte.sonet.Sonet.Stags;
import static com.piusvelte.sonet.Sonet.Sto;
import static com.piusvelte.sonet.Sonet.Stype;
import static com.piusvelte.sonet.Sonet.Suser_likes;

/**
 * Created by bemmanuel on 2/15/15.
 */
public class FacebookClient extends SocialClient {

    public FacebookClient(Context context, String token, String secret, String accountEsid) {
        super(context, token, secret, accountEsid);
    }

    @Override
    String getFirstPhotoUrl(String[] parts) {
        // facebook wall post handling
        if (parts.length > 0 && Spicture.equals(parts[0])) {
            return parts[1];
        }

        return super.getFirstPhotoUrl(parts);
    }

    @Override
    String getPostFriendOverride(String friend) {
        // facebook wall post handling
        if (friend.indexOf(">") > 0) {
            return friend;
        }

        return super.getPostFriendOverride(friend);
    }

    @Override
    String getPostFriend(String friend) {
        if (friend.indexOf(">") > 0) {
            return friend.substring(0, friend.indexOf(">") - 1);
        }

        return super.getPostFriend(friend);
    }

    @Override
    public String getFeed(int appWidgetId,
                          String widget,
                          long account,
                          int service,
                          int status_count,
                          boolean time24hr,
                          boolean display_profile,
                          int notifications,
                          HttpClient httpClient) {
        String notificationMessage = null;
        String response;
        JSONArray statusesArray;
        ArrayList<String[]> links = new ArrayList<String[]>();
        final ArrayList<String> notificationSids = new ArrayList<String>();
        JSONObject statusObj;
        JSONObject friendObj;
        JSONArray commentsArray;
        JSONObject commentObj;
        Cursor currentNotifications;
        String sid;
        String esid;
        long notificationId;
        long updated;
        boolean cleared;
        String friend;
        // notifications first to populate notificationsSids
        if (notifications != 0) {
            currentNotifications = mContext.getContentResolver().query(Sonet.Notifications.getContentUri(mContext), new String[]{Sonet.Notifications._ID, Sonet.Notifications.SID, Sonet.Notifications.UPDATED, Sonet.Notifications.CLEARED, Sonet.Notifications.ESID}, Sonet.Notifications.ACCOUNT + "=?", new String[]{Long.toString(account)}, null);
            // loop over notifications
            if (currentNotifications.moveToFirst()) {
                while (!currentNotifications.isAfterLast()) {
                    notificationId = currentNotifications.getLong(0);
                    sid = SonetCrypto.getInstance(mContext).Decrypt(currentNotifications.getString(1));
                    updated = currentNotifications.getLong(2);
                    cleared = currentNotifications.getInt(3) == 1;

                    // store sids, to avoid duplicates when requesting the latest feed
                    if (!notificationSids.contains(sid)) {
                        notificationSids.add(sid);
                    }

                    // get comments for current notifications
                    if ((response = SonetHttpClient.httpResponse(httpClient, new HttpGet(String.format(FACEBOOK_COMMENTS, FACEBOOK_BASE_URL, sid, Saccess_token, mToken)))) != null) {
                        // check for a newer post, if it's the user's own, then set CLEARED=0
                        try {
                            commentsArray = new JSONObject(response).getJSONArray(Sdata);
                            final int i2 = commentsArray.length();

                            if (i2 > 0) {
                                for (int i = 0; i < i2; i++) {
                                    commentObj = commentsArray.getJSONObject(i);
                                    final long created_time = commentObj.getLong(Screated_time) * 1000;

                                    if (created_time > updated) {
                                        final JSONObject from = commentObj.getJSONObject(Sfrom);
                                        notificationMessage = updateNotificationMessage(notificationMessage,
                                                updateNotification(notificationId, created_time, mAccountEsid, from.getString(Sid), from.getString(Sname), cleared));
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(mTag, service + ":" + e.toString() + ":" + response);
                        }
                    }

                    currentNotifications.moveToNext();
                }
            }

            currentNotifications.close();
        }

        // parse the response
        if ((response = SonetHttpClient.httpResponse(httpClient, new HttpGet(String.format(FACEBOOK_HOME, FACEBOOK_BASE_URL, Saccess_token, mToken)))) != null) {
            try {
                statusesArray = new JSONObject(response).getJSONArray(Sdata);
                // if there are updates, clear the cache
                int d2 = statusesArray.length();
                if (d2 > 0) {
                    removeOldStatuses(widget, Long.toString(account));

                    for (int d = 0; d < d2; d++) {
                        links.clear();
                        statusObj = statusesArray.getJSONObject(d);

                        // only parse status types, not photo, video or link
                        if (statusObj.has(Stype) && statusObj.has(Sfrom) && statusObj.has(Sid)) {
                            friendObj = statusObj.getJSONObject("from");

                            if (friendObj.has(Sname) && friendObj.has(Sid)) {
                                friend = friendObj.getString(Sname);
                                esid = friendObj.getString(Sid);
                                sid = statusObj.getString(Sid);
                                StringBuilder message = new StringBuilder();

                                if (statusObj.has(Smessage)) {
                                    message.append(statusObj.getString(Smessage));
                                } else if (statusObj.has(Sstory)) {
                                    message.append(statusObj.getString(Sstory));
                                }

                                if (statusObj.has(Spicture)) {
                                    links.add(new String[]{Spicture, statusObj.getString(Spicture)});
                                }

                                if (statusObj.has(Slink)) {
                                    links.add(new String[]{statusObj.getString(Stype), statusObj.getString(Slink)});

                                    if (!statusObj.has(Spicture) || !statusObj.getString(Stype).equals(Sphoto)) {
                                        message.append("(");
                                        message.append(statusObj.getString(Stype));
                                        message.append(": ");
                                        message.append(Uri.parse(statusObj.getString(Slink)).getHost());
                                        message.append(")");
                                    }
                                }

                                if (statusObj.has(Ssource)) {
                                    links.add(new String[]{statusObj.getString(Stype), statusObj.getString(Ssource)});

                                    if (!statusObj.has(Spicture) || !statusObj.getString(Stype).equals(Sphoto)) {
                                        message.append("(");
                                        message.append(statusObj.getString(Stype));
                                        message.append(": ");
                                        message.append(Uri.parse(statusObj.getString(Ssource)).getHost());
                                        message.append(")");
                                    }
                                }

                                long date = statusObj.getLong(Screated_time) * 1000;
                                String notification = null;

                                if (statusObj.has(Sto)) {
                                    // handle wall messages from one friend to another
                                    JSONObject t = statusObj.getJSONObject(Sto);

                                    if (t.has(Sdata)) {
                                        JSONObject n = t.getJSONArray(Sdata).getJSONObject(0);

                                        if (n.has(Sname)) {
                                            friend += " > " + n.getString(Sname);

                                            if (!notificationSids.contains(sid) && n.has(Sid) && (n.getString(Sid).equals(mAccountEsid))) {
                                                notification = String.format(getString(R.string.friendcommented), friend);
                                            }
                                        }
                                    }
                                }
                                int commentCount = 0;

                                if (statusObj.has(Scomments)) {
                                    JSONObject jo = statusObj.getJSONObject(Scomments);

                                    if (jo.has(Sdata)) {
                                        commentsArray = jo.getJSONArray(Sdata);
                                        commentCount = commentsArray.length();

                                        if (!notificationSids.contains(sid) && (commentCount > 0)) {
                                            // default hasCommented to whether or not these comments are for the own user's status
                                            boolean hasCommented = notification != null || esid.equals(mAccountEsid);

                                            for (int c2 = 0; c2 < commentCount; c2++) {
                                                commentObj = commentsArray.getJSONObject(c2);
                                                // if new notification, or updated

                                                if (commentObj.has(Sfrom)) {
                                                    JSONObject c4 = commentObj.getJSONObject(Sfrom);

                                                    if (c4.getString(Sid).equals(mAccountEsid)) {
                                                        if (!hasCommented) {
                                                            // the user has commented on this thread, notify any updates
                                                            hasCommented = true;
                                                        }

                                                        // clear any notifications, as the user is already aware
                                                        if (notification != null) {
                                                            notification = null;
                                                        }
                                                    } else if (hasCommented) {
                                                        // don't notify about user's own comments
                                                        // send the parent comment sid
                                                        notification = String.format(getString(R.string.friendcommented), c4.getString(Sname));
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if ((notifications != 0) && (notification != null)) {
                                    // new notification
                                    addNotification(sid, esid, friend, message.toString(), statusObj.getLong(Screated_time) * 1000, account, notification);
                                    notificationMessage = updateNotificationMessage(notificationMessage, notification);
                                }

                                if (d < status_count) {
                                    addStatusItem(date,
                                            friend,
                                            display_profile ? String.format(FACEBOOK_PICTURE, esid) : null,
                                            String.format(getString(R.string.messageWithCommentCount), message.toString(), commentCount),
                                            service,
                                            time24hr,
                                            appWidgetId,
                                            account,
                                            sid,
                                            esid,
                                            links,
                                            httpClient);
                                }
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(mTag, service + ":" + e.toString() + ":" + response);
            }
        }

        return notificationMessage;
    }

    @Override
    public boolean createPost(String message, String placeId, String latitude, String longitude, String photoPath, String[] tags) {
        StringBuilder formattedTags = null;

        if (tags != null && tags.length > 0) {
            formattedTags = new StringBuilder();
            formattedTags.append("[");
            String tag_format;

            if (!TextUtils.isEmpty(photoPath)) {
                tag_format = "{\"tag_uid\":\"%s\",\"x\":0,\"y\":0}";
            } else {
                tag_format = "%s";
            }

            for (int i = 0, l = tags.length; i < l; i++) {
                if (i > 0) {
                    formattedTags.append(",");
                }

                formattedTags.append(String.format(tag_format, tags[i]));
            }

            formattedTags.append("]");
        }

        if (!TextUtils.isEmpty(photoPath)) {
            // upload photo
            // uploading a photo takes a long time, have the service handle it
            Intent i = Sonet.getPackageIntent(mContext, PhotoUploadService.class);
            i.setAction(Sonet.ACTION_UPLOAD);
            i.putExtra(Sonet.Accounts.TOKEN, mToken);
            i.putExtra(Sonet.Widgets.INSTANT_UPLOAD, photoPath);
            i.putExtra(Sonet.Statuses.MESSAGE, message);
            i.putExtra(Splace, placeId);

            if (tags != null) {
                i.putExtra(Stags, tags.toString());
            }

            mContext.startService(i);
            return true;
        } else {
            // regular post
            HttpPost httpPost = new HttpPost(String.format(FACEBOOK_POST, FACEBOOK_BASE_URL, Saccess_token, mToken));
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair(Smessage, message));

            if (placeId != null) {
                params.add(new BasicNameValuePair(Splace, placeId));
            }

            if (tags != null) {
                params.add(new BasicNameValuePair(Stags, tags.toString()));
            }

            try {
                httpPost.setEntity(new UrlEncodedFormEntity(params));
                return SonetHttpClient.httpResponse(mContext, httpPost) != null;
            } catch (UnsupportedEncodingException e) {
                if (BuildConfig.DEBUG) Log.e(mTag, e.toString());
            }
        }

        return false;
    }

    @Override
    public boolean isLikeable(String statusId) {
        return true;
    }

    @Override
    public boolean isLiked(String statusId, String accountId) {
        String response = SonetHttpClient.httpResponse(mContext, new HttpGet(String.format(FACEBOOK_LIKES, FACEBOOK_BASE_URL, statusId, Saccess_token, mToken)));

        if (!TextUtils.isEmpty(response)) {
            try {
                JSONArray likes = new JSONObject(response).getJSONArray(Sdata);

                for (int i = 0, i2 = likes.length(); i < i2; i++) {
                    JSONObject like = likes.getJSONObject(i);

                    if (like.getString(Sid).equals(accountId)) {
                        return true;
                    }
                }
            } catch (JSONException e) {
                if (BuildConfig.DEBUG) Log.e(mTag, e.toString());
            }
        }

        return false;
    }

    @Override
    public String getLikeText(boolean isLiked) {
        return getString(isLiked ? R.string.unlike : R.string.like);
    }

    @Override
    public boolean isCommentable(String statusId) {
        return true;
    }

    @Override
    public String getCommentPretext(String accountId) {
        return null;
    }

    @Nullable
    @Override
    public String getCommentsResponse(String statusId) {
        return SonetHttpClient.httpResponse(mContext, new HttpGet(String.format(FACEBOOK_COMMENTS, FACEBOOK_BASE_URL, statusId, Saccess_token, mToken)));
    }

    @Nullable
    @Override
    public JSONArray parseComments(@NonNull String response) throws JSONException {
        return new JSONObject(response).getJSONArray(Sdata);
    }

    @Nullable
    @Override
    public HashMap<String, String> parseComment(@NonNull String statusId, @NonNull JSONObject jsonComment, boolean time24hr) throws JSONException {
        HashMap<String, String> commentMap = new HashMap<>();
        commentMap.put(Sonet.Statuses.SID, jsonComment.getString(Sid));
        commentMap.put(Sonet.Entities.FRIEND, jsonComment.getJSONObject(Sfrom).getString(Sname));
        commentMap.put(Sonet.Statuses.MESSAGE, jsonComment.getString(Smessage));
        commentMap.put(Sonet.Statuses.CREATEDTEXT, Sonet.getCreatedText(jsonComment.getLong(Screated_time) * 1000, time24hr));
        commentMap.put(getString(R.string.like), getLikeText(jsonComment.has(Suser_likes) && jsonComment.getBoolean(Suser_likes)));
        return commentMap;
    }

    @Override
    public LinkedHashMap<String, String> getLocations(String latitude, String longitude) {
        String response = SonetHttpClient.httpResponse(mContext, new HttpGet(String.format(FACEBOOK_SEARCH, FACEBOOK_BASE_URL, latitude, longitude, Saccess_token, mToken)));

        if (response != null) {
            LinkedHashMap<String, String> locations = new LinkedHashMap<String, String>();

            try {
                JSONArray places = new JSONObject(response).getJSONArray(Sdata);

                for (int i = 0, i2 = places.length(); i < i2; i++) {
                    JSONObject place = places.getJSONObject(i);
                    locations.put(place.getString(Sid), place.getString(Sname));
                }
            } catch (JSONException e) {
                Log.e(mTag, e.toString());
            }

            return locations;
        }

        return null;
    }

    @Override
    String getApiKey() {
        return BuildConfig.FACEBOOK_ID;
    }

    @Override
    String getApiSecret() {
        return null;
    }
}
