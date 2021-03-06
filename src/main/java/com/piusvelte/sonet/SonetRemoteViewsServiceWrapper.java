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

import android.content.Context;
import android.content.Intent;

public class SonetRemoteViewsServiceWrapper {
	
	private static boolean sNativeScrolling = false;

	static {
		try {
			Class.forName("android.widget.RemoteViewsService");
			sNativeScrolling = true;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public static Intent getRemoteAdapterIntent(Context context) {
		if (sNativeScrolling) {
			return Sonet.getPackageIntent(context, SonetRemoteViewsService.class);
		} else {
			return null;
		}
	}
}
