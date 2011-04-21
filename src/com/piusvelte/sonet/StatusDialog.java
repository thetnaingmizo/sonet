/*
 * Sonet - Android Social Networking Widget
 * Copyright (C) 2009 Bryan Emmanuel
 * 
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  Bryan Emmanuel piusvelte@gmail.com
 */
package com.piusvelte.sonet;

import static com.piusvelte.sonet.Sonet.ACTION_REFRESH;

import java.io.IOException;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.piusvelte.sonet.Sonet.TOKEN;
import static com.piusvelte.sonet.Sonet.TWITTER;
import static com.piusvelte.sonet.Sonet.FACEBOOK;
import static com.piusvelte.sonet.Sonet.BUZZ;
import static com.piusvelte.sonet.Sonet.LINKEDIN;
import static com.piusvelte.sonet.Sonet.FACEBOOK_LIKES;
import static com.piusvelte.sonet.Sonet.FACEBOOK_BASE_URL;
import static com.piusvelte.sonet.Sonet.BUZZ_BASE_URL;
import static com.piusvelte.sonet.Sonet.BUZZ_LIKE;
import static com.piusvelte.sonet.Sonet.BUZZ_ACTIVITY;

import static com.piusvelte.sonet.SonetTokens.TWITTER_KEY;
import static com.piusvelte.sonet.SonetTokens.TWITTER_SECRET;
import static com.piusvelte.sonet.Sonet.TWITTER_BASE_URL;
import static com.piusvelte.sonet.Sonet.TWITTER_RETWEET;
import static com.piusvelte.sonet.SonetTokens.BUZZ_KEY;
import static com.piusvelte.sonet.SonetTokens.BUZZ_SECRET;
import static com.piusvelte.sonet.SonetTokens.BUZZ_API_KEY;

import com.piusvelte.sonet.Sonet.Accounts;
import com.piusvelte.sonet.Sonet.Statuses_styles;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class StatusDialog extends Activity implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
	private static final String TAG = "StatusDialog";
	private int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
	private int mService = 0;
	private long mAccount = Sonet.INVALID_ACCOUNT_ID;
	private String mSid;
	private String mEsid;
	private Uri mData;
	private boolean mLike = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		if (intent != null) {
			mData = intent.getData();
			Cursor c = this.getContentResolver().query(Statuses_styles.CONTENT_URI, new String[]{Statuses_styles._ID, Statuses_styles.WIDGET, Statuses_styles.SERVICE, Statuses_styles.ACCOUNT, Statuses_styles.SID, Statuses_styles.ESID}, Statuses_styles._ID + "=?", new String[] {mData.getLastPathSegment()}, null);
			if (c.moveToFirst()) {
				mAppWidgetId = c.getInt(c.getColumnIndex(Statuses_styles.WIDGET));
				mService = c.getInt(c.getColumnIndex(Statuses_styles.SERVICE));
				mAccount = c.getLong(c.getColumnIndex(Statuses_styles.ACCOUNT));
				mSid = c.getString(c.getColumnIndex(Statuses_styles.SID));
				mEsid = c.getString(c.getColumnIndex(Statuses_styles.ESID));
			}
			c.close();
		}
		String[] items;
		Cursor account;
		switch (mService) {
		case TWITTER:
			items = new String[]{getString(R.string.retweet), getString(R.string.reply), getString(R.string.tweet), getString(R.string.settings), getString(R.string.button_refresh)};
			break;
		case FACEBOOK:
			account = this.getContentResolver().query(Accounts.CONTENT_URI, new String[]{Accounts._ID, Accounts.SID, Accounts.TOKEN}, Accounts._ID + "=?", new String[]{Long.toString(mAccount)}, null);
			if (account.moveToFirst()) {
				String response = Sonet.httpGet(String.format(FACEBOOK_LIKES, FACEBOOK_BASE_URL, mEsid, TOKEN, account.getString(account.getColumnIndex(Accounts.TOKEN))));
				if (response != null) {
					try {
						JSONArray likes = new JSONObject(response).getJSONArray("data");
						for (int i = 0; i < likes.length(); i++) {
							JSONObject like = likes.getJSONObject(i);
							if (like.getString("id") == account.getString(account.getColumnIndex(Accounts.SID))) {
								mLike = false;
								break;
							}
						}
					} catch (JSONException e) {
						Log.e(TAG,e.toString());
					}
				}
			}
			account.close();
			items = new String[]{getString(mLike ? R.string.like : R.string.unlike), getString(R.string.comment), getString(R.string.button_post), getString(R.string.settings), getString(R.string.button_refresh)};
			break;
		case BUZZ:
			//TODO: like
			account = this.getContentResolver().query(Accounts.CONTENT_URI, new String[]{Accounts._ID, Accounts.SID, Accounts.TOKEN, Accounts.SECRET}, Accounts._ID + "=?", new String[]{Long.toString(mAccount)}, null);
			if (account.moveToFirst()) {
				SonetOAuth sonetOAuth = new SonetOAuth(BUZZ_KEY, BUZZ_SECRET, account.getString(account.getColumnIndex(Accounts.TOKEN)), account.getString(account.getColumnIndex(Accounts.SECRET)));				
				try {
					String response = sonetOAuth.httpGet(String.format(BUZZ_ACTIVITY, BUZZ_BASE_URL, mEsid, BUZZ_API_KEY));
					if (response != null) {
						Log.v(TAG,"response:"+response);
						JSONArray likes = new JSONObject(response).getJSONArray("data");
						for (int i = 0; i < likes.length(); i++) {
							JSONObject like = likes.getJSONObject(i);
							if (like.getString("id") == account.getString(account.getColumnIndex(Accounts.SID))) {
								mLike = false;
								break;
							}
						}
					}
				} catch (ClientProtocolException e) {
					Log.e(TAG, e.toString());
				} catch (OAuthMessageSignerException e) {
					Log.e(TAG, e.toString());
				} catch (OAuthExpectationFailedException e) {
					Log.e(TAG, e.toString());
				} catch (OAuthCommunicationException e) {
					Log.e(TAG, e.toString());
				} catch (IOException e) {
					Log.e(TAG, e.toString());
				} catch (JSONException e) {
					Log.e(TAG, e.toString());
				}
			}
			account.close();
			items = new String[]{getString(mLike ? R.string.like : R.string.unlike), getString(R.string.comment), getString(R.string.button_post), getString(R.string.settings), getString(R.string.button_refresh)};
			break;
		case LINKEDIN:
			//TODO: like
			items = new String[]{getString(mLike ? R.string.like : R.string.unlike), getString(R.string.comment), getString(R.string.button_post), getString(R.string.settings), getString(R.string.button_refresh)};
			break;
		default:
			items = new String[]{getString(R.string.settings), getString(R.string.button_refresh)};
			break;
		}
		(new AlertDialog.Builder(this))
		.setItems(items, this)
		.setCancelable(true)
		.setOnCancelListener(this)
		.show();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
		case 0:
			switch (mService) {
			case TWITTER:
				if (mSid != null) {
					Cursor c = this.getContentResolver().query(Accounts.CONTENT_URI, new String[]{Accounts._ID, Accounts.TOKEN, Accounts.SECRET}, Accounts._ID + "=?", new String[]{Long.toString(mAccount)}, null);
					if (c.moveToFirst()) {
						SonetOAuth sonetOAuth = new SonetOAuth(TWITTER_KEY, TWITTER_SECRET, c.getString(c.getColumnIndex(Accounts.TOKEN)), c.getString(c.getColumnIndex(Accounts.SECRET)));
						try {
							String response = sonetOAuth.httpGet(String.format(TWITTER_RETWEET, TWITTER_BASE_URL, mSid));
							//TODO: check response
							Log.v(TAG,"retweet:"+response);
						} catch (ClientProtocolException e) {
							Log.e(TAG,e.toString());
						} catch (OAuthMessageSignerException e) {
							Log.e(TAG,e.toString());
						} catch (OAuthExpectationFailedException e) {
							Log.e(TAG,e.toString());
						} catch (OAuthCommunicationException e) {
							Log.e(TAG,e.toString());
						} catch (IOException e) {
							Log.e(TAG,e.toString());
						}
					}
					c.close();
				}
				break;
			case FACEBOOK:
				if (mSid != null) {
					Cursor c = this.getContentResolver().query(Accounts.CONTENT_URI, new String[]{Accounts._ID, Accounts.TOKEN}, Accounts._ID + "=?", new String[]{Long.toString(mAccount)}, null);
					if (c.moveToFirst()) {
						String response = mLike ? Sonet.httpPost(String.format(FACEBOOK_LIKES, FACEBOOK_BASE_URL, mSid, TOKEN, c.getString(c.getColumnIndex(Accounts.TOKEN)))) : Sonet.httpDelete(String.format(FACEBOOK_LIKES, mSid, TOKEN, c.getString(c.getColumnIndex(Accounts.TOKEN))));
						//TODO: check response
						Log.v(TAG,"like:"+response);
					}
					c.close();
				}
				break;
			case BUZZ:
				//TODO:like
				break;
			case LINKEDIN:
				//TODO:like
				break;
			default:
				startActivity(new Intent(this, SonetCreatePost.class).setData(mData));
				break;
			}
			break;
		case 1:
			switch (mService) {
			case TWITTER:
				startActivity(new Intent(this, SonetCreatePost.class).setData(mData));
				break;
			case FACEBOOK:
				startActivity(new Intent(this, SonetCreatePost.class).setData(mData));
				break;
			case BUZZ:
				startActivity(new Intent(this, SonetCreatePost.class).setData(mData));
				break;
			default:
				startActivity(new Intent(this, SonetCreatePost.class).setData(Uri.withAppendedPath(Accounts.CONTENT_URI, Long.toString(mAccount))));
				break;
			}
			break;
		case 2:
			switch (mService) {
			case TWITTER:
				startActivity(new Intent(this, SonetCreatePost.class).setData(Uri.withAppendedPath(Accounts.CONTENT_URI, Long.toString(mAccount))));
				break;
			case FACEBOOK:
				startActivity(new Intent(this, SonetCreatePost.class).setData(Uri.withAppendedPath(Accounts.CONTENT_URI, Long.toString(mAccount))));
				break;
			case BUZZ:
				startActivity(new Intent(this, SonetCreatePost.class).setData(Uri.withAppendedPath(Accounts.CONTENT_URI, Long.toString(mAccount))));
				break;
			case LINKEDIN:
				startActivity(new Intent(this, SonetCreatePost.class).setData(Uri.withAppendedPath(Accounts.CONTENT_URI, Long.toString(mAccount))));
				break;
			default:
				startActivity(new Intent(this, ManageAccounts.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId));
				break;
			}
			break;
		case 3:
			switch (mService) {
			case TWITTER:
				startActivity(new Intent(this, ManageAccounts.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId));
				break;
			case FACEBOOK:
				startActivity(new Intent(this, ManageAccounts.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId));
				break;
			case BUZZ:
				startActivity(new Intent(this, ManageAccounts.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId));
				break;
			case LINKEDIN:
				startActivity(new Intent(this, ManageAccounts.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId));
				break;
			default:
				startService(new Intent(this, SonetService.class).setAction(ACTION_REFRESH).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{mAppWidgetId}));
				break;
			} 
			break;
		case 4:
			startService(new Intent(this, SonetService.class).setAction(ACTION_REFRESH).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{mAppWidgetId}));
			break;
		}
		dialog.cancel();
	}

	@Override
	public void onCancel(DialogInterface dialog) {
		finish();
	}	

}
