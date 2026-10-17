package com.guitarmagic.tuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guitarmagic.tuner.ui.ios.IosFilledButton
import com.guitarmagic.tuner.ui.ios.IosTextButton
import com.guitarmagic.tuner.ui.ios.IosType
import com.guitarmagic.tuner.ui.ios.LocalIosColors

/**
 * 没有麦克风权限时显示（iOS 风格的引导页）。
 * @param permanentlyDenied 用户已拒绝且系统不再弹窗，只能去系统设置里打开
 */
@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalIosColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(colors.blue.copy(alpha = if (colors.isDark) 0.24f else 0.12f)),
        ) {
            Text("🎤", fontSize = 40.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (permanentlyDenied) "麦克风权限已关闭" else "需要使用麦克风",
            style = IosType.title2,
            color = colors.label,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "调音器需要通过麦克风“听”到吉他的声音才能识别音高。声音只在手机上实时分析，不会录制保存，也不会上传。",
            style = IosType.subheadline,
            textAlign = TextAlign.Center,
            color = colors.secondaryLabel,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied) {
            Text(
                "请按以下步骤开启：\n" +
                    "1. 轻点下方“打开系统设置”\n" +
                    "2. 找到“权限”\n" +
                    "3. 轻点“麦克风”\n" +
                    "4. 选择“仅在使用该应用时允许”\n" +
                    "5. 返回本应用即可开始调音",
                style = IosType.subheadline,
                color = colors.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
                    .padding(16.dp),
            )
            Spacer(Modifier.height(20.dp))
            IosFilledButton("打开系统设置", onOpenSettings, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            IosTextButton("再次请求权限", onRequest)
        } else {
            IosFilledButton("允许使用麦克风", onRequest, modifier = Modifier.fillMaxWidth())
        }
    }
}
