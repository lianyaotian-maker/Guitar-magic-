package com.guitarmagic.tuner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 没有麦克风权限时显示。
 * @param permanentlyDenied 用户已拒绝且系统不再弹窗，只能去系统设置里打开
 */
@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🎤", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                if (permanentlyDenied) "麦克风权限已关闭" else "需要使用麦克风",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "调音器需要通过麦克风“听”到吉他的声音才能识别音高。\n" +
                    "声音只在手机上实时分析，不会录制保存，也不会上传。",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            if (permanentlyDenied) {
                Text(
                    "请按以下步骤开启：\n" +
                        "1. 点击下方“打开系统设置”\n" +
                        "2. 找到“权限”\n" +
                        "3. 点击“麦克风”\n" +
                        "4. 选择“仅在使用该应用时允许”\n" +
                        "5. 返回本应用即可开始调音",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("打开系统设置")
                }
                TextButton(onClick = onRequest) {
                    Text("再次请求权限")
                }
            } else {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                    Text("允许使用麦克风")
                }
            }
        }
    }
}
