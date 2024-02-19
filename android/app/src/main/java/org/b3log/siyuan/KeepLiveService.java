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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.util.Random;

/**
 * 保活服务.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.0.1.2, Feb 7, 2024
 * @since 1.0.0
 */
public class KeepLiveService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        try {
            super.onCreate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startMyOwnForeground();
            } else {
                startForeground(1, new Notification());
            }
        } catch (final Throwable e) {
            Utils.LogError("keeplive", "Start foreground service failed", e);
        }
    }

    private final String[] words = new String[]{
            "We are programmed to receive",
            "Then the piper will lead us to reason",
            "You're not the only one",
            "Sometimes I need some time all alone",
            "We still can find a way",
            "You gotta make it your own way",
            "Everybody needs somebody",
            "原谅我这一生不羁放纵爱自由",
            "我要再次找那旧日的足迹",
            "心中一股冲劲勇闯，抛开那现实没有顾虑",
            "愿望是努力走向那一方",
            "其实怕被忘记至放大来演吧",
            "荣耀的背后刻着一道孤独",
            "动机也只有一种名字那叫做欲望",
    };

    private Random random = new Random();

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        final Intent resultIntent = new Intent(this, MainActivity.class).
                setAction(Intent.ACTION_MAIN).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent resultPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 端部分系统闪退 https://github.com/siyuan-note/siyuan/issues/7188
            resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        final String NOTIFICATION_CHANNEL_ID = "org.b3log.siyuan";
        final String channelName = "SiYuan Kernel Service";
        final NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(chan);
        final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        final Notification notification = notificationBuilder.setOngoing(true).
                setSmallIcon(R.drawable.icon).
                setContentTitle(words[random.nextInt(words.length)]).
                setPriority(NotificationManager.IMPORTANCE_MIN).
                setCategory(Notification.CATEGORY_SERVICE).
                setContentIntent(resultPendingIntent).
                build();
        startForeground(2, notification);
    }
}

