/*
 * SiYuan - 源于思考，饮水思源
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.blankj.utilcode.util.BarUtils;
import com.blankj.utilcode.util.StringUtils;
import com.zackratos.ultimatebarx.ultimatebarx.java.UltimateBarX;

/**
 * JavaScript 接口.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.1.2.1, Feb 9, 2023
 * @since 1.0.0
 */
public final class JSAndroid {
    private MainActivity activity;

    public JSAndroid(final MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getBlockURL() {
        final String blockURL = activity.getIntent().getStringExtra("blockURL");
        return blockURL;
    }

    @JavascriptInterface
    public String readClipboard() {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clipData = clipboard.getPrimaryClip();
        if (null == clipData) {
            return "";
        }

        final ClipData.Item item = clipData.getItemAt(0);
        if (null != item.getUri()) {
            final Uri uri = item.getUri();
            final String url = uri.toString();
            if (url.startsWith("http://127.0.0.1:6806/assets/")) {
                final int idx = url.indexOf("/assets/");
                final String asset = url.substring(idx);
                String name = asset.substring(asset.lastIndexOf("/") + 1);
                final int suffixIdx = name.lastIndexOf(".");
                if (0 < suffixIdx) {
                    name = name.substring(0, suffixIdx);
                }
                if (23 < StringUtils.length(name)) {
                    name = name.substring(0, name.length() - 23);
                }
                return "![" + name + "](" + asset + ")";
            }
        }
        return item.getText().toString();
    }

    @JavascriptInterface
    public void writeImageClipboard(final String uri) {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newUri(activity.getContentResolver(), "Copied img from SiYuan", Uri.parse("http://127.0.0.1:6806/" + uri));
        clipboard.setPrimaryClip(clip);
    }

    @JavascriptInterface
    public void writeClipboard(final String content) {
        final ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = ClipData.newPlainText("Copied text from SiYuan", content);
        clipboard.setPrimaryClip(clip);
    }

    @JavascriptInterface
    public void returnDesktop() {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    @JavascriptInterface
    public void openExternal(String url) {
        if (StringUtils.isEmpty(url)) {
            return;
        }

        if (url.startsWith("#")) {
            return;
        }

        if (url.startsWith("assets/")) {
            url = "http://127.0.0.1:6806/" + url;
        }
        if (url.startsWith("/")) {
            url = "http://127.0.0.1:6806" + url;
        }

        final Uri uri = Uri.parse(url);
        final Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
        activity.startActivity(browserIntent);
    }

    @JavascriptInterface
    public void changeStatusBarColor(final String color, final int appearanceMode) {
        activity.runOnUiThread(() -> {
            UltimateBarX.statusBarOnly(activity).transparent().light(appearanceMode == 0).color(parseColor(color)).apply();

            BarUtils.setNavBarLightMode(activity, appearanceMode == 0);
            BarUtils.setNavBarColor(activity, parseColor(color));
        });
    }

    private int parseColor(String str) {
        try {
            str = str.trim();
            if (str.toLowerCase().contains("rgb")) {
                String splitStr = str.substring(str.indexOf('(') + 1, str.indexOf(')'));
                String[] splitString = splitStr.split(",");

                final int[] colorValues = new int[splitString.length];
                for (int i = 0; i < splitString.length; i++) {
                    colorValues[i] = Integer.parseInt(splitString[i].trim());
                }
                return Color.rgb(colorValues[0], colorValues[1], colorValues[2]);
            }
            if (7 > str.length()) {
                // https://stackoverflow.com/questions/10230331/how-to-convert-3-digit-html-hex-colors-to-6-digit-flex-hex-colors
                str = str.replaceAll("#([0-9a-fA-F])([0-9a-fA-F])([0-9a-fA-F])", "#$1$1$2$2$3$3");
            }
            return Color.parseColor(str);
        } catch (final Exception e) {
            Utils.LogError("js", "parse color failed", e);
            return Color.parseColor("#212224");
        }
    }


}
